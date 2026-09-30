package com.aifacemate.face.vision

import com.aifacemate.face.capability.Availability
import com.aifacemate.face.capability.CapabilityKey
import com.aifacemate.face.capability.ModelStore
import com.aifacemate.face.capability.Provider
import com.aifacemate.face.capability.ProviderRegistry
import com.aifacemate.face.capability.currentDeviceEnvironment
import com.aifacemate.face.capability.platformModelStore
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the user turns on in settings. Each task needs one provider per capability. */
enum class VisionTask(val capabilities: List<CapabilityKey<*>>) {
    Objects(listOf(ObjectDetector)),
    Expressions(listOf(FaceDetector, ExpressionClassifier)),
    Gestures(listOf(HandLandmarker)),
}

sealed interface VisionTaskStatus {
    data object Off : VisionTaskStatus
    /** No provider for one of the task's capabilities is packaged on this device. */
    data object NoProvider : VisionTaskStatus
    /** [availability] is [Availability.NeedsModels] or [Availability.Unsupported]. */
    data class Unavailable(val availability: Availability) : VisionTaskStatus
    data object Running : VisionTaskStatus
    /** Stopped for the rest of the session after an error. */
    data class Failed(val message: String) : VisionTaskStatus
}

/** [expression] is null when the classifier is unsure (below the saved threshold). */
data class FaceObservation(val face: DetectedFace, val expression: ExpressionResult?)

/** [gesture] is null when the hand is not making a known static gesture. */
data class HandObservation(val hand: DetectedHand, val gesture: HandGesture?)

/** One analyzed frame; every coordinate is normalized, so consumers need not know the camera resolution. */
data class VisionObservation(
    val frameWidth: Int,
    val frameHeight: Int,
    val objects: List<DetectedObject> = emptyList(),
    val faces: List<FaceObservation> = emptyList(),
    val hands: List<HandObservation> = emptyList(),
)

/** The provider chosen for each of the task's capabilities, in [VisionTask.capabilities] order. */
fun ProviderRegistry.visionProviders(task: VisionTask, preferences: VisionPreferences): List<Provider<*>?> =
    task.capabilities.map { capability -> select(capability, preferences.providerId(capability)) }

fun ProviderRegistry.visionStatus(task: VisionTask, preferences: VisionPreferences): VisionTaskStatus {
    if (!preferences.enabled(task)) return VisionTaskStatus.Off
    val providers = visionProviders(task, preferences)
    if (providers.any { it == null }) return VisionTaskStatus.NoProvider
    val options = preferences.options(task)
    val results = providers.map { availability(it!!, options) }
    results.firstOrNull { it is Availability.Unsupported }?.let { return VisionTaskStatus.Unavailable(it) }
    val missing = results.filterIsInstance<Availability.NeedsModels>().flatMap { it.missing }.distinct()
    return if (missing.isEmpty()) VisionTaskStatus.Running else VisionTaskStatus.Unavailable(Availability.NeedsModels(missing))
}

/**
 * Runs the enabled tasks on frames. Providers open on the first frame and are reused until [close]; a task
 * that throws is stopped and closed while the others keep running. Calls must not overlap.
 */
class PerceptionPipeline(
    private val registry: ProviderRegistry,
    preferences: VisionPreferences,
    private val gestures: GestureClassifier = RuleBasedGestureClassifier,
) {
    private val config = preferences.normalized()
    private val statuses = VisionTask.entries.associateWith { registry.visionStatus(it, config) }.toMutableMap()
    private val opened = LinkedHashMap<CapabilityKey<*>, AutoCloseable>()

    val status: Map<VisionTask, VisionTaskStatus> get() = statuses.toMap()
    val active: Boolean get() = statuses.values.any { it == VisionTaskStatus.Running }

    suspend fun analyze(frame: RgbaFrame): VisionObservation {
        val objects = guarded(VisionTask.Objects) {
            instance(VisionTask.Objects, ObjectDetector).detect(frame, config.yoloConfidenceThreshold)
        }
        val faces = guarded(VisionTask.Expressions) {
            val detector = instance(VisionTask.Expressions, FaceDetector)
            val classifier = instance(VisionTask.Expressions, ExpressionClassifier)
            detector.detect(frame).map { face ->
                FaceObservation(face, classifier.classify(frame, face)?.takeIf { it.confidence >= config.expressionConfidenceThreshold })
            }
        }
        val hands = guarded(VisionTask.Gestures) {
            instance(VisionTask.Gestures, HandLandmarker).detect(frame)
                .filter { it.score >= config.gestureConfidenceThreshold }
                .map { hand -> HandObservation(hand, gestures.classify(hand, frame.width, frame.height)) }
        }
        return VisionObservation(frame.width, frame.height, objects.orEmpty(), faces.orEmpty(), hands.orEmpty())
    }

    fun close() {
        VisionTask.entries.forEach(::closeTask)
    }

    private suspend fun <C : AutoCloseable> instance(task: VisionTask, capability: CapabilityKey<C>): C {
        @Suppress("UNCHECKED_CAST")
        (opened[capability] as C?)?.let { return it }
        val provider = registry.select(capability, config.providerId(capability)) ?: error("没有可用的 ${capability.id}")
        return provider.open(config.options(task)).also { opened[capability] = it }
    }

    private suspend inline fun <T> guarded(task: VisionTask, block: () -> T): T? {
        if (statuses[task] != VisionTaskStatus.Running) return null
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            statuses[task] = VisionTaskStatus.Failed(failure.message ?: failure::class.simpleName ?: "识别失败")
            closeTask(task)
            null
        }
    }

    private fun closeTask(task: VisionTask) {
        task.capabilities.forEach { capability -> runCatching { opened.remove(capability)?.close() } }
    }
}

data class PerceptionState(
    val running: Boolean = false,
    val statuses: Map<VisionTask, VisionTaskStatus> = emptyMap(),
    val observation: VisionObservation? = null,
    /** Time spent on the last analyzed frame, useful when comparing devices. */
    val analysisMillis: Long? = null,
)

/**
 * Owns a perception session independently of any screen. The camera page runs it today; a future behaviour
 * core can observe [state] the same way.
 */
class PerceptionService(
    private val registry: ProviderRegistry,
    private val gestures: GestureClassifier = RuleBasedGestureClassifier,
) {
    private val gate = Mutex()
    private val current = MutableStateFlow(PerceptionState())
    val state: StateFlow<PerceptionState> = current.asStateFlow()

    /** Analyzes the newest frame, drops frames that arrive while busy, and closes every provider when cancelled. */
    suspend fun run(frames: Flow<RgbaFrame>, preferences: VisionPreferences, minIntervalMillis: Long = 150) = gate.withLock {
        val pipeline = PerceptionPipeline(registry, preferences, gestures)
        current.value = PerceptionState(running = true, statuses = pipeline.status)
        try {
            frames.conflate().collect { frame ->
                if (!pipeline.active) return@collect
                val started = TimeSource.Monotonic.markNow()
                val observation = pipeline.analyze(frame)
                current.value = PerceptionState(true, pipeline.status, observation, started.elapsedNow().inWholeMilliseconds)
                delay(minIntervalMillis)
            }
        } finally {
            pipeline.close()
            current.value = PerceptionState()
        }
    }
}

/** Composition root for vision: the providers packaged for this platform and the shared model store. */
object VisionRuntime {
    val modelStore: ModelStore by lazy { platformModelStore() }
    val registry: ProviderRegistry by lazy { ProviderRegistry(platformVisionProviders(modelStore), currentDeviceEnvironment()) }
}

internal expect fun platformVisionProviders(store: ModelStore): List<Provider<*>>
