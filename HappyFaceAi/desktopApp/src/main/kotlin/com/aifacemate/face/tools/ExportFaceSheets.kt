package com.aifacemate.face.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.model.ExpressionLibrary
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.render.CatFace
import com.aifacemate.face.render.FaceSurface
import com.aifacemate.face.ui.FaceDisplay
import com.aifacemate.face.ui.FacePreviewScreen
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private const val TILE_WIDTH = 300
private const val TILE_HEIGHT = 250
private const val COLUMNS = 5

/**
 * 离屏渲染工具：把 25 个表情位导出成 PNG。
 *
 * 这样不用启动窗口、不用截屏就能做视觉走查，后续也能接进 CI 当视觉回归基线。
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val outputDir = File(args.firstOrNull() ?: "build/face-sheets").apply { mkdirs() }

    FaceExpression.entries.forEach { expression ->
        renderToFile(
            file = File(outputDir, "${expression.id}.png"),
            width = 720,
            height = 460,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(SheetBackground).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CatFace(
                    params = ExpressionLibrary.paramsFor(expression),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // 与四张设计图一一对应：基础 01-25、扩展 I 26-50、扩展 II 51-75、扩展 III 76-100
    overviewGroups.forEach { (name, expressions) ->
        renderOverviewSheet(File(outputDir, "_overview-$name.png"), expressions)
        // 关掉扩散光晕的同一批：只留近边发光，黑屏不再被大面积辉光铺灰
        renderOverviewSheet(
            File(outputDir, "_overview-$name-nodiffuse.png"),
            expressions,
            diffuseGlow = 0f,
        )
    }

    // 整机界面截图：顺带验证调试台 UI 在运行时能正常组合
    renderToFile(
        file = File(outputDir, "_app-preview.png"),
        width = 1180,
        height = 820,
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFFFFC46B),
                surface = Color(0xFF121218),
                background = Color(0xFF09090C),
            ),
        ) {
            FacePreviewScreen(
                modifier = Modifier.fillMaxSize(),
                onExitToDisplay = {},
            )
        }
    }

    // 窄屏下的上下布局（窗口宽度不足以左右分栏时）
    renderToFile(
        file = File(outputDir, "_app-preview-narrow.png"),
        width = 620,
        height = 900,
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFFFFC46B),
                surface = Color(0xFF121218),
                background = Color(0xFF09090C),
            ),
        ) {
            FacePreviewScreen(
                modifier = Modifier.fillMaxSize(),
                onExitToDisplay = {},
            )
        }
    }

    // 实机全屏（16:9）效果图：走的就是 App 里「回到全屏」那条路径（FaceDisplay）
    renderDeviceSheet(File(outputDir, "_device-sheet.png"), diffuseGlow = 1f)
    renderDeviceSheet(File(outputDir, "_device-sheet-nodiffuse.png"), diffuseGlow = 0f)

    // 扩散光晕开 / 关的并排对比：每两格一组，左有扩散光晕、右无
    renderComparisonSheet(File(outputDir, "_compare-diffuse.png"))

    // 扩散光晕强度的四档对比：1.0（现状）/ 0.6 / 0.3 / 0
    renderDiffuseLevelsSheet(File(outputDir, "_compare-diffuse-levels.png"))

    // 单张表情 + 四张总览 + 四张无光晕总览 + 两张对比图 + 两种预览台布局 + 实机屏幕
    println("已导出 ${FaceExpression.entries.size + 13} 张图到 ${outputDir.absolutePath}")
}

/** 四张设计图对应的分组，导出总览时按这个顺序走。 */
private val overviewGroups: List<Pair<String, List<FaceExpression>>> = listOf(
    "01-25" to FaceExpression.basic,
    "26-50" to FaceExpression.extensionOne,
    "51-75" to FaceExpression.extensionTwo,
    "76-100" to FaceExpression.extensionThree,
)

/** 挑几个有代表性的表情，把「有扩散光晕 / 无扩散光晕」并排放在一起看差异。 */
private val DIFFUSE_COMPARISON_SAMPLES = listOf(
    FaceExpression.IDLE,
    FaceExpression.HAPPY,
    FaceExpression.SLEEPING,
    FaceExpression.OFFLINE,
    FaceExpression.GOOD_NIGHT,
    FaceExpression.CELEBRATING,
    FaceExpression.CURIOUS,
    FaceExpression.MISSING_YOU,
    FaceExpression.FLUSTERED,
    FaceExpression.NEW_DAY,
)

