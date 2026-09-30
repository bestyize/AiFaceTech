package com.aifacemate.face.vision

import com.aifacemate.face.capability.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PerceptionPipelineTest {
    private val environment = DeviceEnvironment(DevicePlatform.Desktop, OperatingSystem.Linux, CpuArchitecture.Arm64)
    private val frame = RgbaFrame(4, 2, ByteArray(4 * 2 * 4))
    private val model = ModelSpec("fixture", "vision/fixture", "fixture.onnx", 1, "", "https://example.test/fixture.onnx")

    /** Counts opens and closes so tests can check lazy loading, reuse and release. */
    private class Fake<C : AutoCloseable>(
        override val id: String,
        override val capability: CapabilityKey<C>,
        var availability: Availability = Availability.Ready,
        private val create: (Fake<C>) -> C,
    ) : Provider<C> {
        override val displayName = id
        override val description = ""
        override val models = emptyList<ModelSpec>()
        var opened = 0
        var closed = 0
        override fun availability(environment: DeviceEnvironment) = availability
        override suspend fun open(): C { opened++; return create(this) }
    }

    private fun objects(label: String = "cup", score: Float = .9f, failAfter: Int = Int.MAX_VALUE) =
        Fake("objects.$label", ObjectDetector) { owner ->
            var calls = 0
            object : ObjectDetector {
                override suspend fun detect(frame: RgbaFrame, minConfidence: Float): List<DetectedObject> {
                    if (++calls > failAfter) error("runtime fixture failure")
                    return listOf(DetectedObject(label, 41, NormalizedBox(0f, 0f, .5f, .5f), score)).filter { it.score >= minConfidence }
                }
                override fun close() { owner.closed++ }
            }
        }

    private val face = DetectedFace(NormalizedBox(.1f, .1f, .4f, .6f), .9f)
    private fun faces() = Fake("faces", FaceDetector) { owner ->
        object : FaceDetector {
            override suspend fun detect(frame: RgbaFrame) = listOf(face)
            override fun close() { owner.closed++ }
        }
    }
    private fun expressions(confidence: Float) = Fake("expressions", ExpressionClassifier) { owner ->
        object : ExpressionClassifier {
            override suspend fun classify(frame: RgbaFrame, face: DetectedFace) = ExpressionResult(Expression.Happy, confidence)
            override fun close() { owner.closed++ }
        }
    }

    @Test
    fun disabledOrUnavailableTasksNeverOpenProviders() = runBlocking {
        val detector = objects()
        val classifier = expressions(.9f).apply { availability = Availability.NeedsModels(listOf(model)) }
        val registry = ProviderRegistry(listOf(detector, faces(), classifier), environment)
        val pipeline = PerceptionPipeline(registry, VisionPreferences(expressionEnabled = true, gestureEnabled = true))
        assertEquals(VisionTaskStatus.Off, pipeline.status[VisionTask.Objects])
        assertEquals(VisionTaskStatus.Unavailable(Availability.NeedsModels(listOf(model))), pipeline.status[VisionTask.Expressions])
        assertEquals(VisionTaskStatus.NoProvider, pipeline.status[VisionTask.Gestures])
        assertFalse(pipeline.active)
        assertEquals(VisionObservation(4, 2), pipeline.analyze(frame))
        assertEquals(0, detector.opened + classifier.opened)
    }

    @Test
    fun providersOpenOnceAndThresholdsFilterResults() = runBlocking {
        val detector = objects(score = .5f)
        val faceDetector = faces()
        val classifier = expressions(.55f)
        val registry = ProviderRegistry(listOf(detector, faceDetector, classifier), environment)
        val preferences = VisionPreferences(yoloEnabled = true, yoloConfidenceThreshold = .4f, expressionEnabled = true,
            expressionConfidenceThreshold = .6f)
        val pipeline = PerceptionPipeline(registry, preferences)
        repeat(3) {
            val observation = pipeline.analyze(frame)
            assertEquals("cup", observation.objects.single().label)
            // The face is reported, but its expression is below the saved threshold.
            assertEquals(FaceObservation(face, null), observation.faces.single())
        }
        assertEquals(listOf(1, 1, 1), listOf(detector.opened, faceDetector.opened, classifier.opened))
        pipeline.close()
        assertEquals(listOf(1, 1, 1), listOf(detector.closed, faceDetector.closed, classifier.closed))
    }

    @Test
    fun aFailingTaskStopsAndClosesWhileOthersContinue() = runBlocking {
        val detector = objects(failAfter = 1)
        val faceDetector = faces()
        val registry = ProviderRegistry(listOf(detector, faceDetector, expressions(.9f)), environment)
        val pipeline = PerceptionPipeline(registry, VisionPreferences(yoloEnabled = true, expressionEnabled = true))
        assertEquals(1, pipeline.analyze(frame).objects.size)
        val second = pipeline.analyze(frame)
        assertTrue(second.objects.isEmpty())
        assertEquals(Expression.Happy, second.faces.single().expression?.expression)
        assertEquals(VisionTaskStatus.Failed("runtime fixture failure"), pipeline.status[VisionTask.Objects])
        assertEquals(1, detector.closed)
        pipeline.analyze(frame)
        assertEquals(1, detector.opened)
    }

    @Test
    fun savedProviderIdSelectsAnotherImplementation() = runBlocking {
        val cpu = objects("cup")
        val npu = objects("npu-cup")
        val registry = ProviderRegistry(listOf(cpu, npu), environment)
        val preferences = VisionPreferences(yoloEnabled = true).withProvider(ObjectDetector.id, npu.id)
        assertEquals("npu-cup", PerceptionPipeline(registry, preferences).analyze(frame).objects.single().label)
        assertEquals(0, cpu.opened)
    }

    @Test
    fun serviceReportsObservationsAndReleasesProvidersWhenCancelled() = runBlocking {
        val detector = objects()
        val service = PerceptionService(ProviderRegistry(listOf(detector), environment))
        val frames = MutableStateFlow<RgbaFrame?>(null)
        val session = launch(start = CoroutineStart.UNDISPATCHED) {
            service.run(frames.filterNotNull(), VisionPreferences(yoloEnabled = true), minIntervalMillis = 0)
        }
        assertTrue(service.state.value.running)
        frames.value = RgbaFrame(frame.width, frame.height, frame.pixels, sampledAtMillis = 1234)
        val state = service.state.first { it.observation != null }
        assertEquals("cup", state.observation!!.objects.single().label)
        assertEquals(VisionTaskStatus.Running, state.statuses[VisionTask.Objects])
        assertEquals(1234L, state.sampledAtMillis)
        session.cancel()
        session.join()
        assertEquals(PerceptionState(), service.state.value)
        assertEquals(1, detector.closed)
    }
}
