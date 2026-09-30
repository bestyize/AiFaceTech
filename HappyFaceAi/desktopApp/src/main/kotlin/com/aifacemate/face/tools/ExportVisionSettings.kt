package com.aifacemate.face.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.aifacemate.face.VisionSettingsContent
import com.aifacemate.face.VisionSettingsScreen
import com.aifacemate.face.feature.lifecenter.*
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.capability.*
import com.aifacemate.face.vision.*
import com.aifacemate.face.settingsState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private data class VisionViewport(val name: String, val width: Int, val height: Int, val density: Float = 1f, val font: Float = 1f)

/** Metadata-only providers: the page reads names, sizes and availability from them, and they are never opened. */
private class FixtureProvider<C : AutoCloseable>(
    override val id: String,
    override val capability: CapabilityKey<C>,
    override val displayName: String,
    override val description: String,
    override val models: List<ModelSpec>,
    private val supported: Boolean,
    private val installed: () -> Boolean,
) : Provider<C> {
    override fun availability(environment: DeviceEnvironment) = when {
        !supported -> Availability.Unsupported("此设备暂不支持 ONNX Runtime 本地推理")
        models.isEmpty() || installed() -> Availability.Ready
        else -> Availability.NeedsModels(models)
    }
    override suspend fun open(): C = error("UI fixtures are never opened")
}

private fun fixtureRegistry(supported: Boolean = true, installed: (VisionTask) -> Boolean): ProviderRegistry {
    fun <C : AutoCloseable> provider(id: String, capability: CapabilityKey<C>, name: String, detail: String, task: VisionTask,
        models: List<ModelSpec>, builtIn: Boolean = false) =
        FixtureProvider(id, capability, name, detail, models, supported || builtIn) { installed(task) }
    return ProviderRegistry(listOf(
        provider("fixture.objects", ObjectDetector, "YOLO26m", "COCO 80 类", VisionTask.Objects, listOf(VisionModels.yolo26m)),
        provider("fixture.faces", FaceDetector, "YuNet", "人脸检测", VisionTask.Expressions, listOf(VisionModels.yunet)),
        provider("fixture.expressions", ExpressionClassifier, "MobileFaceNet", "七类表情", VisionTask.Expressions, listOf(VisionModels.mobileFaceNetExpression)),
        provider("fixture.hands", HandLandmarker, "MediaPipe", "掌心检测 + 21 点骨架", VisionTask.Gestures,
            listOf(VisionModels.palmDetection, VisionModels.handLandmarks)),
        provider("fixture.hands.npu", HandLandmarker, "NPU 加速", "设备内置模型", VisionTask.Gestures, emptyList(), builtIn = true),
    ), currentDeviceEnvironment())
}

