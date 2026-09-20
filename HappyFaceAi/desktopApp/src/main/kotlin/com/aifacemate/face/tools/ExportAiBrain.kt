package com.aifacemate.face.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.aifacemate.face.feature.aibrain.AiBrainScreen
import com.aifacemate.face.feature.aibrain.AiBrainUiState
import com.aifacemate.face.feature.lifecenter.LifeColors
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** Render the actual AI brain page at desktop and device-landscape sizes for visual QA. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/ai-brain").apply { mkdirs() }
    // 第三张用「自定义」提供商：它没有内置模型，用来走查空态文案。
    val cases = listOf(
        Triple("wide", 1440, 900) to AiBrainUiState(),
        Triple("device-landscape", 817, 375) to AiBrainUiState(),
        Triple("device-landscape-custom-provider", 817, 375) to AiBrainUiState().selectProvider("custom"),
    )
    cases.forEach { (size, state) ->
        val (name, width, height) = size
        val scene = ImageComposeScene(width, height, Density(1f)) {
            MaterialTheme(colorScheme = darkColorScheme(primary = LifeColors.Amber)) {
                AiBrainScreen(
                    state = state,
                    onBack = {},
                    onProviderSelected = {},
                    onModelSelected = {},
                    onThinkingModeSelected = {},
                    onCapabilityToggled = {},
                    onBaseUrlChanged = {},
                    onApiKeyChanged = {},
                    onFetchModels = {},
                    onCustomModelAdded = {},
                    onDismissApiKeyPrompt = {},
                )
            }
        }
        try {
            scene.render().encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                File(output, "$name.png").writeBytes(data.bytes)
            }
        } finally {
            scene.close()
        }
    }
}
