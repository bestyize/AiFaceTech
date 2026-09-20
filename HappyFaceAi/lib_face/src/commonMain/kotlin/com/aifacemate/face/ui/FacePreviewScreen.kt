package com.aifacemate.face.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.render.FaceSurface
import com.aifacemate.face.render.PANEL_ASPECT_RATIO

private val StageTop = Color(0xFF241B16)
private val StageBottom = Color(0xFF09090C)
private val PanelSurface = Color(0xFF121218)
private val Accent = Color(0xFFFFC46B)

/** 左预览 + 右操作 的分栏阈值；窄屏自动退化成上下布局。 */
private val SIDE_BY_SIDE_THRESHOLD = 720.dp

/**
 * 表情调试台：左边是预览区，右边是操作区。
 *
 * 操作区刻意做得紧凑：标题左侧，「生命感」与「回到全屏」收纳到右上角，
 * 渲染表面一行小胶囊，剩下的空间全部留给 100 个表情位（全部展开、可滚动）。
 *
 * 表情与「扩散光晕」开关都可以由外部托管：
 * 传进来就用外部的，不传就自己管，这样全屏页与调试台能共用同一份状态。
 *
 * 耳朵灯不做开关：走查时始终亮着，作为表情的背景光。
 */
@Composable
fun FacePreviewScreen(
    modifier: Modifier = Modifier,
    expression: FaceExpression? = null,
    onExpressionChange: ((FaceExpression) -> Unit)? = null,
    diffuseGlow: Float? = null,
    onDiffuseGlowChange: ((Float) -> Unit)? = null,
    onExitToDisplay: (() -> Unit)? = null,
) {
    // 外部托管表情时用外部的，否则自己管，方便单独预览这个页面
    var internalExpression by remember { mutableStateOf(FaceExpression.IDLE) }
    val currentExpression = expression ?: internalExpression
    val selectExpression: (FaceExpression) -> Unit = { selected ->
        if (onExpressionChange != null) onExpressionChange(selected) else internalExpression = selected
    }

    var animate by remember { mutableStateOf(true) }
    var surface by remember { mutableStateOf(FaceSurface.PRODUCT_MOCKUP) }
    // 扩散光晕开关：只关掉最外层那圈大面积辉光，近边发光保留
    var internalDiffuseGlow by remember { mutableStateOf(DEFAULT_DIFFUSE_GLOW) }
    val currentDiffuseGlow = diffuseGlow ?: internalDiffuseGlow
    val selectDiffuseGlow: (Float) -> Unit = { value ->
        if (onDiffuseGlowChange != null) onDiffuseGlowChange(value) else internalDiffuseGlow = value
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(StageTop, StageBottom))),
    ) {
        val sideBySide = maxWidth >= SIDE_BY_SIDE_THRESHOLD
        val controlWidth = minOf(420.dp, maxWidth * 0.42f)

        if (sideBySide) {
            Row(modifier = Modifier.fillMaxSize()) {
                PreviewPane(
                    expression = currentExpression,
                    animate = animate,
                    surface = surface,
                    diffuseGlow = currentDiffuseGlow,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                ControlPane(
                    expression = currentExpression,
                    onExpressionChange = selectExpression,
                    animate = animate,
                    onAnimateChange = { animate = it },
                    surface = surface,
                    onSurfaceChange = { surface = it },
                    diffuseGlow = currentDiffuseGlow,
                    onDiffuseGlowChange = selectDiffuseGlow,
                    onExitToDisplay = onExitToDisplay,
                    modifier = Modifier.width(controlWidth).fillMaxHeight(),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                PreviewPane(
                    expression = currentExpression,
                    animate = animate,
                    surface = surface,
                    diffuseGlow = currentDiffuseGlow,
                    modifier = Modifier.fillMaxWidth().weight(0.55f),
                )
                ControlPane(
                    expression = currentExpression,
                    onExpressionChange = selectExpression,
                    animate = animate,
                    onAnimateChange = { animate = it },
                    surface = surface,
                    onSurfaceChange = { surface = it },
                    diffuseGlow = currentDiffuseGlow,
                    onDiffuseGlowChange = selectDiffuseGlow,
                    onExitToDisplay = onExitToDisplay,
                    // 窄屏上下布局：预览占 55%、操作区占 45%，列表在操作区内滚动
                    modifier = Modifier.fillMaxWidth().weight(0.45f),
                )
            }
        }
    }
}

@Composable
private fun PreviewPane(
    expression: FaceExpression,
    animate: Boolean,
    surface: FaceSurface,
    diffuseGlow: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.drawBehind {
            // 耳朵灯是表情的背景光，走查时始终亮着
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Accent.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.42f),
                    radius = size.minDimension * 0.75f,
                ),
                radius = size.minDimension * 0.75f,
                center = Offset(size.width / 2f, size.height * 0.42f),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        when (surface) {
            FaceSurface.PRODUCT_MOCKUP -> FaceStage(
                expression = expression,
                animate = animate,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                surface = FaceSurface.PRODUCT_MOCKUP,
                diffuseGlow = diffuseGlow,
            )

            // 实机模式：按 4.0 英寸 16:9 的比例预览屏幕里真正会显示的内容
            FaceSurface.DEVICE_SCREEN -> BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val screenWidth = minOf(maxWidth, maxHeight * PANEL_ASPECT_RATIO)
                Box(
                    modifier = Modifier
                        .width(screenWidth)
                        .aspectRatio(PANEL_ASPECT_RATIO)
                        .background(Color.Black)
                        .border(1.dp, Color.White.copy(alpha = 0.14f)),
                ) {
                    FaceStage(
                        expression = expression,
                        animate = animate,
                        modifier = Modifier.fillMaxSize(),
                        surface = FaceSurface.DEVICE_SCREEN,
                        diffuseGlow = diffuseGlow,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlPane(
    expression: FaceExpression,
    onExpressionChange: (FaceExpression) -> Unit,
    animate: Boolean,
    onAnimateChange: (Boolean) -> Unit,
    surface: FaceSurface,
    onSurfaceChange: (FaceSurface) -> Unit,
    diffuseGlow: Float,
    onDiffuseGlowChange: (Float) -> Unit,
    onExitToDisplay: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(PanelSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // 紧凑标题行：左侧标题，右上角收纳「生命感」与「回到全屏」
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "脸部表情系统 · Alpha 1",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(expression.cnName)
                        append(" · ")
                        append(expression.enName)
                        append("（")
                        append(expression.ordinal + 1)
                        append(" / ")
                        append(FaceExpression.entries.size)
                        append("）")
                    },
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactChip(
                    label = "生命感",
                    selected = animate,
                    onClick = { onAnimateChange(!animate) },
                )
                if (onExitToDisplay != null) {
                    CompactActionButton(
                        label = "回到全屏",
                        onClick = onExitToDisplay,
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(
                text = "渲染表面",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
            )
            CompactChip(
                label = "整机概念图",
                selected = surface == FaceSurface.PRODUCT_MOCKUP,
                onClick = { onSurfaceChange(FaceSurface.PRODUCT_MOCKUP) },
            )
            CompactChip(
                label = "实机屏幕",
                selected = surface == FaceSurface.DEVICE_SCREEN,
                onClick = { onSurfaceChange(FaceSurface.DEVICE_SCREEN) },
            )
            CompactChip(
                label = "扩散光晕",
                selected = diffuseGlow > 0.01f,
                onClick = {
                    onDiffuseGlowChange(if (diffuseGlow > 0.01f) 0f else DEFAULT_DIFFUSE_GLOW)
                },
            )
        }

        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.padding(vertical = 10.dp),
        )

        // 剩下的空间全部给 100 个表情位，操作区自己滚动
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            expressionGroups().forEach { group ->
                Text(
                    text = "${group.title} · ${group.expressions.size}",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    group.expressions.forEach { item ->
                        CompactChip(
                            label = item.cnName,
                            selected = item == expression,
                            onClick = { onExpressionChange(item) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 紧凑胶囊：操作区里的开关与表情位都用它。
 * 比 Material 的 FilterChip 矮一截，同样的高度能多放一行表情。
 */
@Composable
private fun CompactChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Accent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.78f),
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** 右上角的主操作按钮：回到实机全屏。 */
@Composable
private fun CompactActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private data class ExpressionGroup(val title: String, val expressions: List<FaceExpression>)

/** 按四张设计图分组，每组 25 个表情位，便于走查时定位。 */
private fun expressionGroups(): List<ExpressionGroup> = listOf(
    ExpressionGroup("基础 01-25", FaceExpression.basic),
    ExpressionGroup("扩展 I 26-50", FaceExpression.extensionOne),
    ExpressionGroup("扩展 II 51-75", FaceExpression.extensionTwo),
    ExpressionGroup("扩展 III 76-100", FaceExpression.extensionThree),
)

@Preview
@Composable
private fun FacePreviewScreenPreview() {
    MaterialTheme {
        FacePreviewScreen()
    }
}
