package com.aifacemate.face.tools

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.aifacemate.face.*
import com.aifacemate.face.chat.ChatMessage
import java.io.File
import org.jetbrains.skia.EncodedImageFormat

private data class ChatViewport(val name: String, val width: Int, val height: Int, val density: Float = 1f, val font: Float = 1f, val keyboard: Int = 0)

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/conversation").apply { mkdirs() }
    val viewports = listOf(
        ChatViewport("small-480x320", 480, 320),
        ChatViewport("small-640x360", 640, 360),
        ChatViewport("medium-800x480", 800, 480),
        ChatViewport("large-1440x900", 1440, 900),
        ChatViewport("portrait-390x844", 390, 844),
        ChatViewport("small-font130", 480, 320, font = 1.3f),
        ChatViewport("small-density150", 480, 320, density = 1.5f),
        ChatViewport("phone-2400x1080", 2400, 1080, density = 2.75f),
        ChatViewport("keyboard-small", 480, 320, keyboard = 180),
        ChatViewport("keyboard-density150", 480, 320, density = 1.5f, keyboard = 100),
        ChatViewport("keyboard-phone", 2400, 1080, density = 2.75f, keyboard = 180),
    )
    var screenshots = 0
    for (viewport in viewports) {
        val state = mutableStateOf(ConversationScreenState(VoiceMode.Traditional))
        val keyboard = mutableStateOf(viewport.keyboard)
        var sent = 0
        var voiced = 0
        var closed = 0
        var collapsed = 0
        val scene = ImageComposeScene(viewport.width, viewport.height, Density(viewport.density, viewport.font)) {
            ConversationScreenContent(state.value, { state.value = state.value.copy(draft = it) }, { sent++ },
                { voiced++; state.value = state.value.copy(voiceActive = !state.value.voiceActive) },
                { closed++ }, modifier = Modifier.padding(bottom = keyboard.value.dp), onCollapse = { collapsed++ })
        }
        try {
            var time = 0L
            fun frames(count: Int = 3) { repeat(count) { time += 100_000_000L; scene.render(time).close() } }
            fun nodes(): List<SemanticsNode> {
                fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { visit(it) }
                return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
            }
            fun tagged(tag: String) = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
                ?: error("${viewport.name}: missing $tag")
            fun controlsVisible() {
                for (tag in listOf("chat.close", "chat.collapse", "chat.send", "chat.voice")) {
                    val bounds = tagged(tag).boundsInRoot
                    check(bounds.width >= 47 * viewport.density && bounds.height >= 47 * viewport.density) { "$tag too small: $bounds" }
                    check(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= viewport.width && bounds.bottom <= viewport.height - keyboard.value * viewport.density + 1f) { "$tag outside viewport: $bounds" }
                }
                check(tagged("chat.draft").boundsInRoot.width >= 60 * viewport.density) { "Draft input too narrow" }
            }
            fun click(tag: String) { check(tagged(tag).config[SemanticsActions.OnClick].action?.invoke() == true); frames(); controlsVisible() }
            fun shot(name: String) {
                scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.use { File(output, "${viewport.name}-$name.png").writeBytes(it.bytes) }
                screenshots++
            }
            frames(); controlsVisible(); shot("empty")
            check(tagged("chat.send").config.contains(SemanticsProperties.Disabled))
            check(tagged("chat.draft").config[SemanticsActions.SetText].action?.invoke(AnnotatedString("今天想和你聊聊")) == true)
            frames(); click("chat.send"); check(sent == 1)
            click("chat.voice"); check(voiced == 1 && state.value.voiceActive)
            shot("listening")
            click("chat.voice"); check(voiced == 2 && !state.value.voiceActive)
            state.value = state.value.copy(mode = VoiceMode.Realtime)
            frames(); click("chat.voice"); check(voiced == 3 && state.value.voiceActive)
            check("停止实时对话" in tagged("chat.voice").config[SemanticsProperties.ContentDescription])
            click("chat.voice"); check(voiced == 4 && !state.value.voiceActive)
            state.value = state.value.copy(mode = VoiceMode.Traditional)
            state.value = state.value.copy(messages = listOf(
                ChatMessage("user", "今天的阳光很好，我们出去走走吧。"),
                ChatMessage("assistant", "可以呀。你想去附近的公园，还是先在楼下转一圈？"),
                ChatMessage("user", "先去公园看看。"),
                ChatMessage("assistant", "好的。你可以继续告诉我你的想法，也可以用下面的输入框打字。"),
            ), draft = "", status = "可以继续对话")
            frames(); shot("messages"); controlsVisible()
            state.value = state.value.copy(busy = true, voiceEnabled = false, status = "AI 大脑正在思考")
            frames(); check(tagged("chat.voice").config.contains(SemanticsProperties.Disabled)); shot("processing")
            // Dismiss the simulated keyboard before reading history on extremely short screens.
            keyboard.value = 0
            frames(); controlsVisible()
            state.value = state.value.copy(busy = false, voiceEnabled = true,
                messages = List(24) { ChatMessage(if (it % 2 == 0) "user" else "assistant", "这是一条保留在记录中的消息 $it") })
            frames()
            check(tagged("chat.history").config[SemanticsActions.ScrollToIndex].action?.invoke(0) == true)
            frames(8)
            check(tagged("chat.message.0").boundsInRoot.height > 0) { "Old messages were lost" }
            state.value = state.value.copy(messages = state.value.messages + ChatMessage("assistant", "新回复"), status = "回复已完成")
            frames()
            check(tagged("chat.message.0").boundsInRoot.height > 0) { "Incoming reply interrupted reading older history" }
            state.value = state.value.copy(status = "本地识别失败时，完整提示会保留在记录下方。你可以滚动查看原因，并继续输入文字或返回声音设置。")
            frames(); controlsVisible()
            click("chat.collapse"); check(collapsed == 1)
            click("chat.close"); check(closed == 1)
            println("PASS ${viewport.name}: fixed controls, input/send, voice start/stop, processing, full history, collapse/close")
        } finally { scene.close() }
    }
    println("Exported $screenshots full-screen conversation screenshots to ${output.absolutePath}")
}
