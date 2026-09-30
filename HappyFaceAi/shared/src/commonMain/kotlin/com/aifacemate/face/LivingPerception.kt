package com.aifacemate.face

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aifacemate.face.behavior.BehaviorEvent
import com.aifacemate.face.behavior.VisualGesture
import com.aifacemate.face.behavior.FacialAppearance
import com.aifacemate.face.camera.*
import com.aifacemate.face.vision.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Face-page owner. Leaving the page or stopping the app releases camera and inference. */
@Composable
internal fun LivingPerception(now: () -> Long, onEvent: (BehaviorEvent) -> Unit, onNotice: (String) -> Unit, onStatus: (String) -> Unit) {
    val preferences = remember { VisionPreferencesStore.load() }
    if (VisionTask.entries.none { VisionRuntime.registry.visionStatus(it, preferences) == VisionTaskStatus.Running }) {
        SideEffect { onStatus(if (VisionTask.entries.any { preferences.enabled(it) }) "未就绪" else "未开启") }
        return
    }
    val controller = rememberCameraController()
    val perception = remember { PerceptionService(VisionRuntime.registry) }
    val objects = remember { PerceptionService(VisionRuntime.registry) }
    val interactive = preferences.expressionEnabled || preferences.gestureEnabled
    val interactivePreferences = if (interactive) preferences.copy(yoloEnabled = false) else preferences
    val callback = rememberUpdatedState(onEvent)
    val currentNow = rememberUpdatedState(now)
    val notice = rememberUpdatedState(onNotice)
    val status = rememberUpdatedState(onStatus)
    val cameraState by controller.state.collectAsState()
    var restart by remember { mutableStateOf(0) }
    var lastFrameAt by remember { mutableStateOf<Long?>(null) }
    var active by remember { mutableStateOf(true) }
    var recognitionReady by remember { mutableStateOf(false) }
    LaunchedEffect(controller) {
        controller.frames.collect { frame -> if (frame != null) lastFrameAt = currentNow.value() }
    }
    // A failed camera or a stalled stream must recover on this page, without a preview-page round trip.
    LaunchedEffect(cameraState.status, active, recognitionReady) {
        status.value(when {
            !active -> "已暂停"
            cameraState.status == CameraStatus.Running -> if (recognitionReady) "监测中" else "识别启动中"
            cameraState.status == CameraStatus.Starting -> "启动中"
            cameraState.status == CameraStatus.PermissionDenied -> "需授权"
            cameraState.status == CameraStatus.Error -> "重试中"
            else -> "启动中"
        })
        if (!active) return@LaunchedEffect
        if (cameraState.status == CameraStatus.Error) {
            delay(3000)
            restart++
        } else if (cameraState.status == CameraStatus.Running) {
            val started = currentNow.value()
            while (true) {
                delay(2000)
                if (currentNow.value() - (lastFrameAt ?: started) > 8000) {
                    status.value("重试中")
                    restart++
                    break
                }
            }
        }
    }
    LaunchedEffect(cameraState.status, cameraState.error) {
        when (cameraState.status) {
            CameraStatus.PermissionDenied -> notice.value("视觉互动需要相机权限，可在系统设置中开启")
            CameraStatus.Error -> notice.value(cameraState.error ?: "相机不可用，视觉互动已停止")
            else -> Unit
        }
    }
    val gate = remember { Mutex() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { active = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { active = false }
    LaunchedEffect(active, restart) {
        gate.withLock {
            if (!active) { controller.stop(); callback.value(BehaviorEvent.VisionStopped); return@withLock }
            try {
                lastFrameAt = null
                recognitionReady = false
                controller.start()
                coroutineScope {
                    var failureReported = false
                    val frames = controller.frames.filterNotNull()
                        .filter { controller.state.value.status == CameraStatus.Running }
                        .map { frame -> RgbaFrame(frame.width, frame.height, frame.rgba, currentNow.value()) }
                    launch(Dispatchers.Default) {
                        perception.run(frames, interactivePreferences, minIntervalMillis = if (interactive) 350 else 1500)
                    }
                    // Heavy object detection must not delay gesture evidence or facial-expression inference.
                    if (interactive && preferences.yoloEnabled) {
                        launch(Dispatchers.Default) {
                            objects.run(frames, preferences.copy(expressionEnabled = false, gestureEnabled = false), minIntervalMillis = 1500)
                        }
                        launch {
                            var failureReported = false
                            objects.state.collect { state ->
                                if (!failureReported && state.statuses.values.any { it is VisionTaskStatus.Failed }) {
                                    failureReported = true
                                    notice.value("目标检测已停止，手势识别继续运行")
                                }
                                if (!preferences.expressionEnabled) {
                                    state.toBehaviorEvent(persons = true, gestures = false)?.let { callback.value(it) }
                                }
                            }
                        }
                    }
                    var lastDiagnosticAt = -3000L
                    perception.state.collect { state ->
                        if (!failureReported && state.statuses.values.any { it is VisionTaskStatus.Failed }) {
                            failureReported = true
                            notice.value("部分视觉识别已停止，请到视觉页面查看")
                        }
                        val observation = state.observation ?: return@collect
                        recognitionReady = state.statuses.values.any { it == VisionTaskStatus.Running }
                        if (currentNow.value() - lastDiagnosticAt >= 3000) {
                            lastDiagnosticAt = currentNow.value()
                            println("FaceVision interactiveMs=${state.analysisMillis} faces=${observation.faces.size} expressions=${observation.faces.mapNotNull { it.expression }.take(2).map { "${it.expression.id}:${(it.confidence * 100).toInt()}" }} hands=${observation.hands.size} thumbs=${observation.hands.count { it.gesture == HandGesture.ThumbsUp }} ageMs=${state.sampledAtMillis?.let { currentNow.value() - it }}")
                        }
                        state.toBehaviorEvent(persons = !interactive || preferences.expressionEnabled, gestures = preferences.gestureEnabled)
                            ?.let { callback.value(it) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                notice.value(failure.message ?: "视觉互动暂不可用")
            } finally {
                withContext(NonCancellable) { controller.stop() }
                callback.value(BehaviorEvent.VisionStopped)
            }
        }
    }
}

/** Perception already applies the saved thresholds; do not silently raise them in the face-page adapter. */
internal fun PerceptionState.toBehaviorEvent(persons: Boolean, gestures: Boolean): BehaviorEvent.Vision? {
    val result = observation ?: return null
    val sampled = sampledAtMillis ?: return null
    val selectedGesture = result.hands.filter { it.gesture != null }.maxByOrNull { it.hand.score }?.gesture
    return BehaviorEvent.Vision(
        personVisible = if (persons) result.faces.isNotEmpty() || result.objects.any { it.label == "person" } else null,
        thumbsUp = if (gestures) selectedGesture == HandGesture.ThumbsUp else null,
        capturedAtMillis = sampled,
        gesture = if (gestures) selectedGesture?.let { VisualGesture.valueOf(it.name) } else null,
        faceAnalyzed = persons && statuses[VisionTask.Expressions] == VisionTaskStatus.Running,
        appearance = result.faces.mapNotNull { it.expression }.maxByOrNull { it.confidence }?.expression?.let {
            when (it) {
                Expression.Happy, Expression.Smiling -> FacialAppearance.Smile
                Expression.Sad -> FacialAppearance.Sad
                Expression.Surprise -> FacialAppearance.Surprise
                else -> null
            }
        },
    )
}
