package com.aifacemate.face

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aifacemate.face.camera.*
import com.aifacemate.face.vision.*
import com.aifacemate.face.capability.Availability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A perception result tied to the camera frame orientation it was computed on. */
data class CameraDetections(val mirrored: Boolean, val observation: VisionObservation)

@Composable
fun CameraScreen(
    onBack: () -> Unit,
    controller: CameraController = rememberCameraController(),
    preferences: VisionPreferences = remember { VisionPreferencesStore.load() },
    perception: PerceptionService = remember { PerceptionService(VisionRuntime.registry) },
) {
    val cameraState by controller.state.collectAsState()
    val latest by controller.frames.collectAsState()
    val perceived by perception.state.collectAsState()
    var active by remember { mutableStateOf(true) }
    var photoBusy by remember { mutableStateOf(false) }
    var photoMessage by remember { mutableStateOf<String?>(null) }
    var captureFlash by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var request by remember { mutableStateOf(0) }
    var deviceId by remember { mutableStateOf<String?>(null) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewFrame by remember { mutableStateOf<CameraFrame?>(null) }
    var mirrored by remember { mutableStateOf(false) }
    val sessionGate = remember { Mutex() }
    DisposableEffect(controller) { onDispose { controller.close() } }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { active = false }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { active = true }
    LaunchedEffect(photoMessage) { if (photoMessage != null) { delay(4000); photoMessage = null } }
    LaunchedEffect(captureFlash) { if (captureFlash) { delay(100); captureFlash = false } }
    LaunchedEffect(latest) {
        val frame = latest
        if (frame == null) { bitmap = null; previewFrame = null }
        else {
            val image = withContext(Dispatchers.Default) { frame.toPreviewBitmap() }
            bitmap = image
            previewFrame = frame
        }
    }
    LaunchedEffect(active, request, deviceId) {
        sessionGate.withLock {
            if (!active) {
                controller.stop()
                return@withLock
            }
            try {
                controller.start(deviceId)
                coroutineScope {
                    launch {
                        perception.run(
                            controller.frames.filterNotNull()
                                .filter { controller.state.value.status == CameraStatus.Running }
                                .map { frame -> mirrored = frame.mirrored; RgbaFrame(frame.width, frame.height, frame.rgba) },
                            preferences,
                        )
                    }
                }
            } finally {
                withContext(NonCancellable) { controller.stop() }
            }
        }
    }
    val observation = perceived.observation
    CameraScreenContent(
        state = cameraState,
        bitmap = bitmap,
        frame = previewFrame,
        detections = observation?.let { CameraDetections(mirrored, it) },
        recognition = when {
            !active -> "相机已停止"
            !perceived.running -> "相机已启动"
            else -> recognitionSummary(perceived)
        },
        onBack = onBack,
        onStart = { active = true; request++ },
        onStop = { active = false },
        onDeviceChange = { deviceId = it; if (active) request++ },
        photoBusy = photoBusy,
        photoMessage = photoMessage,
        captureFlash = captureFlash,
        onZoomChange = { ratio -> scope.launch {
            try { controller.setZoom(ratio) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { photoMessage = failure.message ?: "变焦失败，请重试" }
        } },
        onCapture = { aspect ->
            if (!photoBusy) {
                photoBusy = true
                captureFlash = true
                scope.launch {
                    try { photoMessage = "照片已保存：${controller.capturePhoto(aspect).location}" }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { photoMessage = failure.message ?: "照片保存失败" }
                    finally { photoBusy = false }
                }
            }
        },
    )
}

/** One line per enabled task, e.g. "开心 91% · 点赞 99% · person 97% · 108 ms". */
internal fun recognitionSummary(state: PerceptionState): String {
    val observation = state.observation
    val parts = VisionTask.entries.mapNotNull { task ->
        when (val status = state.statuses[task]) {
            null, VisionTaskStatus.Off -> null
            VisionTaskStatus.NoProvider -> "${task.label()}暂未接入"
            is VisionTaskStatus.Unavailable -> when (val availability = status.availability) {
                is Availability.NeedsModels -> "请先下载${task.label()}模型"
                is Availability.Unsupported -> availability.reason
                Availability.Ready -> null
            }
            is VisionTaskStatus.Failed -> "${task.label()}已停止：${status.message}"
            VisionTaskStatus.Running -> observation?.let { summary(task, it) }
        }
    }
    val timing = state.analysisMillis?.let { "$it ms" }
    return (parts + listOfNotNull(timing)).joinToString(" · ").ifBlank { "相机已启动" }
}

private fun summary(task: VisionTask, observation: VisionObservation): String = when (task) {
    VisionTask.Objects -> observation.objects.take(2).joinToString(" · ") { "${it.label} ${percent(it.score)}" }.ifBlank { "未检测到目标" }
    VisionTask.Expressions -> observation.faces.mapNotNull { it.expression }.take(2)
        .joinToString(" · ") { "${it.expression.label()} ${percent(it.confidence)}" }
        .ifBlank { if (observation.faces.isEmpty()) "未检测到人脸" else "表情不明显" }
    VisionTask.Gestures -> observation.hands.mapNotNull { it.gesture }.take(2).joinToString(" · ") { it.label() }
        .ifBlank { if (observation.hands.isEmpty()) "未检测到手" else "手势不明显" }
}

internal fun percent(value: Float) = "${(value * 100).toInt()}%"

internal fun VisionTask.label() = when (this) {
    VisionTask.Objects -> "目标检测"
    VisionTask.Expressions -> "表情识别"
    VisionTask.Gestures -> "手势识别"
}

internal fun Expression.label() = when (this) {
    Expression.Angry -> "愤怒"
    Expression.Disgust -> "厌恶"
    Expression.Fear -> "恐惧"
    Expression.Happy -> "开心"
    Expression.Neutral -> "平静"
    Expression.Sad -> "难过"
    Expression.Surprise -> "惊讶"
    Expression.Smiling -> "微笑"
    Expression.MouthOpen -> "张嘴"
}

internal fun HandGesture.label() = when (this) {
    HandGesture.ThumbsUp -> "点赞"
    HandGesture.OpenPalm -> "张开手掌"
    HandGesture.Fist -> "握拳"
    HandGesture.Victory -> "胜利手势"
    HandGesture.Pointing -> "指向"
    HandGesture.Pinch -> "捏合"
}
