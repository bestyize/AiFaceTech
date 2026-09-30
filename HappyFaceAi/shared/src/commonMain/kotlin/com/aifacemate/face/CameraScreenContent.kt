package com.aifacemate.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.camera.*
import com.aifacemate.face.vision.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Full-screen camera UI; fixtures may supply a frame without opening a physical camera. */
@Suppress("UNUSED_PARAMETER")
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
    photoBusy: Boolean = false,
    photoMessage: String? = null,
    captureFlash: Boolean = false,
    onZoomChange: (Float) -> Unit = {},
    onCapture: (Float) -> Unit = {},
    focusResult: CameraFocusResult? = null,
    onFocus: (CameraFocusPoint) -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    var showDevices by remember { mutableStateOf(false) }
    var index by remember(state.deviceId) { mutableStateOf(state.deviceId ?: "0") }
    val running = state.status == CameraStatus.Running
    var focusMarker by remember(state.deviceId) { mutableStateOf<Offset?>(null) }
    val latestFocus by rememberUpdatedState(onFocus)
    LaunchedEffect(focusMarker, focusResult) {
        if (focusMarker != null) { delay(if (focusResult == null) 5000 else 1300); focusMarker = null }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).clipToBounds()) {
        val aspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        Box(Modifier.fillMaxSize().testTag("camera.preview").semantics {
            if (running && frame != null) onClick(label = "中央对焦") {
                focusMarker = Offset(.5f, .5f); latestFocus(CameraFocusPoint(.5f, .5f)); true
            }
        }.pointerInput(state.status, state.deviceId, frame?.width, frame?.height, frame?.mirrored) {
            val source = frame
            if (running && source != null) detectTapGestures { tap ->
                val transform = CameraPreviewTransform.crop(source.width, source.height, size.width.toFloat(), size.height.toFloat(), source.mirrored)
                focusMarker = Offset(tap.x / size.width, tap.y / size.height)
                latestFocus(transform.focusPoint(tap.x, tap.y, source.height))
            }
        }, contentAlignment = Alignment.Center) {
            if (bitmap != null && frame != null && running) {
                Image(bitmap, "摄像头实时预览", Modifier.fillMaxSize().graphicsLayer {
                    scaleX = if (frame.mirrored) -1f else 1f
                }, contentScale = ContentScale.Crop)
                val observation = detections?.observation
                if (observation != null && observation.frameWidth == frame.width && observation.frameHeight == frame.height && detections.mirrored == frame.mirrored) {
                    Canvas(Modifier.fillMaxSize().testTag("camera.detections")) {
                        val transform = CameraPreviewTransform.crop(frame.width, frame.height, size.width, size.height, frame.mirrored)
                        fun drawBox(box: NormalizedBox, label: String, accent: Color) {
                            val left = transform.x(box.left * frame.width)
                            val right = transform.x(box.right * frame.width)
                            val top = transform.y(box.top * frame.height)
                            val bottom = transform.y(box.bottom * frame.height)
                            if (maxOf(left, right) < 0 || min(left, right) > size.width || bottom < 0 || top > size.height) return
                            drawRect(accent, Offset(min(left, right), top), Size(abs(right - left), (bottom - top).coerceAtLeast(0f)), style = Stroke(2.dp.toPx()))
                            val measured = textMeasurer.measure(label, TextStyle(color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                            val labelX = min(left, right).coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                            val labelY = (top - measured.size.height).coerceIn(0f, (size.height - measured.size.height).coerceAtLeast(0f))
                            drawRect(accent, Offset(labelX, labelY), Size(measured.size.width.toFloat(), measured.size.height.toFloat()))
                            drawText(measured, topLeft = Offset(labelX, labelY))
                        }
                        observation.objects.forEach { drawBox(it.box, "${it.label} ${percent(it.score)}", Color(0xFF66E3AC)) }
                        observation.faces.forEach { face -> drawBox(face.face.box,
                            face.expression?.let { "${it.expression.label()} ${percent(it.confidence)}" } ?: "人脸", Color(0xFFFFA0C9)) }
                        observation.hands.forEach { hand -> drawBox(hand.hand.box, hand.gesture?.label() ?: "手", Color(0xFF74D5FF)) }
                    }
                }
            } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 64.dp)) {
                if (state.status == CameraStatus.Starting || state.status == CameraStatus.Idle || running) CircularProgressIndicator(color = Color.White)
                Text(state.error ?: when (state.status) {
                    CameraStatus.PermissionDenied -> "请允许摄像头访问"
                    CameraStatus.Error -> "摄像头暂不可用"
                    CameraStatus.Running -> "等待相机画面…"
                    else -> "正在启动摄像头…"
                }, color = Color.White)
                if (state.status == CameraStatus.Error || state.status == CameraStatus.PermissionDenied) {
                    OutlinedButton(onClick = onStart, modifier = Modifier.testTag("camera.retry")) { Text("重试", color = Color.White) }
                }
            }
            val marker = focusMarker
            if (marker != null && running) Canvas(Modifier.fillMaxSize().testTag("camera.focusIndicator").semantics {
                contentDescription = when (focusResult) {
                    CameraFocusResult.Focused -> "对焦成功"
                    CameraFocusResult.Failed -> "未完成对焦"
                    CameraFocusResult.ExposureAdjusted -> "已调整曝光"
                    CameraFocusResult.Unsupported -> "不支持点选对焦"
                    else -> "正在对焦"
                }
            }) {
                val color = when (focusResult) {
                    CameraFocusResult.Focused -> Color(0xFF66E3AC)
                    CameraFocusResult.Unsupported -> Color.Gray
                    CameraFocusResult.Failed -> Color(0xFFFF947B)
                    else -> Color(0xFFF1C779)
                }
                val radius = if (size.height / density < 400) 14.dp.toPx() else 22.dp.toPx()
                drawRoundRect(color, Offset(marker.x * size.width - radius, marker.y * size.height - radius), Size(radius * 2, radius * 2),
                    CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
        }
        if (captureFlash) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .65f)))
        CameraControls(state, frame, recognition, photoBusy, photoMessage, aspect, onBack, onZoomChange, onCapture,
            onDeviceChange, onSelectDevice = { showDevices = true })
        if (showDevices) AlertDialog(onDismissRequest = { showDevices = false }, title = { Text("选择摄像头") },
            text = { OutlinedTextField(index, { index = it.filter(Char::isDigit).take(2) }, singleLine = true,
                label = { Text("设备编号（0–15）") }, modifier = Modifier.testTag("camera.deviceIndex")) },
            confirmButton = { TextButton(onClick = { onDeviceChange(index); showDevices = false }, enabled = index.toIntOrNull() in 0..15,
                modifier = Modifier.testTag("camera.deviceConfirm")) { Text("使用此设备") } },
            dismissButton = { TextButton(onClick = { showDevices = false }) { Text("关闭") } })
    }
}

