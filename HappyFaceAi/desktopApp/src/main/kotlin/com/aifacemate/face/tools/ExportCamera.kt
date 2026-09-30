package com.aifacemate.face.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.aifacemate.face.CameraDetections
import com.aifacemate.face.CameraScreenContent
import com.aifacemate.face.CameraScreen
import com.aifacemate.face.camera.*
import com.aifacemate.face.theme.AppColors
import com.aifacemate.face.capability.ProviderRegistry
import com.aifacemate.face.capability.currentDeviceEnvironment
import com.aifacemate.face.vision.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private data class CameraViewport(val name: String, val width: Int, val height: Int, val density: Float = 1f, val font: Float = 1f)

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/camera").apply { mkdirs() }
    val frame = CameraFrame(640, 480, ByteArray(640 * 480 * 4).apply {
        for (y in 0 until 480) for (x in 0 until 640) {
            val at = (y * 640 + x) * 4
            this[at] = (30 + x / 4).toByte()
            this[at + 1] = (50 + y / 4).toByte()
            this[at + 2] = 80
            this[at + 3] = -1
        }
    }, 1)
    val bitmap = frame.toPreviewBitmap()
    val viewports = listOf(CameraViewport("tiny", 320, 240), CameraViewport("small", 480, 320), CameraViewport("medium", 800, 480),
        CameraViewport("wide", 1440, 900), CameraViewport("portrait", 390, 844),
        CameraViewport("font130", 480, 320, font = 1.3f), CameraViewport("tiny-font200", 320, 240, font = 2f),
        CameraViewport("density150", 480, 320, density = 1.5f),
        CameraViewport("tiny-density150", 320, 240, density = 1.5f),
        CameraViewport("phone", 2400, 1080, density = 2.75f))
    for (viewport in viewports) {
        val state = mutableStateOf(CameraState(status = CameraStatus.Running, deviceId = "front", maxZoomRatio = 4f,
            devices = listOf(CameraDevice("back", "后置摄像头", CameraLens.Back), CameraDevice("front", "前置摄像头", CameraLens.Front))))
        var starts = 0
        var stops = 0
        var backed = 0
        var captured = 0
        val focusRequests = mutableListOf<CameraFocusPoint>()
        val scene = ImageComposeScene(viewport.width, viewport.height, Density(viewport.density, viewport.font)) {
            MaterialTheme(colorScheme = darkColorScheme(primary = AppColors.Amber, background = AppColors.Background, surface = AppColors.Card)) {
                val display = CameraFrame(frame.width, frame.height, frame.rgba, frame.sequence, state.value.deviceId == "front")
                CameraScreenContent(state.value, bitmap, display,
                    CameraDetections(display.mirrored, VisionObservation(640, 480,
                        objects = listOf(DetectedObject("person", 0, NormalizedBox(140f / 640, 90f / 480, 370f / 640, 400f / 480), .85f)),
                        faces = listOf(FaceObservation(DetectedFace(NormalizedBox(210f / 640, 110f / 480, 300f / 640, 220f / 480), .9f),
                            ExpressionResult(Expression.Happy, .91f))),
                        hands = listOf(HandObservation(DetectedHand(NormalizedBox(400f / 640, 200f / 480, 480f / 640, 300f / 480), .95f,
                            emptyList()), HandGesture.ThumbsUp)),
                    )),
                    "开心 91% · 点赞 · person 85% · 108 ms", { backed++ },
                    { starts++; state.value = state.value.copy(status = CameraStatus.Running, error = null) },
                    { stops++; state.value = state.value.copy(status = CameraStatus.Idle) },
                    { state.value = state.value.copy(deviceId = it) },
                    onCapture = { check(it > 0); captured++ },
                    onZoomChange = { state.value = state.value.copy(zoomRatio = it) },
                    focusResult = CameraFocusResult.Focused, onFocus = { focusRequests += it })
            }
        }
        try {
            var time = 0L
            fun render() { time += 100_000_000L; scene.render(time).close() }
            fun nodes(): List<SemanticsNode> {
                fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { visit(it) }
                return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
            }
            fun tagged(tag: String) = nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
            fun fixedControls() {
                for (tag in listOf("camera.back", "camera.flip", "camera.capture")) {
                    val bounds = tagged(tag).boundsInRoot
                    check(bounds.height >= 47 * viewport.density && bounds.width >= 47 * viewport.density) { "$tag too small: $bounds" }
                    check(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= viewport.width && bounds.bottom <= viewport.height) { "$tag clipped: $bounds" }
                }
                val preview = tagged("camera.preview").boundsInRoot
                check(preview.width == viewport.width.toFloat() && preview.height == viewport.height.toFloat()) { "Preview must fill the screen: $preview" }
                val compactLandscape = viewport.height / viewport.density < 400 && viewport.width > viewport.height
                if (compactLandscape) {
                    val shutter = tagged("camera.capture").boundsInRoot
                    check(shutter.left >= viewport.width * .75f) { "Compact shutter must stay at the right edge: $shutter" }
                    check(shutter.height <= 57 * viewport.density) { "Compact shutter too large: $shutter" }
                    val status = tagged("camera.status").boundsInRoot
                    check(status.top >= viewport.height * .65f) { "Compact status must stay at the bottom edge: $status" }
                    val edgeControls = listOf("camera.back", "camera.flip", "camera.capture", "camera.zoomToggle")
                        .map { tagged(it).boundsInRoot }
                    for ((index, a) in edgeControls.withIndex()) for (b in edgeControls.drop(index + 1)) {
                        check(a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top) {
                            "Compact controls overlap: $a, $b"
                        }
                    }
                }
            }
            fun click(tag: String) { check(tagged(tag).config[SemanticsActions.OnClick].action?.invoke() == true); render() }
            fun shot(name: String) { scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.use { File(output, "${viewport.name}-$name.png").writeBytes(it.bytes) } }
            render(); fixedControls(); check(starts == 0); shot("mirrored")
            val tap = Offset(viewport.width * .45f, viewport.height * .45f)
            scene.sendPointerEvent(PointerEventType.Press, tap); render()
            scene.sendPointerEvent(PointerEventType.Release, tap); render()
            val expectedFocus = CameraPreviewTransform.crop(frame.width, frame.height, viewport.width.toFloat(), viewport.height.toFloat(), true)
                .focusPoint(tap.x, tap.y, frame.height)
            check(focusRequests.size == 1 && kotlin.math.abs(focusRequests.single().x - expectedFocus.x) < .001f &&
                kotlin.math.abs(focusRequests.single().y - expectedFocus.y) < .001f) { "Tap focus must map the mirrored, cropped preview" }
            shot("focus")
            click("camera.flip"); check(state.value.deviceId == "back"); fixedControls(); shot("preview")
            val shutter = tagged("camera.capture").boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, shutter); render()
            scene.sendPointerEvent(PointerEventType.Release, shutter); render()
            check(captured == 1 && focusRequests.size == 1) { "Shutter taps must not also focus the underlying preview" }
            if (viewport.height / viewport.density < 400) {
                click("camera.status")
                check(nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == "camera.details" })
                check(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.contains("person 85%") } == true })
                repeat(3) { render() }
                shot("details")
                click("camera.detailsClose")
                repeat(3) { render() }
                check(nodes().none { it.config.getOrNull(SemanticsProperties.TestTag) == "camera.zoomPanel" }) { "Small-screen zoom must start collapsed" }
                click("camera.zoomToggle")
            }
            val zoomBounds = tagged("camera.zoom").boundsInRoot
            val highZoom = Offset(zoomBounds.center.x, zoomBounds.top + 10 * viewport.density)
            scene.sendPointerEvent(PointerEventType.Press, highZoom)
            render()
            scene.sendPointerEvent(PointerEventType.Release, highZoom)
            render()
            check(state.value.zoomRatio >= 3.8f) { "${viewport.name}: upper zoom tap did not update ${state.value.zoomRatio} at $highZoom in $zoomBounds" }
            check(focusRequests.size == 1) { "Zoom taps must not focus the underlying preview" }
            check(tagged("camera.zoom").config[SemanticsActions.SetProgress].action?.invoke(2f) == true)
            render(); check(state.value.zoomRatio == 2f); shot("zoom")
            state.value = state.value.copy(status = CameraStatus.PermissionDenied, error = "请在系统设置中允许摄像头访问，然后重试")
            render(); fixedControls(); shot("permission")
            click("camera.retry"); check(starts == 1)
            click("camera.back"); check(backed == 1)
            println("PASS ${viewport.name}: full-screen preview, floating controls, camera flip, zoom, capture, permission/retry, back")
        } finally { scene.close() }
    }
    val state = mutableStateOf(CameraState(usesDeviceIndex = true))
    val desktop = ImageComposeScene(800, 480, Density(1f)) {
        MaterialTheme { CameraScreenContent(state.value, null, null, null, "", {}, {}, {}, { state.value = state.value.copy(deviceId = it) }) }
    }
    try {
        desktop.render().close()
        fun nodes(): List<SemanticsNode> {
            fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { visit(it) }
            return desktop.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
        }
        fun tagged(tag: String) = nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        tagged("camera.devices").config[SemanticsActions.OnClick].action!!.invoke(); desktop.render().close()
        tagged("camera.deviceIndex").config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("99")); desktop.render().close()
        check(tagged("camera.deviceConfirm").config.contains(SemanticsProperties.Disabled))
        tagged("camera.deviceIndex").config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("1")); desktop.render().close()
        tagged("camera.deviceConfirm").config[SemanticsActions.OnClick].action!!.invoke(); desktop.render().close()
        check(state.value.deviceId == "1")
        println("PASS desktop camera index selection and validation")
    } finally { desktop.close() }
    verifySessionLifecycle(frame)
    println("Exported camera screenshots to ${output.absolutePath}")
}

