package com.aifacemate.face.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
 * 右侧操作区顶部是「回到实机全屏」，下面是渲染表面与 75 个表情位（全部展开、可滚动）。
 * 这是给产品、设计、开发内部走查用的界面，也方便直接在真机上验证表情位。
 */
@Composable
fun FacePreviewScreen(
    modifier: Modifier = Modifier,
    expression: FaceExpression? = null,
    onExpressionChange: ((FaceExpression) -> Unit)? = null,
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
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                ControlPane(
                    expression = currentExpression,
                    onExpressionChange = selectExpression,
                    animate = animate,
                    onAnimateChange = { animate = it },
                    surface = surface,
                    onSurfaceChange = { surface = it },
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
                    modifier = Modifier.fillMaxWidth().weight(0.55f),
                )
                ControlPane(
                    expression = currentExpression,
                    onExpressionChange = selectExpression,
                    animate = animate,
                    onAnimateChange = { animate = it },
                    surface = surface,
                    onSurfaceChange = { surface = it },
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.drawBehind {
            // 背后的暖色环境光，让发光屏幕有“落在桌面上的光”
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
    onExitToDisplay: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(PanelSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "脸部表情系统 · Alpha 1",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = buildString {
                append("当前：")
                append(expression.cnName)
                append(" · ")
                append(expression.enName)
                append("（")
                append(expression.ordinal + 1)
                append(" / 75）")
            },
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )

        // 顶部主操作：回到实机全屏
        if (onExitToDisplay != null) {
            Button(
                onClick = onExitToDisplay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color.Black,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(text = "回到实机全屏", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Text(
                text = "生命感动画",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = animate,
                onCheckedChange = onAnimateChange,
                modifier = Modifier.width(46.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(
                text = "渲染表面",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
            SurfaceChip(
                label = "整机概念图",
                selected = surface == FaceSurface.PRODUCT_MOCKUP,
                onClick = { onSurfaceChange(FaceSurface.PRODUCT_MOCKUP) },
            )
            SurfaceChip(
                label = "实机屏幕",
                selected = surface == FaceSurface.DEVICE_SCREEN,
                onClick = { onSurfaceChange(FaceSurface.DEVICE_SCREEN) },
            )
        }

        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.padding(vertical = 12.dp),
        )

        // 75 个表情位全部展开，操作区自己滚动
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    group.expressions.forEach { item ->
                        ExpressionChip(
                            expression = item,
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

private data class ExpressionGroup(val title: String, val expressions: List<FaceExpression>)

/** 按三张设计图 + Alpha 1 精修分组，便于走查时定位。 */
private fun expressionGroups(): List<ExpressionGroup> = listOf(
    ExpressionGroup("Alpha 1 精修", FaceExpression.alphaOne),
    ExpressionGroup(
        "基础 06-25",
        FaceExpression.basic.filterNot { it in FaceExpression.alphaOne },
    ),
    ExpressionGroup("扩展 I 26-50", FaceExpression.extensionOne),
    ExpressionGroup("扩展 II 51-75", FaceExpression.extensionTwo),
)

@Composable
private fun SurfaceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        colors = chipColors(),
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (selected) Color.Black else Color.White.copy(alpha = 0.8f),
            )
        },
    )
}

@Composable
private fun ExpressionChip(
    expression: FaceExpression,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        colors = chipColors(),
        label = {
            Text(
                text = expression.cnName,
                fontSize = 12.sp,
                color = if (selected) Color.Black else Color.White.copy(alpha = 0.8f),
            )
        },
    )
}

/** 选中态用品牌暖色，不用 Material 默认的紫色容器色。 */
@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Accent,
    selectedLabelColor = Color.Black,
)

@Preview
@Composable
private fun FacePreviewScreenPreview() {
    MaterialTheme {
        FacePreviewScreen()
    }
}