@Composable
private fun CameraControls(
    state: CameraState, frame: CameraFrame?, recognition: String, photoBusy: Boolean, photoMessage: String?, aspect: Float,
    onBack: () -> Unit, onZoomChange: (Float) -> Unit, onCapture: (Float) -> Unit,
    onDeviceChange: (String) -> Unit, onSelectDevice: () -> Unit,
) {
    var zoomExpanded by remember(state.deviceId) { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var zoomInteraction by remember { mutableIntStateOf(0) }
    LaunchedEffect(zoomExpanded, zoomInteraction) {
        if (zoomExpanded) { delay(3000); zoomExpanded = false }
    }
    val running = state.status == CameraStatus.Running
    val tenths = (state.zoomRatio * 10).roundToInt()
    val ratioLabel = if (tenths % 10 == 0) "${tenths / 10}×" else "${tenths / 10f}×"
    // Measure controls inside the safe area once; individual controls must not each add all four insets.
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val compact = maxHeight < 400.dp
        val sideShutter = compact && maxWidth > maxHeight
        val edge = if (compact) 4.dp else 16.dp
        val zoomHeight = if (compact) minOf(128.dp, (maxHeight - 24.dp).coerceAtLeast(48.dp)) else 200.dp
        val detailsHeight = maxOf(48.dp, maxHeight * .4f)
        val overlay = Color.Black.copy(alpha = if (compact) .35f else .55f)
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(edge), verticalAlignment = Alignment.CenterVertically) {
            CameraFloatingButton("返回", "camera.back", compact = compact, onClick = onBack) {
                drawLine(Color.White, Offset(size.width * .65f, size.height * .15f), Offset(size.width * .3f, size.height * .5f), 2.dp.toPx())
                drawLine(Color.White, Offset(size.width * .3f, size.height * .5f), Offset(size.width * .65f, size.height * .85f), 2.dp.toPx())
            }
            Spacer(Modifier.weight(1f))
            if (state.usesDeviceIndex && !compact) TextButton(onClick = onSelectDevice, modifier = Modifier.testTag("camera.devices")) {
                Text("设备 ${state.deviceId ?: "0"}", color = Color.White)
            }
            if (state.devices.size > 1) CameraFloatingButton("切换摄像头", "camera.flip", compact = compact,
                enabled = running && !photoBusy, onClick = {
                    val cameras = state.devices
                    val current = cameras.indexOfFirst { it.id == state.deviceId }
                    val opposite = when (cameras.getOrNull(current)?.lens) {
                        CameraLens.Front -> CameraLens.Back
                        CameraLens.Back -> CameraLens.Front
                        else -> null
                    }
                    val next = cameras.firstOrNull { opposite != null && it.lens == opposite }
                        ?: cameras[(current + 1).mod(cameras.size)]
                    onDeviceChange(next.id)
                }) {
                drawRoundRect(Color.White, Offset(size.width * .18f, size.height * .25f), Size(size.width * .64f, size.height * .5f), CornerRadius(3.dp.toPx()), style = Stroke(1.7.dp.toPx()))
                drawArc(Color.White, 30f, 270f, false, Offset(size.width * .34f, size.height * .34f), Size(size.width * .32f, size.height * .32f), style = Stroke(1.8.dp.toPx()))
                drawLine(Color.White, Offset(size.width * .66f, size.height * .44f), Offset(size.width * .55f, size.height * .38f), 1.8.dp.toPx())
                drawLine(Color.White, Offset(size.width * .66f, size.height * .44f), Offset(size.width * .7f, size.height * .32f), 1.8.dp.toPx())
            }
        }
        if (state.maxZoomRatio > state.minZoomRatio) {
            if (compact) {
                TextButton(onClick = { zoomExpanded = !zoomExpanded; zoomInteraction++ }, enabled = running && !photoBusy,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.align(if (sideShutter) Alignment.BottomEnd else Alignment.CenterEnd)
                        .padding(edge).size(48.dp).semantics { contentDescription = "展开或收起变焦" }.testTag("camera.zoomToggle")) {
                    Text(ratioLabel, color = Color.White, fontSize = 12.sp, maxLines = 1,
                        modifier = Modifier.background(overlay, CircleShape).padding(horizontal = 7.dp, vertical = 7.dp))
                }
            }
            if (!compact || zoomExpanded) Column(
                Modifier.align(Alignment.CenterEnd).padding(end = if (compact) 64.dp else 12.dp)
                    .background(overlay, RoundedCornerShape(24.dp)).padding(vertical = if (compact) 4.dp else 12.dp)
                    .testTag("camera.zoomPanel"), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!compact) Text(ratioLabel, color = Color.White, fontSize = 12.sp)
                CameraZoomSlider(state.zoomRatio, state.minZoomRatio..state.maxZoomRatio, running && !photoBusy,
                    onChange = { zoomInteraction++; onZoomChange(it) },
                    modifier = Modifier.width(48.dp).height(zoomHeight))
            }
        }
        val status = state.error ?: recognition
        if (compact) {
            val summary = when {
                photoMessage?.startsWith("照片已保存") == true -> "照片已保存"
                photoMessage != null -> photoMessage
                else -> status.split(" · ").firstOrNull { it.contains("请先") || it.contains("已停止") || it.contains("不支持") }
                    ?: status.substringBefore(" · ")
            }
            Box(Modifier.align(Alignment.BottomStart).padding(start = edge, end = if (sideShutter) 64.dp else 8.dp,
                bottom = if (sideShutter) edge else 64.dp).height(48.dp).widthIn(max = (maxWidth - 72.dp).coerceAtLeast(48.dp))
                .clickable { showDetails = true }.semantics { contentDescription = "查看完整识别结果" }.testTag("camera.status"),
                contentAlignment = Alignment.CenterStart) {
                Text(summary, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.background(overlay, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 4.dp).testTag("camera.statusText"))
            }
        } else Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 76.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(status, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.background(overlay, RoundedCornerShape(12.dp)).padding(10.dp).testTag("camera.status"))
            if (photoMessage != null) Text(photoMessage, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.background(overlay, RoundedCornerShape(12.dp)).padding(10.dp).testTag("camera.photoStatus"))
        }
        val captureSize = if (compact) { if (maxHeight < 220.dp) 48.dp else 56.dp } else 76.dp
        IconButton(onClick = { onCapture(aspect) }, enabled = running && frame != null && !photoBusy,
            modifier = Modifier.align(if (sideShutter) Alignment.CenterEnd else Alignment.BottomCenter)
                .padding(if (sideShutter) PaddingValues(end = edge) else PaddingValues(bottom = edge)).size(captureSize)
                .semantics { contentDescription = "拍照" }.testTag("camera.capture")) {
            Canvas(Modifier.fillMaxSize().padding(if (compact) 2.dp else 4.dp)) {
                val color = if (running && frame != null && !photoBusy) Color.White else Color.Gray
                drawCircle(color, radius = size.minDimension / 2 - 2.dp.toPx(), style = Stroke(if (compact) 2.dp.toPx() else 3.dp.toPx()))
                drawCircle(color, radius = size.minDimension / 2 - if (compact) 6.dp.toPx() else 8.dp.toPx())
            }
            if (photoBusy) CircularProgressIndicator(Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
        }
        if (showDetails) AlertDialog(onDismissRequest = { showDetails = false },
            title = { Text("识别结果", fontSize = 16.sp, maxLines = 1) },
            text = { Column(Modifier.heightIn(max = detailsHeight).verticalScroll(rememberScrollState()).testTag("camera.details")) {
                Text(status)
                if (photoMessage != null) Text(photoMessage)
                if (state.usesDeviceIndex) TextButton(onClick = { showDetails = false; onSelectDevice() },
                    modifier = Modifier.testTag("camera.devices")) { Text("选择摄像头") }
            } }, confirmButton = { TextButton(onClick = { showDetails = false }, modifier = Modifier.testTag("camera.detailsClose")) { Text("关闭") } })
    }
}

