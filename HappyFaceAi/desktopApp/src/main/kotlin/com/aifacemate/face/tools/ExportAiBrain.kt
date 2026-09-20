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
    listOf(Triple("wide", 1440, 900), Triple("device-landscape", 817, 375)).forEach { (name, width, height) ->
        val scene = ImageComposeScene(width, height, Density(1f)) {
            MaterialTheme(colorScheme = darkColorScheme(primary = LifeColors.Amber)) {
                AiBrainScreen(
                    state = AiBrainUiState(),
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
