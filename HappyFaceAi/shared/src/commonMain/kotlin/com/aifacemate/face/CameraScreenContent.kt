package com.aifacemate.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.camera.*
import com.aifacemate.face.vision.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

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
) {
    val textMeasurer = rememberTextMeasurer()
    var showDevices by remember { mutableStateOf(false) }
    var index by remember(state.deviceId) { mutableStateOf(state.deviceId ?: "0") }
    val running = state.status == CameraStatus.Running
    val overlay = Color.Black.copy(alpha = .55f)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).clipToBounds()) {
        val aspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val short = maxHeight < 400.dp
        Box(Modifier.fillMaxSize().testTag("camera.preview"), contentAlignment = Alignment.Center) {
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
        }
        if (captureFlash) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .65f)))
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CameraFloatingButton("返回", "camera.back", onClick = onBack) {
                drawLine(Color.White, Offset(size.width * .65f, size.height * .15f), Offset(size.width * .3f, size.height * .5f), 2.dp.toPx())
                drawLine(Color.White, Offset(size.width * .3f, size.height * .5f), Offset(size.width * .65f, size.height * .85f), 2.dp.toPx())
            }
            Spacer(Modifier.weight(1f))
            if (state.usesDeviceIndex) TextButton(onClick = { showDevices = true }, modifier = Modifier.testTag("camera.devices")) {
                Text("设备 ${state.deviceId ?: "0"}", color = Color.White)
            }
            if (state.devices.size > 1) CameraFloatingButton("切换摄像头", "camera.flip", enabled = running && !photoBusy,
                onClick = {
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
        if (state.maxZoomRatio > state.minZoomRatio) Column(
            Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(end = 12.dp)
                .background(overlay, RoundedCornerShape(28.dp)).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${(state.zoomRatio * 10).roundToInt() / 10f}×", color = Color.White, fontSize = 12.sp)
            CameraZoomSlider(state.zoomRatio, state.minZoomRatio..state.maxZoomRatio, running && !photoBusy,
                onZoomChange, Modifier.width(48.dp).height(if (short) 112.dp else 200.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(start = 16.dp, end = 76.dp, bottom = if (short) 94.dp else 112.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(state.error ?: recognition, color = Color.White, fontSize = 12.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.background(overlay, RoundedCornerShape(12.dp)).padding(10.dp).testTag("camera.status"))
            if (photoMessage != null) Text(photoMessage, color = Color.White, fontSize = 12.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.background(overlay, RoundedCornerShape(12.dp)).padding(10.dp).testTag("camera.photoStatus"))
        }
        IconButton(onClick = { onCapture(aspect) }, enabled = running && frame != null && !photoBusy,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 16.dp).size(76.dp)
                .semantics { contentDescription = "拍照" }.testTag("camera.capture")) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val color = if (running && frame != null && !photoBusy) Color.White else Color.Gray
                drawCircle(color, radius = size.minDimension / 2 - 2.dp.toPx(), style = Stroke(3.dp.toPx()))
                drawCircle(color, radius = size.minDimension / 2 - 8.dp.toPx())
            }
            if (photoBusy) CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 2.dp)
        }
        if (showDevices) AlertDialog(onDismissRequest = { showDevices = false }, title = { Text("选择摄像头") },
            text = { OutlinedTextField(index, { index = it.filter(Char::isDigit).take(2) }, singleLine = true,
                label = { Text("设备编号（0–15）") }, modifier = Modifier.testTag("camera.deviceIndex")) },
            confirmButton = { TextButton(onClick = { onDeviceChange(index); showDevices = false }, enabled = index.toIntOrNull() in 0..15,
                modifier = Modifier.testTag("camera.deviceConfirm")) { Text("使用此设备") } },
            dismissButton = { TextButton(onClick = { showDevices = false }) { Text("关闭") } })
    }
}

@Composable
private fun CameraFloatingButton(description: String, tag: String, enabled: Boolean = true, onClick: () -> Unit,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = .55f), CircleShape)
        .semantics { contentDescription = description }.testTag(tag)) { Canvas(Modifier.size(24.dp), onDraw = draw) }
}

/** Swapped constraints keep a rotated Material slider's pointer and accessibility bounds on the visible track. */
@Composable
private fun CameraZoomSlider(value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    onChange: (Float) -> Unit, modifier: Modifier) {
    Layout(content = {
        Slider(value.coerceIn(range), onChange, enabled = enabled, valueRange = range,
            modifier = Modifier.graphicsLayer { rotationZ = -90f }.semantics { contentDescription = "摄像头变焦" }.testTag("camera.zoom"),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.Gray))
    }, modifier = modifier) { measurables, constraints ->
        val control = measurables.single().measure(Constraints(minWidth = constraints.minHeight, maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth, maxHeight = constraints.maxWidth))
        layout(control.height, control.width) { control.place((control.height - control.width) / 2, (control.width - control.height) / 2) }
    }
}