@Composable
private fun CameraFloatingButton(description: String, tag: String, enabled: Boolean = true, compact: Boolean = false, onClick: () -> Unit,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp)
        .semantics { contentDescription = description }.testTag(tag)) {
        Box(Modifier.size(if (compact) 32.dp else 48.dp).background(Color.Black.copy(alpha = if (compact) .35f else .55f), CircleShape),
            contentAlignment = Alignment.Center) { Canvas(Modifier.size(if (compact) 18.dp else 24.dp), onDraw = draw) }
    }
}

/** A narrow visual track with a full 48dp touch target, accessible progress actions and keyboard adjustment. */
@Composable
private fun CameraZoomSlider(value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    onChange: (Float) -> Unit, modifier: Modifier) {
    val latestChange by rememberUpdatedState(onChange)
    val current = value.coerceIn(range)
    fun pointerValue(y: Float, height: Int, margin: Float): Float {
        val fraction = ((height - margin - y) / (height - margin * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return range.start + fraction * (range.endInclusive - range.start)
    }
    Canvas(modifier.semantics {
        contentDescription = "摄像头变焦"
        progressBarRangeInfo = ProgressBarRangeInfo(current, range)
        if (!enabled) disabled()
        setProgress { requested ->
            if (enabled && requested.isFinite()) { latestChange(requested.coerceIn(range)); true } else false
        }
    }.testTag("camera.zoom").onKeyEvent { event ->
        if (!enabled || event.type != KeyEventType.KeyDown) false else {
            val direction = when (event.key) { Key.DirectionUp, Key.DirectionRight -> 1; Key.DirectionDown, Key.DirectionLeft -> -1; else -> 0 }
            if (direction == 0) false else { latestChange((current + direction * (range.endInclusive - range.start) / 20).coerceIn(range)); true }
        }
    }.focusable(enabled).pointerInput(enabled, range) {
        if (enabled) detectDragGestures(onDragStart = { latestChange(pointerValue(it.y, size.height, 10.dp.toPx())) }) { change, _ ->
            change.consume()
            latestChange(pointerValue(change.position.y, size.height, 10.dp.toPx()))
        }
    }.pointerInput(enabled, range) {
        if (enabled) detectTapGestures { latestChange(pointerValue(it.y, size.height, 10.dp.toPx())) }
    }) {
        val top = 10.dp.toPx()
        val bottom = size.height - top
        val x = size.width / 2
        val fraction = (current - range.start) / (range.endInclusive - range.start)
        val thumbY = bottom - fraction * (bottom - top)
        val color = if (enabled) Color.White else Color.Gray
        drawLine(Color.White.copy(alpha = .3f), Offset(x, top), Offset(x, bottom), 2.dp.toPx())
        drawLine(color, Offset(x, thumbY), Offset(x, bottom), 2.dp.toPx())
        drawCircle(Color.Black.copy(alpha = .3f), 8.dp.toPx(), Offset(x, thumbY))
        drawCircle(color, 6.dp.toPx(), Offset(x, thumbY))
    }
}
