package com.aifacemate.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aifacemate.face.camera.*
import com.aifacemate.face.theme.AppColors
import com.aifacemate.face.vision.*
import com.aifacemate.face.capability.Availability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.min

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
    var active by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf(0) }
    var deviceId by remember { mutableStateOf<String?>(null) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewFrame by remember { mutableStateOf<CameraFrame?>(null) }
    var mirrored by remember { mutableStateOf(false) }
    val sessionGate = remember { Mutex() }
    DisposableEffect(controller) { onDispose { controller.close() } }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { active = false }
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

private fun percent(value: Float) = "${(value * 100).toInt()}%"

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

/** Offline fixtures can exercise the page without opening any device or loading a model. */
@Composable
fun CameraScreenContent(
    state: CameraState,
    bitmap: ImageBitmap?,
    frame: CameraFrame?,
    detections: CameraDetections?,
    recognition: String,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDeviceChange: (String) -> Unit,
) {
    var showDevices by remember { mutableStateOf(false) }
    var index by remember(state.deviceId) { mutableStateOf(state.deviceId ?: "0") }
    val textMeasurer = rememberTextMeasurer()
    val running = state.status == CameraStatus.Running || state.status == CameraStatus.Starting
    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.Background).safeDrawingPadding()) {
        val compact = maxHeight < 400.dp || maxWidth < 600.dp
        Column(Modifier.fillMaxSize().padding(if (compact) 8.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).testTag("camera.back"),
                    contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Canvas(Modifier.size(14.dp)) {
                        drawLine(AppColors.Amber, Offset(size.width * .75f, size.height * .1f), Offset(size.width * .25f, size.height * .5f), 2.dp.toPx())
                        drawLine(AppColors.Amber, Offset(size.width * .25f, size.height * .5f), Offset(size.width * .75f, size.height * .9f), 2.dp.toPx())
                    }
                    if (!compact) { Spacer(Modifier.width(4.dp)); Text("返回") }
                }
                Text("摄像头", Modifier.weight(1f), color = Color.White, fontSize = if (compact) 17.sp else 22.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                OutlinedButton(onClick = { showDevices = true },
                    enabled = state.usesDeviceIndex || state.devices.size > 1,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("camera.devices"), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("设备") }
                Button(onClick = if (running) onStop else onStart, modifier = Modifier.heightIn(min = 48.dp).testTag("camera.toggle"),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Amber, contentColor = AppColors.Background),
                    contentPadding = PaddingValues(horizontal = 14.dp)) { Text(if (running) "停止" else "启动") }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black).testTag("camera.preview"), contentAlignment = Alignment.Center) {
                if (bitmap != null && frame != null && state.status == CameraStatus.Running) {
                    Image(bitmap, "摄像头实时预览", Modifier.fillMaxSize().graphicsLayer { scaleX = if (frame.mirrored) -1f else 1f }, contentScale = ContentScale.Fit)
                    val observation = detections?.observation
                    if (observation != null && observation.frameWidth == frame.width && observation.frameHeight == frame.height && detections.mirrored == frame.mirrored) {
                        Canvas(Modifier.fillMaxSize().testTag("camera.detections")) {
                            val transform = CameraPreviewTransform.fit(frame.width, frame.height, size.width, size.height, frame.mirrored)
                            fun drawBox(box: NormalizedBox, label: String, accent: Color) {
                                val left = transform.x(box.left * frame.width)
                                val right = transform.x(box.right * frame.width)
                                val top = transform.y(box.top * frame.height)
                                val bottom = transform.y(box.bottom * frame.height)
                                drawRect(accent, Offset(min(left, right), top), Size(abs(right - left), (bottom - top).coerceAtLeast(0f)),
                                    style = Stroke(2.dp.toPx()))
                                val measured = textMeasurer.measure(label,
                                    TextStyle(color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                                val labelX = min(left, right).coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                                val labelY = (top - measured.size.height).coerceIn(0f, (size.height - measured.size.height).coerceAtLeast(0f))
                                drawRect(accent, Offset(labelX, labelY), Size(measured.size.width.toFloat(), measured.size.height.toFloat()))
                                drawText(measured, topLeft = Offset(labelX, labelY))
                            }
                            observation.objects.forEach { drawBox(it.box, "${it.label} ${percent(it.score)}", Color(0xFF66E3AC)) }
                            observation.faces.forEach { face ->
                                val label = face.expression?.let { "${it.expression.label()} ${percent(it.confidence)}" } ?: "人脸"
                                drawBox(face.face.box, label, Color(0xFFFFA0C9))
                            }
                            observation.hands.forEach { hand ->
                                drawBox(hand.hand.box, hand.gesture?.label() ?: "手", Color(0xFF74D5FF))
                            }
                        }
                    }
                } else {
                    Text(when (state.status) {
                        CameraStatus.Starting -> "正在启动摄像头…"
                        CameraStatus.PermissionDenied -> "需要摄像头权限"
                        CameraStatus.Error -> "摄像头暂不可用"
                        CameraStatus.Running -> "等待相机画面…"
                        else -> "点击启动，打开摄像头"
                    }, color = AppColors.Secondary, modifier = Modifier.padding(16.dp))
                }
            }
            Text(state.error ?: recognition, color = if (state.error != null) AppColors.Pink else AppColors.Secondary,
                fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().testTag("camera.status"))
            if (!compact) Text("视频帧仅在本机处理；离开页面或进入后台后停止采集。", color = AppColors.Secondary, fontSize = 12.sp)
        }
        if (showDevices) AlertDialog(
            onDismissRequest = { showDevices = false },
            title = { Text("选择摄像头") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.usesDeviceIndex) {
                        Text("桌面设备按编号选择，0 为默认摄像头。", color = AppColors.Secondary)
                        OutlinedTextField(index, { index = it.filter(Char::isDigit).take(2) }, label = { Text("设备编号（0–15）") },
                            singleLine = true, modifier = Modifier.testTag("camera.deviceIndex"))
                    } else state.devices.forEach { device ->
                        TextButton(onClick = { onDeviceChange(device.id); showDevices = false },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("camera.device.${device.id}")) {
                            Text(if (device.id == state.deviceId) "✓ ${device.name}" else device.name)
                        }
                    }
                }
            },
            confirmButton = {
                if (state.usesDeviceIndex) TextButton(
                    onClick = { onDeviceChange(index); showDevices = false },
                    enabled = index.toIntOrNull() in 0..15, modifier = Modifier.testTag("camera.deviceConfirm"),
                ) { Text("使用此设备") }
            },
            dismissButton = { TextButton(onClick = { showDevices = false }) { Text("关闭") } },
        )
    }
}
