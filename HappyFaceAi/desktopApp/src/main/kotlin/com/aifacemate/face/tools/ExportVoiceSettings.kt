package com.aifacemate.face.tools

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.aifacemate.face.*
import java.io.File
import org.jetbrains.skia.EncodedImageFormat

private data class VoiceViewport(val name: String, val width: Int, val height: Int, val density: Float = 1f, val font: Float = 1f)

/** Render and exercise the actual UI with in-memory fixtures, without credentials or network. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/voice-settings").apply { mkdirs() }
    val viewports = listOf(
        VoiceViewport("small-480x320", 480, 320),
        VoiceViewport("small-640x360", 640, 360),
        VoiceViewport("medium-800x480", 800, 480),
        VoiceViewport("foldable-897x813", 897, 813),
        VoiceViewport("large-1440x900", 1440, 900),
        VoiceViewport("portrait-390x844", 390, 844),
        VoiceViewport("small-font130", 480, 320, font = 1.3f),
        VoiceViewport("small-density150", 480, 320, density = 1.5f),
        VoiceViewport("phone-2400x1080", 2400, 1080, density = 2.75f),
    )
    var shots = 0
    for (viewport in viewports) {
        val state = mutableStateOf(VoicePreferences())
        var saved: VoicePreferences? = null
        var backed = 0
        var modelActions = 0
        var piperActions = 0
        val scene = ImageComposeScene(viewport.width, viewport.height, Density(viewport.density, viewport.font)) {
            VoiceSettingsContent(state.value, { state.value = it }, { backed++ }, { saved = state.value },
                systemAsrAvailable = true, systemTtsAvailable = true, realtimeAvailable = true, senseVoiceSupported = true,
                piperSupported = true, onPiperAction = { piperActions++ }, onModelAction = { modelActions++ })
        }
        try {
            var time = 0L
            fun frame() { time += 100_000_000L; scene.render(time).close() }
            fun nodes(): List<SemanticsNode> {
                fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { visit(it) }
                return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
            }
            fun tagged(tag: String) = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
                ?: error("${viewport.name}: missing $tag")
            fun visibleBounds(tag: String): Rect = tagged(tag).boundsInRoot
            fun fixedActions() {
                for (tag in listOf("voice.back", "voice.save")) {
                    val bounds = visibleBounds(tag)
                    check(bounds.width >= 47 * viewport.density && bounds.height >= 47 * viewport.density) { "$tag too small: $bounds" }
                    check(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= viewport.width && bounds.bottom <= viewport.height) { "$tag outside viewport: $bounds" }
                }
            }
            fun scrollTo(tag: String) {
                val area = tagged("voice.configuration")
                val range = area.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return
                val scroll = area.config.getOrNull(SemanticsActions.ScrollBy)?.action ?: return
                fun advanceScroll(delta: Float) {
                    scroll(0f, delta)
                    // Accessibility scrolling is animated; settle it before issuing another action.
                    repeat(8) { frame() }
                }
                advanceScroll(-range.maxValue())
                repeat((range.maxValue() / (24 * viewport.density)).toInt() + 4) {
                    val bounds = tagged(tag).boundsInRoot
                    val viewportBounds = tagged("voice.configuration").boundsInRoot
                    if (bounds.height >= 47 * viewport.density && bounds.top >= viewportBounds.top && bounds.bottom <= viewportBounds.bottom) return
                    advanceScroll(24 * viewport.density)
                }
                scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.use { File(output, "failure-${viewport.name}.png").writeBytes(it.bytes) }
                error("${viewport.name}: $tag cannot be reached by scrolling; bounds=${tagged(tag).boundsInRoot}, area=${tagged("voice.configuration").boundsInRoot}, scroll=${range.value()}/${range.maxValue()}")
            }
            fun click(tag: String, scroll: Boolean = false) {
                if (scroll) scrollTo(tag)
                check(tagged(tag).config[SemanticsActions.OnClick].action?.invoke() == true) { "$tag click failed" }
                frame(); fixedActions()
            }
            fun shot(name: String) {
                scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.use {
                    File(output, "${viewport.name}-$name.png").writeBytes(it.bytes)
                }
                shots++
            }
            frame(); fixedActions(); shot("qwen")
            click("voice.provider.Doubao", scroll = true)
            check(state.value.provider == RealtimeProvider.Doubao)
            shot("doubao")
            scrollTo("voice.doubao.appId")
            check(tagged("voice.doubao.appId").config[SemanticsActions.SetText].action?.invoke(AnnotatedString("example-app")) == true)
            frame(); check(state.value.doubaoAppId == "example-app")
            click("voice.mode.Traditional")
            check(state.value.mode == VoiceMode.Traditional)
            shot("mimo")
            scrollTo("voice.mimo.voice")
            check(tagged("voice.mimo.voice").config[SemanticsActions.SetText].action?.invoke(AnnotatedString("ExampleVoice")) == true)
            frame(); check(state.value.mimoVoice == "ExampleVoice")
            click("voice.asr.System", scroll = true)
            check(state.value.traditionalAsr == TraditionalAsr.System)
            click("voice.tts.System", scroll = true)
            check(state.value.traditionalTts == TraditionalTts.System)
            click("voice.tts.Piper", scroll = true)
            check(state.value.traditionalTts == TraditionalTts.Piper)
            click("voice.piper.action", scroll = true); check(piperActions == 1)
            click("voice.asr.SenseVoice", scroll = true)
            check(state.value.traditionalAsr == TraditionalAsr.SenseVoice)
            check(state.value.traditionalTts == TraditionalTts.Piper)
            click("voice.tts.Mimo", scroll = true)
            check(state.value.traditionalTts == TraditionalTts.Mimo)
            shot("sensevoice")
            click("voice.model.action", scroll = true); check(modelActions == 1)
            click("voice.keepFaceVisible", scroll = true); check(!state.value.keepFaceVisible)
            click("voice.save"); check(saved == state.value)
            click("voice.asr.System", scroll = true); check(state.value.traditionalAsr == TraditionalAsr.System)
            click("voice.tts.System", scroll = true); check(state.value.traditionalTts == TraditionalTts.System)
            shot("system-services")
            click("voice.mode.Realtime"); check(state.value.doubaoAppId == "example-app")
            click("voice.back"); check(backed == 1)
            println("PASS ${viewport.name}: fixed actions, modes, providers, form edits, model action, display toggle, save/back")
        } finally { scene.close() }
    }
    // Unsupported providers remain discoverable but cannot be selected or downloaded.
    val unavailable = ImageComposeScene(800, 480, Density(1f)) {
        VoiceSettingsContent(VoicePreferences(mode = VoiceMode.Traditional), {}, {}, {},
            systemAsrAvailable = false, systemTtsAvailable = false, realtimeAvailable = false, senseVoiceSupported = false,
            piperSupported = false)
    }
    try {
        unavailable.render().encodeToData(EncodedImageFormat.PNG)!!.use { File(output, "unavailable-desktop.png").writeBytes(it.bytes) }
        fun collect(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { collect(it) }
        val nodes = unavailable.semanticsOwners.flatMap { collect(it.rootSemanticsNode) }
        for (tag in listOf("voice.mode.Realtime", "voice.asr.System", "voice.tts.System", "voice.tts.Piper", "voice.asr.SenseVoice")) {
            check(nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }.config.contains(SemanticsProperties.Disabled))
        }
        println("PASS unsupported-device choices remain visible and disabled")
    } finally { unavailable.close() }
    println("Exported $shots voice settings screenshots to ${output.absolutePath}")
}
