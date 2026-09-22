package com.aifacemate.face.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.aifacemate.face.feature.aibrain.*

/** Offline UI smoke test against the actual Compose semantics tree at both small-screen sizes. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // 480x320 与 640x360 走 compact 分支；800x480 走完整图文导航；897x813 是折叠屏内屏全屏。
    for ((width, height) in listOf(480 to 320, 640 to 360, 800 to 480, 897 to 813)) {
        val state = mutableStateOf(AiBrainUiState())
        var tested = 0
        var saved = 0
        var cleared = 0
        var backed = 0
        val scene = ImageComposeScene(width, height, Density(1f)) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AiBrainScreen(
                    state = state.value, onBack = { backed++ },
                    onProviderSelected = { state.value = state.value.selectProvider(it) },
                    onModelSelected = { state.value = state.value.selectModel(it) },
                    onThinkingModeSelected = { state.value = state.value.copy(thinkingMode = it) },
                    onCapabilityToggled = { state.value = state.value.copy(capabilities = state.value.capabilities + (it to !(state.value.capabilities[it] ?: false))) },
                    onBaseUrlChanged = {}, onApiKeyChanged = {}, onFetchModels = {}, onCustomModelAdded = {},
                    onDismissApiKeyPrompt = {}, onSaveSecret = { saved++ }, onToastShown = {},
                    onTestConnection = { tested++ }, onClearSecret = { cleared++ },
                )
            }
        }
        try {
            var time = 0L
            fun frame() { time += 100_000_000L; scene.render(time).close() }
            fun nodes(): List<SemanticsNode> {
                fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { visit(it) }
                return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
            }
            fun label(node: SemanticsNode): List<String> = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            fun click(label: String) {
                val node = nodes().firstOrNull { label in label(it) && it.config.getOrNull(SemanticsActions.OnClick) != null }
                    ?: error("$width x $height: missing clickable $label")
                check(node.boundsInRoot.width >= 43f && node.boundsInRoot.height >= 43f) { "Undersized target $label: ${node.boundsInRoot}" }
                check(node.config[SemanticsActions.OnClick].action?.invoke() == true)
                frame()
            }
            fun contains(text: String) = nodes().any { text in label(it) }
            frame()
            check(contains("当前 AI 大脑"))
            click("OpenAI")
            check(state.value.config.providerId == "openai")
            click("密钥配置")
            check(contains("API Base URL") && contains("API Key") && contains("Model"))
            click("测试连接"); check(tested == 1 && saved == 0)
            click("保存配置"); check(saved == 1)
            click("清空"); check(cleared == 0 && contains("清空当前服务商配置？"))
            // Dialog confirmation is the only operation allowed to clear persisted credentials.
            val confirm = nodes().first { "清空配置" in label(it) && it.config.getOrNull(SemanticsActions.OnClick) != null }
            confirm.config[SemanticsActions.OnClick].action!!.invoke(); frame()
            check(cleared == 1)
            click("能力接入")
            val toggle = nodes().first { "联网搜索" in label(it) && it.config.getOrNull(SemanticsActions.OnClick) != null }
            toggle.config[SemanticsActions.OnClick].action!!.invoke(); frame()
            check(state.value.capabilities[AiCapability.WebSearch] == false)
            click("思考模式")
            click("深度")
            check(state.value.thinkingMode == ThinkingMode.Deep)
            click("AI 大脑")
            check(contains("当前 AI 大脑"))
            click("返回生命中心"); check(backed == 1)
            println("PASS $width x $height: navigation, provider, test/save, clear confirmation, toggle, thinking, exit")
        } finally { scene.close() }
    }
}