/** 扩散光晕强度的四个档位，用来挑一个“还有发光感、但黑屏够黑”的中间值。 */
private val DIFFUSE_LEVELS = listOf(1f, 0.6f, 0.3f, 0f)

private val DIFFUSE_LEVEL_SAMPLES = listOf(
    FaceExpression.IDLE,
    FaceExpression.HAPPY,
    FaceExpression.MISSING_YOU,
)

@OptIn(ExperimentalComposeUiApi::class)
private fun renderDiffuseLevelsSheet(file: File) {
    renderToFile(
        file = file,
        width = TILE_WIDTH * DIFFUSE_LEVELS.size,
        height = TILE_HEIGHT * DIFFUSE_LEVEL_SAMPLES.size,
    ) {
        Column(Modifier.fillMaxSize().background(SheetBackground)) {
            DIFFUSE_LEVEL_SAMPLES.forEach { expression ->
                Row {
                    DIFFUSE_LEVELS.forEach { level ->
                        FaceTile(expression, showLabel = true, diffuseGlow = level)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun renderComparisonSheet(file: File) {
    val perRow = 2 // 每行两组，每组两张（有扩散光晕 + 无扩散光晕）
    val rows = (DIFFUSE_COMPARISON_SAMPLES.size + perRow - 1) / perRow
    renderToFile(
        file = file,
        width = TILE_WIDTH * 2 * perRow,
        height = TILE_HEIGHT * rows,
    ) {
        Column(Modifier.fillMaxSize().background(SheetBackground)) {
            DIFFUSE_COMPARISON_SAMPLES.chunked(perRow).forEach { rowExpressions ->
                Row {
                    rowExpressions.forEach { expression ->
                        FaceTile(expression, showLabel = true, diffuseGlow = 1f)
                        FaceTile(expression, showLabel = true, diffuseGlow = 0f)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun renderOverviewSheet(
    file: File,
    expressions: List<FaceExpression>,
    diffuseGlow: Float = 1f,
) {
    val rows = (expressions.size + COLUMNS - 1) / COLUMNS
    renderToFile(
        file = file,
        width = TILE_WIDTH * COLUMNS,
        height = TILE_HEIGHT * rows,
    ) {
        Column(Modifier.fillMaxSize().background(SheetBackground)) {
            expressions.chunked(COLUMNS).forEach { rowExpressions ->
                Row {
                    rowExpressions.forEach { expression ->
                        FaceTile(expression, showLabel = true, diffuseGlow = diffuseGlow)
                    }
                }
            }
        }
    }
}

private const val DEVICE_TILE_WIDTH = 480
private const val DEVICE_TILE_HEIGHT = 270

@OptIn(ExperimentalComposeUiApi::class)
private fun renderDeviceSheet(file: File, diffuseGlow: Float) {
    val expressions = FaceExpression.alphaOne
    renderToFile(
        file = file,
        width = DEVICE_TILE_WIDTH,
        height = DEVICE_TILE_HEIGHT * expressions.size,
    ) {
        Column(Modifier.fillMaxSize().background(SheetBackground)) {
            expressions.forEach { expression ->
                Box(
                    modifier = Modifier
                        .size(DEVICE_TILE_WIDTH.dp, DEVICE_TILE_HEIGHT.dp)
                        .background(Color.Black),
                ) {
                    // 用实机全屏组件本身，保证导出图和「回到全屏」看到的一致
                    FaceDisplay(
                        modifier = Modifier.fillMaxSize(),
                        expression = expression,
                        animate = false,
                        diffuseGlow = diffuseGlow,
                    )
                }
            }
        }
    }
}

private val SheetBackground = Color(0xFF15151A)

@Composable
private fun FaceTile(expression: FaceExpression, showLabel: Boolean, diffuseGlow: Float = 1f) {
    Column(
        modifier = Modifier
            .size(TILE_WIDTH.dp, TILE_HEIGHT.dp)
            .background(SheetBackground)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CatFace(
                params = ExpressionLibrary.paramsFor(expression),
                modifier = Modifier.fillMaxSize(),
                diffuseGlow = diffuseGlow,
            )
        }
        if (showLabel) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = expression.cnName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = expression.enName,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun renderToFile(file: File, width: Int, height: Int, content: @Composable () -> Unit) {
    val scene = ImageComposeScene(
        width = width,
        height = height,
        density = Density(1f),
        content = content,
    )
    try {
        val image = scene.render()
        val data = image.encodeToData(EncodedImageFormat.PNG)
            ?: error("PNG 编码失败: ${file.name}")
        file.writeBytes(data.bytes)
    } finally {
        scene.close()
    }
}
