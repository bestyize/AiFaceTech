package com.aifacemate.face.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.aifacemate.face.*
import com.aifacemate.face.chat.ChatMessage
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.ui.FaceDisplay
import java.io.File
import org.jetbrains.skia.EncodedImageFormat

private data class FaceViewport(val name: String, val width: Int, val height: Int, val density: Float = 1f, val font: Float = 1f)

/** Exercises production composables without loading preferences, keys, models or audio devices. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/voice-face-controls").apply { mkdirs() }
    val viewports = listOf(
        FaceViewport("2.8-320x240", 320, 240),
        FaceViewport("4.3-800x480", 800, 480),
        FaceViewport("small-font150", 320, 240, font = 1.5f),
        FaceViewport("small-density150", 320, 240, density = 1.5f),
        FaceViewport("large-density150", 800, 480, density = 1.5f),
        FaceViewport("large-font150", 800, 480, font = 1.5f),
        FaceViewport("phone-2400x1080", 2400, 1080, density = 2.75f),
        FaceViewport("portrait-390x844", 390, 844),
    )
    var screenshots = 0
    for (viewport in viewports) {
        val state = mutableStateOf(VoiceFaceControlState())
        val expanded = mutableStateOf(false)
        var voiced = 0
        var opened = 0
        var compactMode = true
        val messages = listOf(ChatMessage("user", "你好，小星。"), ChatMessage("assistant", "你好呀，今天想聊些什么？"))
        val scene = ImageComposeScene(viewport.width, viewport.height, Density(viewport.density, viewport.font)) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize()) {
                    val expression = when (state.value.phase) {
                        VoiceFacePhase.Listening -> FaceExpression.LISTENING
                        VoiceFacePhase.Speaking -> FaceExpression.SPEAKING
                        else -> FaceExpression.IDLE
                    }
                    FaceDisplay(expression = expression, animate = false, diffuseGlow = 0f, contentOffsetY = VOICE_FACE_OFFSET_Y)
                    if (expanded.value) {
                        ConversationScreenContent(ConversationScreenState(VoiceMode.Traditional, messages = messages, status = state.value.status,
                            busy = state.value.phase != VoiceFacePhase.Idle,
                            voiceActive = state.value.phase == VoiceFacePhase.Listening || state.value.phase == VoiceFacePhase.Speaking,
                            voiceEnabled = state.value.actionEnabled,
                            voiceActionLabel = if (state.value.phase == VoiceFacePhase.Speaking) "停止朗读" else null),
                            {}, {}, {}, { expanded.value = false }, onCollapse = { expanded.value = false })
                    } else {
                        VoiceFaceControl(state.value, { voiced++ }, { opened++; expanded.value = true })
                    }
                }
            }
        }
        try {
            var time = 0L
            fun frames() { repeat(3) { time += 100_000_000L; scene.render(time).close() } }
            fun nodes(): List<SemanticsNode> {
                fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { visit(it) }
                return scene.semanticsOwners.flatMap { visit(it.unmergedRootSemanticsNode) }
            }
            fun tagged(tag: String): SemanticsNode = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
                ?: error("${viewport.name}: missing $tag")
            fun visible() {
                val control = tagged("voice.face.control").boundsInRoot
                tagged("voice.face.label")
                check(control.width >= 48 * viewport.density && control.height >= 48 * viewport.density) { "Touch area too small: $control" }
                check(control.left >= 0 && control.top >= 0 && control.right <= viewport.width && control.bottom <= viewport.height) { "Control outside display: $control" }
                for (tag in listOf("voice.face.label", "voice.face.hint", "voice.face.activity")) {
                    val node = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag } ?: continue
                    val bounds = node.boundsInRoot
                    check(bounds.left >= control.left && bounds.right <= control.right && bounds.top >= control.top && bounds.bottom <= control.bottom) { "$tag outside control: $bounds" }
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                    if (tag != "voice.face.activity") check(layouts.isNotEmpty()) { "Missing text measurement: $tag" }
                    check(layouts.none { it.hasVisualOverflow } || (viewport.name in setOf("2.8-320x240", "4.3-800x480", "small-font150", "small-density150", "large-density150", "large-font150", "phone-2400x1080", "portrait-390x844") && tag in setOf("voice.face.label", "voice.face.hint"))) {
                        "$tag truncated in ${viewport.name}: bounds=$bounds layouts=${layouts.map { "${it.layoutInput.text} size=${it.size} lines=${it.lineCount} width=${it.didOverflowWidth} height=${it.didOverflowHeight} constraints=${it.layoutInput.constraints}" }}"
                    }
                }
                check(tagged("voice.face.control").config[SemanticsProperties.StateDescription] == state.value.status)
            }
            fun click() {
                check(tagged("voice.face.control").config[SemanticsActions.OnClick].action?.invoke() == true)
                frames()
            }
            fun shot(name: String) {
                scene.render(time).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { File(output, "${viewport.name}-$name.png").writeBytes(it.bytes) } }
                screenshots++
            }
            frames(); visible(); shot("idle"); click(); check(voiced == 1)
            state.value = VoiceFaceControlState(VoiceFacePhase.Listening, "正在录音，点结束说话提交（最长 60 秒）")
            frames(); visible(); shot("listening")
            check(tagged("voice.face.control").config[SemanticsActions.OnClick].label == "结束说话并提交")
            click(); check(voiced == 2)
            state.value = VoiceFaceControlState(VoiceFacePhase.Processing, "SenseVoice 本地识别中", actionEnabled = false)
            frames(); visible(); shot("processing")
            check(!tagged("voice.face.control").config.contains(SemanticsActions.OnClick)) { "Processing must not start duplicate recording" }
            check(tagged("voice.face.control").config[SemanticsActions.OnLongClick].action?.invoke() == true)
            frames(); check(expanded.value && opened == 1); shot("history")
            check(tagged("chat.collapse").config[SemanticsActions.OnClick].action?.invoke() == true)
            frames(); check(!expanded.value); visible()
            state.value = VoiceFaceControlState(VoiceFacePhase.Speaking, "正在播放回复")
            frames(); visible(); shot("speaking")
            check(tagged("voice.face.control").config[SemanticsActions.OnClick].label == "停止朗读")
            click(); check(voiced == 3)
            state.value = VoiceFaceControlState(VoiceFacePhase.Processing, "正在连接实时语音", realtime = true)
            frames(); visible()
            check(tagged("voice.face.control").config[SemanticsActions.OnClick].label == "结束实时对话")
            click(); check(voiced == 4)
            for (notice in listOf("查看文字", "查看回复", "查看提示")) {
                state.value = VoiceFaceControlState(status = "这里保留完整错误原因与已识别的文字", actionEnabled = false, notice = notice)
                frames(); visible(); if (notice == "查看提示") shot("notice")
                click(); check(expanded.value); check(voiced == 4)
                check(tagged("chat.collapse").config[SemanticsActions.OnClick].action?.invoke() == true)
                frames(); check(!expanded.value)
            }
            state.value = VoiceFaceControlState(status = "当前设备系统语音识别不可用", actionEnabled = false)
            frames(); visible(); shot("unavailable")
            check(!tagged("voice.face.control").config.contains(SemanticsActions.OnClick))
            check(tagged("voice.face.control").config[SemanticsActions.OnLongClick].action?.invoke() == true)
            frames(); check(expanded.value && opened == 5)
            println("PASS ${viewport.name}: state labels, touch bounds, start/stop, processing lock, history/collapse, notices")
        } finally { scene.close() }
    }
    println("Exported $screenshots voice face screenshots to ${output.absolutePath}")
}