@OptIn(ExperimentalComposeUiApi::class)
private fun verifySessionLifecycle(frame: CameraFrame) {
    val operations = mutableListOf<String>()
    val camera = object : CameraController {
        override val state = MutableStateFlow(CameraState(devices = listOf(
            CameraDevice("back", "后置摄像头", CameraLens.Back), CameraDevice("front", "前置摄像头", CameraLens.Front))))
        override val frames = MutableStateFlow<CameraFrame?>(null)
        var closed = false
        override suspend fun start(deviceId: String?) {
            check(!closed)
            operations += "start:${deviceId ?: "front"}"
            state.value = state.value.copy(status = CameraStatus.Running, deviceId = deviceId ?: "front")
            frames.value = frame
        }
        override suspend fun stop() {
            // Deliberately slow cleanup catches an old session stopping a newly selected device.
            delay(20)
            operations += "stop"
            state.value = state.value.copy(status = CameraStatus.Idle)
            frames.value = null
        }
        override suspend fun focus(point: CameraFocusPoint): CameraFocusResult {
            operations += "focus:${point.x},${point.y}"
            return CameraFocusResult.Focused
        }
        override fun close() {
            if (closed) return
            closed = true
            operations += "close"
            state.value = state.value.copy(status = CameraStatus.Idle)
            frames.value = null
        }
    }
    val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle = registry
    }
    owner.registry.currentState = Lifecycle.State.RESUMED
    val scene = ImageComposeScene(800, 480, Density(1f)) {
        CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            MaterialTheme { CameraScreen({}, camera, VisionPreferences(), PerceptionService(ProviderRegistry(emptyList(), currentDeviceEnvironment()))) }
        }
    }
    try {
        var time = 0L
        fun render() { time += 20_000_000L; scene.render(time).close() }
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + 3_000_000_000L
            while (!condition() && System.nanoTime() < deadline) { render(); Thread.sleep(10) }
            check(condition()) { "Camera lifecycle timed out: $operations" }
            render()
        }
        fun tagged(tag: String): SemanticsNode {
            fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { visit(it) }
            return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
                .single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        }
        fun click(tag: String) { check(tagged(tag).config[SemanticsActions.OnClick].action?.invoke() == true); render() }
        render(); await { camera.state.value.status == CameraStatus.Running }
        check(camera.state.value.deviceId == "front")
        click("camera.preview")
        await { operations.any { it.startsWith("focus:") } }
        click("camera.flip")
        await { camera.state.value.status == CameraStatus.Running && camera.state.value.deviceId == "back" }
        repeat(8) { render(); Thread.sleep(10) }
        check(camera.state.value.status == CameraStatus.Running) { "Old cleanup stopped the new camera" }
        check(operations.indexOf("start:back") > operations.indexOf("start:front"))
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        await { camera.state.value.status == CameraStatus.Idle && camera.frames.value == null }
        val starts = operations.count { it.startsWith("start") }
        owner.registry.currentState = Lifecycle.State.RESUMED
        await { camera.state.value.status == CameraStatus.Running && operations.count { it.startsWith("start") } > starts }
    } finally { scene.close() }
    check(camera.closed && camera.frames.value == null)
    println("PASS real camera route lifecycle: automatic front-camera start, serial switch, background stop, foreground resume, owner disposal")
}
