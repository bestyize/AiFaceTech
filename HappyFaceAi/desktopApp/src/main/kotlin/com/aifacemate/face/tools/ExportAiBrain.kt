package com.aifacemate.face.tools
import com.aifacemate.face.feature.aibrain.domain.*
import com.aifacemate.face.feature.aibrain.presentation.*

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.aifacemate.face.feature.aibrain.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private data class Viewport(val name: String, val width: Int, val height: Int, val density: Float = 1f, val fontScale: Float = 1f)

/** Render the real four page composables, not stand-in dialogs. No API calls or saved secrets are used. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/ai-brain", "v3").apply { mkdirs() }
    val viewports = listOf(
        Viewport("480x320", 480, 320),
        // 640x360 / 640x480 是折叠屏与平板在自由窗口下的常见配置，正好压在 compact 阈值两侧。
        Viewport("640x360", 640, 360),
        Viewport("640x480", 640, 480),
        // 折叠屏内屏全屏横屏（例如 2248x2480@442dpi）接近正方形，长宽比与普通手机完全不同。
        Viewport("foldable-897x813", 897, 813),
        Viewport("800x480", 800, 480),
        Viewport("phone-2400x1080", 2400, 1080, 2.75f),
        Viewport("wide-1440x900", 1440, 900),
        Viewport("480x320-font130", 480, 320, fontScale = 1.3f),
        Viewport("640x360-font130", 640, 360, fontScale = 1.3f),
        Viewport("480x320-density150", 480, 320, density = 1.5f),
    )
    var count = 0
    viewports.forEach { shot ->
        BrainNavSection.entries.forEach { section ->
            val scene = ImageComposeScene(shot.width, shot.height, Density(shot.density, shot.fontScale)) {
                MaterialTheme(colorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFF1C779))) {
                    var state by remember { mutableStateOf(AiBrainUiState().selectProvider("openai")) }
                    AiBrainScreen(
                        state = state, initialSection = section, onBack = {},
                        onProviderSelected = { state = state.selectProvider(it) },
                        onModelSelected = { state = state.selectModel(it) },
                        onThinkingModeSelected = { state = state.copy(thinkingMode = it) },
                        onCapabilityToggled = { state = state.copy(capabilities = state.capabilities + (it to !(state.capabilities[it] ?: false))) },
                        onBaseUrlChanged = { state = state.copy(config = state.config.copy(baseUrl = it)) },
                        onApiKeyChanged = { state = state.copy(config = state.config.copy(apiKey = it)) },
                        onFetchModels = {}, onCustomModelAdded = {}, onDismissApiKeyPrompt = {}, onSaveSecret = {},
                        onToastShown = {}, onTestConnection = {}, onClearSecret = {},
                    )
                }
            }
            try {
                scene.render().encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                    File(output, "${shot.name}-${section.name}.png").writeBytes(data.bytes)
                }
                count++
            } finally { scene.close() }
        }
    }
    println("AI 大脑 v3：已导出 $count 张真实页面，目录 ${output.absolutePath}")
}
