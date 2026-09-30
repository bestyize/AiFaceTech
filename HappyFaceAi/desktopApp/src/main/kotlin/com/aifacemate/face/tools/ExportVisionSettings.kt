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
        VisionViewport("small-640x360", 640, 360),
        VisionViewport("medium-800x480", 800, 480),
        VisionViewport("foldable-897x813", 897, 813),
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
        val expressionsInstalled = mutableStateOf(true)
        var localActions = 0
        val scene = ImageComposeScene(viewport.width, viewport.height, Density(viewport.density, viewport.font)) {
            VisionSettingsContent(state.value, { state.value = it }, { backed++ }, { saved = state.value },
                yoloSupported = true, yoloInstalled = installed.value, yoloBusy = busy.value, yoloProgress = 45,
                yoloError = modelError.value, onYoloModelAction = { modelActions++; installed.value = !installed.value },
                onYoloCancel = { cancelled++; busy.value = false }, onOpenCamera = { openedCamera++ },
                localVisionSupported = true, localModelsDownloadable = true,
                expressionsInstalled = expressionsInstalled.value, gesturesInstalled = false,
                onLocalModelAction = { localActions++; expressionsInstalled.value = !expressionsInstalled.value })
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
            fun descendants(node: SemanticsNode): List<SemanticsNode> = node.children.flatMap { listOf(it) + descendants(it) }
            fun hasTag(node: SemanticsNode, tag: String) = node.config.getOrNull(SemanticsProperties.TestTag) == tag
            fun depth(node: SemanticsNode) = generateSequence(node.parent) { it.parent }.count()
            fun scrollContainer(tag: String) = nodes().filter {
                it.config.contains(SemanticsProperties.VerticalScrollAxisRange) && it.config.contains(SemanticsActions.ScrollBy) &&
                    descendants(it).any { child -> hasTag(child, tag) }
            }.maxByOrNull { depth(it) }
            fun fixedActions() {
                // Back and save are never inside a scrolling area; other entries may scroll on very short screens.
                for (tag in listOf("vision.back", "vision.save", "vision.tab.yolo", "vision.tab.expression")) {
                    if (tag.startsWith("vision.tab") && scrollContainer(tag) != null) continue
                    val bounds = tagged(tag).boundsInRoot
                    check(bounds.width >= 47 * viewport.density && bounds.height >= 47 * viewport.density) { "$tag too small: $bounds" }
                    check(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= viewport.width && bounds.bottom <= viewport.height) { "$tag outside viewport: $bounds" }
                }
                check(scrollContainer("vision.back") == null && scrollContainer("vision.save") == null) { "back/save must stay fixed" }
            }
            fun scrollTo(tag: String) {
                val container = scrollContainer(tag)
                // Material Slider exposes the 44dp thumb semantics inside its 48dp layout.
                val minimumHeight = if (tag.endsWith("confidence")) 43 else 47
                if (container == null) {
                    val bounds = tagged(tag).boundsInRoot
                    check(bounds.height >= minimumHeight * viewport.density && bounds.top >= 0 && bounds.bottom <= viewport.height) { "$tag not visible: $bounds" }
                    return
                }
                val id = container.id
                fun area() = nodes().single { it.id == id }
                val range = container.config[SemanticsProperties.VerticalScrollAxisRange]
                val action = container.config[SemanticsActions.ScrollBy].action!!
                fun scroll(delta: Float) { action(0f, delta); repeat(8) { frame() } }
                scroll(-range.maxValue())
                repeat((range.maxValue() / (24 * viewport.density)).toInt() + 4) {
                    val bounds = tagged(tag).boundsInRoot
                    val available = area().boundsInRoot
                    if (bounds.height >= minimumHeight * viewport.density && bounds.top >= available.top && bounds.bottom <= available.bottom) return
                    scroll(24 * viewport.density)
                }
                scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.use {
                    File(output, "failure-${viewport.name}.png").writeBytes(it.bytes)
                }
                error("${viewport.name}: $tag cannot be reached by scrolling; bounds=${tagged(tag).boundsInRoot}, area=${area().boundsInRoot}, scroll=${range.value()}/${range.maxValue()}")
            }
            fun click(tag: String, scroll: Boolean = true) {
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
            val dpWidth = viewport.width / viewport.density / viewport.font
            val hasRail = nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == "vision.rail" }
            check(hasRail == (viewport.width > viewport.height && dpWidth < 680)) { "${viewport.name}: small landscape uses the side rail, rail=$hasRail" }
            if (viewport.width > viewport.height) check(tagged("vision.tab.gesture").boundsInRoot.left < tagged("vision.configuration").boundsInRoot.left) {
                "${viewport.name}: landscape keeps capabilities beside the configuration"
            }
            if (viewport.width > viewport.height) {
                val back = tagged("vision.back").boundsInRoot
                val detail = tagged("vision.configuration").boundsInRoot
                check(back.right <= detail.left && detail.top <= back.top + viewport.density) { "${viewport.name}: landscape has no top bar; back=$back detail=$detail" }
            }
            click("vision.yolo.modelAction", scroll = true)
            check(installed.value && modelActions == 1)
            click("vision.yolo.modelAction", scroll = true)
            check(!installed.value && modelActions == 2)
            busy.value = true; frame()
            check(tagged("vision.save").config.contains(SemanticsProperties.Disabled))
            check(tagged("vision.camera").config.contains(SemanticsProperties.Disabled))
            check(nodes().none { it.config.getOrNull(SemanticsProperties.TestTag) == "vision.yolo.modelAction" }) { "Cancel replaces the model action while busy" }
            scrollTo("vision.yolo.cancel"); shot("downloading")
            click("vision.yolo.cancel"); check(cancelled == 1 && !busy.value)
            modelError.value = "下载失败，请重试"; frame()
            check(tagged("vision.yolo.modelError").config[SemanticsProperties.Text].any { it.text.contains("下载失败") })
            modelError.value = null; frame()
            click("vision.yolo.enabled", scroll = true)
            check(tagged("vision.tab.yolo").config[SemanticsProperties.StateDescription] == "需下载模型")
            click("vision.save")
            check(saved?.yoloEnabled == true && saved?.yoloModelPath == "") { "Empty path should select downloaded model" }
            saved = null
            check(nodes().none { it.config.getOrNull(SemanticsProperties.TestTag) == "vision.yolo.modelPath" }) { "Custom path starts collapsed" }
            click("vision.yolo.advanced", scroll = true)
            scrollTo("vision.yolo.modelPath")
            check(tagged("vision.yolo.modelPath").config[SemanticsActions.SetText].action?.invoke(AnnotatedString("/models/yolo26m.onnx")) == true)
            frame()
            check(tagged("vision.tab.yolo").config[SemanticsProperties.StateDescription] == "已开启")
            scrollTo("vision.yolo.confidence")
            check(tagged("vision.yolo.confidence").config[SemanticsActions.SetProgress].action?.invoke(0.4f) == true)
            frame(); check(state.value.yoloConfidenceThreshold == 0.4f)
            click("vision.yolo.confidenceReset", scroll = true); check(state.value.yoloConfidenceThreshold == 0.25f)
            shot("yolo-configured")
            click("vision.tab.expression"); shot("expression")
            click("vision.expression.enabled", scroll = true)
            click("vision.expression.modelAction", scroll = true); check(localActions == 1 && !expressionsInstalled.value)
            click("vision.expression.modelAction", scroll = true); check(localActions == 2 && expressionsInstalled.value)
            click("vision.expression.advanced", scroll = true)
            scrollTo("vision.expression.modelPath")
            check(tagged("vision.expression.modelPath").config[SemanticsActions.SetText].action?.invoke(AnnotatedString("/models/expression.txt")) == true)
            frame()
            click("vision.tab.gesture"); shot("gesture")
            click("vision.save"); check(saved == null) { "Invalid expression path must block save" }
            check(tagged("vision.tab.expression").config.getOrNull(SemanticsProperties.Selected) == true) { "Validation jumps to the invalid tab" }
            scrollTo("vision.expression.modelPath")
            check(nodes().any { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(".onnx 扩展名") } == true })
            shot("expression-invalid")
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
