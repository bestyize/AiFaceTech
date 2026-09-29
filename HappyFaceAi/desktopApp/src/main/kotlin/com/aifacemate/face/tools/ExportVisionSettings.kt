package com.aifacemate.face.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.aifacemate.face.VisionSettingsContent
import com.aifacemate.face.feature.lifecenter.*
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.vision.VisionPreferences
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private data class VisionViewport(val name: String, val width: Int, val height: Int, val density: Float = 1f, val font: Float = 1f)

/** Uses the actual Compose semantics tree without saved settings, cameras, models or network. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/vision-settings").apply { mkdirs() }
    val viewports = listOf(
        VisionViewport("small-480x320", 480, 320),
        VisionViewport("medium-800x480", 800, 480),
        VisionViewport("wide-1440x900", 1440, 900),
        VisionViewport("portrait-390x844", 390, 844),
        VisionViewport("font130", 480, 320, font = 1.3f),
        VisionViewport("density150", 480, 320, density = 1.5f),
        VisionViewport("phone-2400x1080", 2400, 1080, density = 2.75f),
    )
    for (viewport in viewports) {
        val state = mutableStateOf(VisionPreferences())
        var saved: VisionPreferences? = null
        var backed = 0
        var modelActions = 0
        var cancelled = 0
        var openedCamera = 0
        val installed = mutableStateOf(false)
        val busy = mutableStateOf(false)
        val modelError = mutableStateOf<String?>(null)
        val scene = ImageComposeScene(viewport.width, viewport.height, Density(viewport.density, viewport.font)) {
            VisionSettingsContent(state.value, { state.value = it }, { backed++ }, { saved = state.value },
                yoloSupported = true, yoloInstalled = installed.value, yoloBusy = busy.value, yoloProgress = 45,
                yoloError = modelError.value, onYoloModelAction = { modelActions++; installed.value = !installed.value },
                onYoloCancel = { cancelled++; busy.value = false }, onOpenCamera = { openedCamera++ })
        }
        try {
            var time = 0L
            fun frame() { time += 100_000_000L; scene.render(time).close() }
            fun nodes(): List<SemanticsNode> {
                fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { visit(it) }
                return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
            }
            fun tagged(tag: String) = nodes().singleOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
                ?: error("${viewport.name}: missing $tag")
            fun fixedActions() {
                for (tag in listOf("vision.back", "vision.save", "vision.tab.yolo", "vision.tab.expression")) {
                    val bounds = tagged(tag).boundsInRoot
                    check(bounds.width >= 47 * viewport.density && bounds.height >= 47 * viewport.density) { "$tag too small: $bounds" }
                    check(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= viewport.width && bounds.bottom <= viewport.height) { "$tag outside viewport: $bounds" }
                }
            }
            fun scrollTo(tag: String) {
                val area = tagged("vision.configuration")
                val range = area.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return
                val action = area.config.getOrNull(SemanticsActions.ScrollBy)?.action ?: return
                fun scroll(delta: Float) { action(0f, delta); repeat(8) { frame() } }
                scroll(-range.maxValue())
                repeat((range.maxValue() / (24 * viewport.density)).toInt() + 4) {
                    val bounds = tagged(tag).boundsInRoot
                    val available = tagged("vision.configuration").boundsInRoot
                    // Material Slider exposes the 44dp thumb semantics inside its 48dp layout.
                    val minimumHeight = if (tag.endsWith("confidence")) 43 else 47
                    if (bounds.height >= minimumHeight * viewport.density && bounds.top >= available.top && bounds.bottom <= available.bottom) return
                    scroll(24 * viewport.density)
                }
                scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.use {
                    File(output, "failure-${viewport.name}.png").writeBytes(it.bytes)
                }
                error("${viewport.name}: $tag cannot be reached by scrolling; bounds=${tagged(tag).boundsInRoot}, area=${tagged("vision.configuration").boundsInRoot}, scroll=${range.value()}/${range.maxValue()}")
            }
            fun click(tag: String, scroll: Boolean = false) {
                if (scroll) scrollTo(tag)
                check(tagged(tag).config[SemanticsActions.OnClick].action?.invoke() == true)
                frame(); fixedActions()
            }
            fun shot(name: String) {
                scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.use {
                    File(output, "${viewport.name}-$name.png").writeBytes(it.bytes)
                }
            }
            frame(); fixedActions(); shot("yolo")
            click("vision.yolo.modelAction", scroll = true)
            check(installed.value && modelActions == 1)
            click("vision.yolo.modelAction", scroll = true)
            check(!installed.value && modelActions == 2)
            busy.value = true; frame()
            check(tagged("vision.save").config.contains(SemanticsProperties.Disabled))
            check(tagged("vision.yolo.modelAction").config.contains(SemanticsProperties.Disabled))
            scrollTo("vision.yolo.cancel"); shot("downloading")
            click("vision.yolo.cancel"); check(cancelled == 1 && !busy.value)
            modelError.value = "下载失败，请重试"; frame()
            check(tagged("vision.yolo.modelError").config[SemanticsProperties.Text].any { it.text.contains("下载失败") })
            modelError.value = null; frame()
            click("vision.yolo.enabled", scroll = true)
            click("vision.save")
            check(saved?.yoloEnabled == true && saved?.yoloModelPath == "") { "Empty path should select downloaded model" }
            saved = null
            scrollTo("vision.yolo.modelPath")
            check(tagged("vision.yolo.modelPath").config[SemanticsActions.SetText].action?.invoke(AnnotatedString("/models/yolo26m.onnx")) == true)
            frame()
            scrollTo("vision.yolo.confidence")
            check(tagged("vision.yolo.confidence").config[SemanticsActions.SetProgress].action?.invoke(0.4f) == true)
            frame(); check(state.value.yoloConfidenceThreshold == 0.4f)
            click("vision.tab.expression"); shot("expression")
            click("vision.expression.enabled", scroll = true)
            click("vision.save"); check(saved == null)
            scrollTo("vision.expression.modelPath")
            check(tagged("vision.expression.modelPath").config[SemanticsActions.SetText].action?.invoke(AnnotatedString("/models/expression.onnx")) == true)
            frame()
            scrollTo("vision.expression.confidence")
            check(tagged("vision.expression.confidence").config[SemanticsActions.SetProgress].action?.invoke(0.8f) == true)
            frame(); check(state.value.expressionConfidenceThreshold == 0.8f)
            click("vision.tab.yolo")
            check(state.value.yoloEnabled && state.value.expressionEnabled)
            check(state.value.yoloModelPath == "/models/yolo26m.onnx")
            click("vision.save"); check(saved == state.value)
            click("vision.camera", scroll = true); check(openedCamera == 1)
            click("vision.back"); check(backed == 1)
            println("PASS ${viewport.name}: model download/delete/cancel, busy/error states, tabs, fields, thresholds, validation, save/back, scroll reachability")
        } finally { scene.close() }
    }
    var navigation: LifeRoute? = null
    val center = ImageComposeScene(800, 480, Density(1f)) {
        MaterialTheme { LifeCenterScreen(LifeCenterUiState(), FaceExpression.HAPPY, 1f) { navigation = it } }
    }
    try {
        center.render().close()
        fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { visit(it) }
        val feature = center.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
            .single { it.config.getOrNull(SemanticsProperties.TestTag) == "life.feature.Vision" }
        check(feature.config[SemanticsActions.OnClick].action?.invoke() == true)
        check(navigation == LifeRoute.Vision)
        println("PASS life center vision navigation")
    } finally { center.close() }
    val unsupported = ImageComposeScene(800, 480, Density(1f)) {
        VisionSettingsContent(VisionPreferences(), {}, {}, {}, yoloSupported = false)
    }
    try {
        unsupported.render().encodeToData(EncodedImageFormat.PNG)!!.use {
            File(output, "unsupported-device.png").writeBytes(it.bytes)
        }
        fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { visit(it) }
        val action = unsupported.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
            .single { it.config.getOrNull(SemanticsProperties.TestTag) == "vision.yolo.modelAction" }
        check(action.config.contains(SemanticsProperties.Disabled))
        println("PASS unsupported platform model action disabled")
    } finally { unsupported.close() }
    println("Exported vision settings screenshots to ${output.absolutePath}")
}
