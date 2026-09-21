package com.aifacemate.face.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import com.aifacemate.face.feature.aibrain.AiBrainScreen
import com.aifacemate.face.feature.aibrain.AiBrainUiState
import com.aifacemate.face.feature.lifecenter.LifeColors
import com.aifacemate.face.ui.AppDialog
import com.aifacemate.face.ui.AppInputDialog
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private class Shot(
    val name: String,
    val width: Int,
    val height: Int,
    val content: @Composable () -> Unit,
)

/** 空实现的 AI 大脑页，用于走查排版。 */
private fun aiBrain(state: AiBrainUiState = AiBrainUiState()): @Composable () -> Unit = {
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
        onSaveSecret = {},
        onToastShown = {},
    )
}

/** 页面 + 弹窗叠在一起，看遮罩和卡片的实际对比度。 */
private fun overPage(dialog: @Composable () -> Unit): @Composable () -> Unit = {
    Box(Modifier.fillMaxSize()) {
        aiBrain().invoke()
        dialog()
    }
}

/**
 * 渲染 AI 大脑页与各类弹窗，用于设计走查与视觉回归。
 * 用法：./gradlew :desktopApp:exportAiBrain
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/ai-brain").apply { mkdirs() }
    val shots = listOf(
        Shot("wide", 1440, 900, aiBrain()),
        Shot("device-landscape", 817, 375, aiBrain()),
        // 模拟器那档小型横屏：480x800@240dpi = 533x320dp
        Shot("small-landscape", 533, 320, aiBrain()),
        // 「自定义」提供商没有内置模型，用来走查空态文案。
        Shot("device-landscape-custom-provider", 817, 375, aiBrain(AiBrainUiState().selectProvider("custom"))),
        Shot("dialog-base-url", 817, 375, overPage {
            AppInputDialog(
                title = "API Base URL",
                value = "https://api.deepseek.com/v1",
                onValueChange = {},
                placeholder = "https://api.deepseek.com/v1",
                keyboardType = KeyboardType.Uri,
                onDismiss = {},
                onConfirm = {},
            )
        }),
        Shot("dialog-api-key", 817, 375, overPage {
            AppInputDialog(
                title = "API Key",
                value = "sk-e2a1234567890abcdef3228",
                onValueChange = {},
                placeholder = "sk-xxxxxxxxxxxxxxxx",
                secret = true,
                onDismiss = {},
                onConfirm = {},
            )
        }),
        Shot("dialog-add-model", 817, 375, overPage {
            AppInputDialog(
                title = "添加自定义模型",
                value = "",
                onValueChange = {},
                placeholder = "deepseek-chat",
                onDismiss = {},
                onConfirm = {},
            )
        }),
        // 横屏 + 键盘弹起时可用高度只剩 100dp 出头：用矮画布逼近那个状态，看输入条是不是正好贴在键盘上方。
        Shot("dialog-input-bar-over-keyboard", 817, 130, overPage {
            AppInputDialog(
                title = "API Key",
                value = "sk-e2a1234567890abcdef3228",
                onValueChange = {},
                placeholder = "sk-xxxxxxxxxxxxxxxx",
                secret = true,
                onDismiss = {},
                onConfirm = {},
            )
        }),
        Shot("dialog-notice", 817, 375, overPage {
            AppDialog(
                title = "还没有填 API Key",
                symbol = "✦",
                description = "获取模型列表需要先连上供应商的接口，填一个 API Key 就能拉取了。",
                confirmText = "去填写",
                dismissText = "稍后再说",
                onConfirm = {},
                onDismiss = {},
            )
        }),
    )

    shots.forEach { shot ->
        val scene = ImageComposeScene(shot.width, shot.height, Density(1f)) {
            MaterialTheme(colorScheme = darkColorScheme(primary = LifeColors.Amber)) {
                shot.content()
            }
        }
        try {
            scene.render().encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                File(output, "${shot.name}.png").writeBytes(data.bytes)
            }
        } finally {
            scene.close()
        }
    }
    println("导出 ${shots.size} 张：${output.absolutePath}")
}