/** Uses the actual Compose semantics tree without saved settings, cameras, models or network. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/vision-settings").apply { mkdirs() }
    val viewports = listOf(
        VisionViewport("tiny-320x240", 320, 240),
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
        val installed = mutableStateOf(setOf(VisionTask.Expressions))
        val busy = mutableStateOf(false)
        val modelError = mutableStateOf<String?>(null)
        var localActions = 0
        val registry = fixtureRegistry { task -> task in installed.value }
        val scene = ImageComposeScene(viewport.width, viewport.height, Density(viewport.density, viewport.font)) {
            val tasks = VisionTask.entries.associateWith { task ->
                registry.settingsState(task, state.value).let { taskState ->
                    if (task != VisionTask.Objects) taskState
                    else taskState.copy(busy = busy.value, progress = 45, error = modelError.value)
                }
            }
            VisionSettingsContent(state.value, { state.value = it; saved = it }, { backed++ }, tasks,
                onModelAction = { task ->
                    if (task == VisionTask.Objects) modelActions++ else localActions++
                    installed.value = if (task in installed.value) installed.value - task else installed.value + task
                },
                onCancel = { cancelled++; busy.value = false }, onOpenCamera = { openedCamera++ })
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
                // Back remains fixed; changes persist directly from the control callbacks.
                check(nodes().none { it.config.getOrNull(SemanticsProperties.TestTag) == "vision.save" })
                for (tag in listOf("vision.back", "vision.tab.yolo", "vision.tab.expression")) {
                    if (tag.startsWith("vision.tab") && scrollContainer(tag) != null) continue
                    val bounds = tagged(tag).boundsInRoot
                    check(bounds.width >= 47 * viewport.density && bounds.height >= 47 * viewport.density) { "$tag too small: $bounds" }
                    check(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= viewport.width && bounds.bottom <= viewport.height) { "$tag outside viewport: $bounds" }
                }
                check(scrollContainer("vision.back") == null) { "back must stay fixed" }
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
            check(tagged("vision.yolo.modelStatus").config[SemanticsProperties.Text].any { it.text == "未下载" })
            check(nodes().any { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "COCO 80 类 · 约 82 MB" } == true }) {
                "Model size comes from the provider's model list"
            }
            click("vision.yolo.modelAction", scroll = true)
            check(VisionTask.Objects in installed.value && modelActions == 1)
            click("vision.yolo.modelAction", scroll = true)
            check(VisionTask.Objects !in installed.value && modelActions == 2)
            busy.value = true; frame()
            check(tagged("vision.camera").config.contains(SemanticsProperties.Disabled))
            check(nodes().none { it.config.getOrNull(SemanticsProperties.TestTag) == "vision.yolo.modelAction" }) { "Cancel replaces the model action while busy" }
            scrollTo("vision.yolo.cancel"); shot("downloading")
            click("vision.yolo.cancel"); check(cancelled == 1 && !busy.value)
            modelError.value = "下载失败，请重试"; frame()
            check(tagged("vision.yolo.modelError").config[SemanticsProperties.Text].any { it.text.contains("下载失败") })
            modelError.value = null; frame()
            click("vision.yolo.enabled", scroll = true)
            check(tagged("vision.tab.yolo").config[SemanticsProperties.StateDescription] == "需下载模型")
            check(saved?.yoloEnabled == true) { "Enabling YOLO is saved before its model is downloaded" }
            check(nodes().none { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "高级" || it.text.contains("自定义") } == true }) {
                "Custom model options are gone"
            }
            click("vision.yolo.modelAction", scroll = true)
            check(tagged("vision.tab.yolo").config[SemanticsProperties.StateDescription] == "已开启")
            scrollTo("vision.yolo.confidence")
            check(tagged("vision.yolo.confidence").config[SemanticsActions.SetProgress].action?.invoke(0.4f) == true)
            frame(); check(state.value.yoloConfidenceThreshold == 0.4f && saved?.yoloConfidenceThreshold == 0.4f)
            click("vision.yolo.confidenceReset", scroll = true); check(state.value.yoloConfidenceThreshold == 0.25f)
            shot("yolo-configured")
            click("vision.tab.expression"); shot("expression")
            click("vision.expression.enabled", scroll = true)
            click("vision.expression.modelAction", scroll = true); check(localActions == 1 && VisionTask.Expressions !in installed.value)
            click("vision.expression.modelAction", scroll = true); check(localActions == 2 && VisionTask.Expressions in installed.value)
            click("vision.tab.gesture"); shot("gesture")
            // Two packaged hand providers: the page offers a choice and saves the provider id.
            click("vision.provider.fixture.hands.npu", scroll = true)
            check(state.value.providerId(HandLandmarker) == "fixture.hands.npu")
            check(saved?.providerId(HandLandmarker) == "fixture.hands.npu")
            check(tagged("vision.gesture.modelStatus").config[SemanticsProperties.Text].any { it.text == "系统内置 · 无需下载" })
            shot("gesture-provider")
            click("vision.tab.expression")
            scrollTo("vision.expression.confidence")
            check(tagged("vision.expression.confidence").config[SemanticsActions.SetProgress].action?.invoke(0.8f) == true)
            frame(); check(state.value.expressionConfidenceThreshold == 0.8f)
            click("vision.tab.yolo")
            check(state.value.yoloEnabled && state.value.expressionEnabled)
            check(saved == state.value)
            click("vision.camera", scroll = true); check(openedCamera == 1)
            click("vision.back"); check(backed == 1)
            println("PASS ${viewport.name}: model actions, auto-save callbacks, no save button, open camera, back and scroll reachability")
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
    val unsupportedRegistry = fixtureRegistry(supported = false) { false }
    val unsupported = ImageComposeScene(800, 480, Density(1f)) {
        VisionSettingsContent(VisionPreferences(), {}, {},
            VisionTask.entries.associateWith { unsupportedRegistry.settingsState(it, VisionPreferences()) })
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
    verifyImmediatePersistence()
}

/** Exercises the real screen's persistence boundary without touching the user's preferences or model files. */
@OptIn(ExperimentalComposeUiApi::class)
private fun verifyImmediatePersistence() {
    var persisted = VisionPreferences()
    var writes = 0
    var opened: VisionPreferences? = null
    var backed = false
    val registry = fixtureRegistry { true }
    val store = object : ModelStore {
        override val supportsDownloads = false
        override fun installed(model: ModelSpec) = true
        override suspend fun install(models: List<ModelSpec>, onProgress: (Long, Long) -> Unit) = error("No fixture downloads")
        override suspend fun remove(models: List<ModelSpec>) = Unit
        override suspend fun verifiedPath(model: ModelSpec): String = error("No fixture inference")
    }
    fun scene() = ImageComposeScene(800, 480, Density(1f)) {
        VisionSettingsScreen(onBack = { backed = true }, onOpenCamera = { opened = persisted }, registry = registry,
            store = store, initialPreferences = persisted, persistPreferences = { persisted = it; writes++ })
    }
    fun node(scene: ImageComposeScene, tag: String): SemanticsNode {
        fun visit(current: SemanticsNode): List<SemanticsNode> = listOf(current) + current.children.flatMap { visit(it) }
        return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
            .single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
    }
    val first = scene()
    try {
        first.render().close()
        node(first, "vision.yolo.enabled").config[SemanticsActions.OnClick].action!!.invoke()
        check(writes == 1 && persisted.yoloEnabled) { "Toggle must persist during its callback" }
        first.render().close()
        node(first, "vision.yolo.confidence").config[SemanticsActions.SetProgress].action!!.invoke(.4f)
        check(writes == 2 && persisted.yoloConfidenceThreshold == .4f) { "Threshold must persist immediately" }
        node(first, "vision.tab.gesture").config[SemanticsActions.OnClick].action!!.invoke()
        first.render().close()
        node(first, "vision.provider.fixture.hands.npu").config[SemanticsActions.OnClick].action!!.invoke()
        check(writes == 3 && persisted.providerId(HandLandmarker) == "fixture.hands.npu")
        node(first, "vision.back").config[SemanticsActions.OnClick].action!!.invoke()
        check(backed && writes == 3)
    } finally { first.close() }
    val reopened = scene()
    try {
        reopened.render().close()
        check(node(reopened, "vision.tab.yolo").config[SemanticsProperties.StateDescription] == "已开启")
        check(node(reopened, "vision.yolo.confidence").config[SemanticsProperties.ProgressBarRangeInfo].current == .4f)
        node(reopened, "vision.camera").config[SemanticsActions.OnClick].action!!.invoke()
        check(opened == persisted && writes == 3) { "Camera must use the already persisted configuration" }
    } finally { reopened.close() }
    println("PASS actual vision route: immediate toggle/threshold/provider persistence, back/reentry and camera configuration")
}
