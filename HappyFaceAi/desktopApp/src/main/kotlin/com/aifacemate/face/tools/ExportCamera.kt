package com.aifacemate.face.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.aifacemate.face.CameraDetections
import com.aifacemate.face.CameraScreenContent
import com.aifacemate.face.CameraScreen
import com.aifacemate.face.camera.*
import com.aifacemate.face.theme.AppColors
import com.aifacemate.face.vision.PixelBox
import com.aifacemate.face.vision.VisionDetection
import com.aifacemate.face.vision.VisionPreferences
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
    val viewports = listOf(CameraViewport("small", 480, 320), CameraViewport("medium", 800, 480),
        CameraViewport("wide", 1440, 900), CameraViewport("portrait", 390, 844),
        CameraViewport("font130", 480, 320, font = 1.3f), CameraViewport("density150", 480, 320, density = 1.5f),
        CameraViewport("phone", 2400, 1080, density = 2.75f))
    for (viewport in viewports) {
        val state = mutableStateOf(CameraState(devices = listOf(CameraDevice("back", "后置摄像头", CameraLens.Back), CameraDevice("front", "前置摄像头", CameraLens.Front))))
        var starts = 0
        var stops = 0
        var backed = 0
        val scene = ImageComposeScene(viewport.width, viewport.height, Density(viewport.density, viewport.font)) {
            MaterialTheme(colorScheme = darkColorScheme(primary = AppColors.Amber, background = AppColors.Background, surface = AppColors.Card)) {
                val display = CameraFrame(frame.width, frame.height, frame.rgba, frame.sequence, state.value.deviceId == "front")
                CameraScreenContent(state.value, bitmap, display,
                    CameraDetections(640, 480, display.mirrored, listOf(VisionDetection(0, .85f, PixelBox(140f, 90f, 370f, 400f)))),
                    "person 85% · 本机识别", { backed++ },
                    { starts++; state.value = state.value.copy(status = CameraStatus.Running, error = null) },
                    { stops++; state.value = state.value.copy(status = CameraStatus.Idle) },
                    { state.value = state.value.copy(deviceId = it) })
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
                for (tag in listOf("camera.back", "camera.devices", "camera.toggle")) {
                    val bounds = tagged(tag).boundsInRoot
                    check(bounds.height >= 47 * viewport.density && bounds.width >= 47 * viewport.density) { "$tag too small: $bounds" }
                    check(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= viewport.width && bounds.bottom <= viewport.height) { "$tag clipped: $bounds" }
                }
                check(tagged("camera.preview").boundsInRoot.height >= 40 * viewport.density)
            }
            fun click(tag: String) { check(tagged(tag).config[SemanticsActions.OnClick].action?.invoke() == true); render() }
            fun shot(name: String) { scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.use { File(output, "${viewport.name}-$name.png").writeBytes(it.bytes) } }
            render(); fixedControls(); check(starts == 0); shot("idle")
            click("camera.toggle"); check(starts == 1); fixedControls(); shot("preview")
            click("camera.devices"); click("camera.device.front"); check(state.value.deviceId == "front"); shot("mirrored")
            click("camera.toggle"); check(stops == 1)
            state.value = state.value.copy(status = CameraStatus.PermissionDenied, error = "请在系统设置中允许摄像头访问，然后重试")
            render(); fixedControls(); shot("permission")
            click("camera.toggle"); check(starts == 2)
            click("camera.back"); check(backed == 1)
            println("PASS ${viewport.name}: fixed controls, explicit start, stop, front camera, permission/retry, back, overlay")
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
            operations += "start:${deviceId ?: "back"}"
            state.value = state.value.copy(status = CameraStatus.Running, deviceId = deviceId ?: "back")
            frames.value = frame
        }
        override suspend fun stop() {
            // Deliberately slow cleanup catches an old session stopping a newly selected device.
            delay(20)
            operations += "stop"
            state.value = state.value.copy(status = CameraStatus.Idle)
            frames.value = null
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
            MaterialTheme { CameraScreen({}, camera, VisionPreferences()) }
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
        render(); await { operations.contains("stop") }
        check(operations.none { it.startsWith("start") })
        click("camera.toggle"); await { camera.state.value.status == CameraStatus.Running }
        click("camera.devices"); click("camera.device.front")
        await { camera.state.value.status == CameraStatus.Running && camera.state.value.deviceId == "front" }
        repeat(8) { render(); Thread.sleep(10) }
        check(camera.state.value.status == CameraStatus.Running) { "Old cleanup stopped the new camera" }
        check(operations.indexOf("start:front") > operations.indexOf("start:back"))
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        await { camera.state.value.status == CameraStatus.Idle && camera.frames.value == null }
        val starts = operations.count { it.startsWith("start") }
        owner.registry.currentState = Lifecycle.State.RESUMED
        repeat(5) { render(); Thread.sleep(10) }
        check(operations.count { it.startsWith("start") } == starts) { "Camera must not reopen automatically after backgrounding" }
        click("camera.toggle"); await { camera.state.value.status == CameraStatus.Running }
    } finally { scene.close() }
    check(camera.closed && camera.frames.value == null)
    println("PASS real camera route lifecycle: explicit start, serial device switch, background stop, no automatic reopen, owner disposal")
}
