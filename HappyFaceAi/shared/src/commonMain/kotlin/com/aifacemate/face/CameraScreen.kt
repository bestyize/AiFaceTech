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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.min

data class CameraDetections(
    val width: Int,
    val height: Int,
    val mirrored: Boolean,
    val items: List<VisionDetection>,
    val local: List<LocalVisualFinding> = emptyList(),
)

@Composable
fun CameraScreen(
    onBack: () -> Unit,
    controller: CameraController = rememberCameraController(),
    preferences: VisionPreferences = remember { VisionPreferencesStore.load() },
) {
    val cameraState by controller.state.collectAsState()
    val latest by controller.frames.collectAsState()
    var active by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf(0) }
    var deviceId by remember { mutableStateOf<String?>(null) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewFrame by remember { mutableStateOf<CameraFrame?>(null) }
    var detections by remember { mutableStateOf<CameraDetections?>(null) }
    var recognition by remember { mutableStateOf("点击启动摄像头") }
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
            detections = null
            if (!active) {
                controller.stop()
                Yolo26mModel.unload()
                LocalVisionAnalyzer.unload()
                recognition = "相机已停止"
                return@withLock
            }
            try {
                controller.start(deviceId)
                val localEnabled = preferences.expressionEnabled || preferences.gestureEnabled
                val localReady =
                    (!preferences.expressionEnabled || LocalVisionAnalyzer.modelsInstalled(LocalVisionTask.FacialExpressions)) &&
                    (!preferences.gestureEnabled || LocalVisionAnalyzer.modelsInstalled(LocalVisionTask.HandGestures))
                var yoloFailed = false
                var localFailed = false
                controller.frames.filterNotNull().conflate().collect { frame ->
                    if (controller.state.value.status != CameraStatus.Running) return@collect
                    val rgbaFrame = RgbaFrame(frame.width, frame.height, frame.rgba)
                    var localFindings = emptyList<LocalVisualFinding>()
                    var localStatus: String? = when {
                        !localEnabled -> null
                        !LocalVisionAnalyzer.supported() -> "设备不支持本地表情/手势识别"
                        !localReady -> "请先下载表情或手势模型"
                        else -> null
                    }
                    if (localEnabled && localReady && LocalVisionAnalyzer.supported() && !localFailed) {
                        try {
                            localFindings = LocalVisionAnalyzer.analyze(rgbaFrame, preferences)
                            localStatus = localFindings.take(2).joinToString(" · ") { "${it.label} ${(it.confidence * 100).toInt()}%" }
                                .ifBlank { "未识别到明显表情或手势" }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: LinkageError) {
                            localFailed = true
                            localStatus = "本地视觉运行库不可用"
                        } catch (error: Exception) {
                            localFailed = true
                            localStatus = error.message ?: "本地表情/手势识别失败"
                        }
                    }
                    var objects = emptyList<VisionDetection>()
                    var yoloStatus = if (!preferences.yoloEnabled) null else when {
                        !Yolo26mModel.supported() -> "此设备 YOLO 后端待接入"
                        !yoloFailed -> "未检测到目标"
                        else -> "YOLO 识别已停止"
                    }
                    if (preferences.yoloEnabled && Yolo26mModel.supported() && !yoloFailed) {
                        try {
                            objects = Yolo26mModel.detect(rgbaFrame, preferences)
                            yoloStatus = objects.take(2).joinToString(" · ") { "${it.label} ${(it.confidence * 100).toInt()}%" }
                                .ifBlank { "未检测到目标" }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: LinkageError) {
                            yoloFailed = true
                            yoloStatus = "YOLO 运行库不可用"
                        } catch (error: Exception) {
                            yoloFailed = true
                            yoloStatus = error.message ?: "YOLO 识别失败，请检查模型后重试"
                        }
                    }
                    detections = CameraDetections(frame.width, frame.height, frame.mirrored, objects, localFindings)
                    recognition = listOfNotNull(localStatus, yoloStatus)
                        .joinToString(" · ").ifBlank { "相机已启动" }
                    // Run inference on the newest frame only. The camera stream does not build up a queue.
                    delay(150)
                }
            } finally {
                withContext(NonCancellable) { controller.stop(); Yolo26mModel.unload(); LocalVisionAnalyzer.unload() }
            }
        }
    }
    CameraScreenContent(
        state = cameraState,
        bitmap = bitmap,
        frame = previewFrame,
        detections = detections,
        recognition = recognition,
        onBack = onBack,
        onStart = { active = true; request++ },
        onStop = { active = false },
        onDeviceChange = { deviceId = it; if (active) request++ },
    )
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
                    if (detections != null && detections.width == frame.width && detections.height == frame.height && detections.mirrored == frame.mirrored) {
                        Canvas(Modifier.fillMaxSize().testTag("camera.detections")) {
                            val transform = CameraPreviewTransform.fit(frame.width, frame.height, size.width, size.height, frame.mirrored)
                            fun drawBox(box: PixelBox, label: String, accent: Color) {
                                val left = transform.x(box.left)
                                val right = transform.x(box.right)
                                val top = transform.y(box.top)
                                val bottom = transform.y(box.bottom)
                                drawRect(accent, Offset(min(left, right), top), Size(abs(right - left), (bottom - top).coerceAtLeast(0f)),
                                    style = Stroke(2.dp.toPx()))
                                val measured = textMeasurer.measure(label,
                                    TextStyle(color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                                val labelX = min(left, right).coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                                val labelY = (top - measured.size.height).coerceIn(0f, (size.height - measured.size.height).coerceAtLeast(0f))
                                drawRect(accent, Offset(labelX, labelY), Size(measured.size.width.toFloat(), measured.size.height.toFloat()))
                                drawText(measured, topLeft = Offset(labelX, labelY))
                            }
                            detections.items.forEach { drawBox(it.box, "${it.label} ${(it.confidence * 100).toInt()}%", Color(0xFF66E3AC)) }
                            detections.local.forEach { finding ->
                                val color = if (finding.task == LocalVisionTask.FacialExpressions) Color(0xFFFFA0C9) else Color(0xFF74D5FF)
                                drawBox(finding.box, "${finding.label} ${(finding.confidence * 100).toInt()}%", color)
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
