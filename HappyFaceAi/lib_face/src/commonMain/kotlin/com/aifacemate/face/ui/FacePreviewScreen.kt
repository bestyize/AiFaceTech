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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * 表情调试台：上面是实时猫脸，下面是表情切换。
 *
 * 这是给产品、设计、开发内部走查用的界面，也方便直接在真机上验证 25 个表情位。
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
    var showAll by remember { mutableStateOf(false) }
    var surface by remember { mutableStateOf(FaceSurface.PRODUCT_MOCKUP) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(StageTop, StageBottom))),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // 横屏手机上控件区容易把脸挤没，这里保底留出脸部高度
                .heightIn(min = 200.dp)
                .drawBehind {
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
                    expression = currentExpression,
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
                            expression = currentExpression,
                            animate = animate,
                            modifier = Modifier.fillMaxSize(),
                            surface = FaceSurface.DEVICE_SCREEN,
                        )
                    }
                }
            }
        }

        ControlPanel(
            expression = currentExpression,
            onExpressionChange = selectExpression,
            animate = animate,
            onAnimateChange = { animate = it },
            showAll = showAll,
            onShowAllChange = { showAll = it },
            surface = surface,
            onSurfaceChange = { surface = it },
            onExitToDisplay = onExitToDisplay,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlPanel(
    expression: FaceExpression,
    onExpressionChange: (FaceExpression) -> Unit,
    animate: Boolean,
    onAnimateChange: (Boolean) -> Unit,
    showAll: Boolean,
    onShowAllChange: (Boolean) -> Unit,
    surface: FaceSurface,
    onSurfaceChange: (FaceSurface) -> Unit,
    onExitToDisplay: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 控件区自己滚动，永远不挤压脸部
            .heightIn(max = 320.dp)
            .background(PanelSurface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
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
                        if (surface == FaceSurface.DEVICE_SCREEN) append("  ·  实机屏幕 16:9")
                    },
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                )
            }
            Text(
                text = "生命感动画",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
            Switch(
                checked = animate,
                onCheckedChange = onAnimateChange,
                modifier = Modifier.padding(start = 8.dp).width(46.dp),
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                FaceExpression.alphaOne.forEach { item ->
                    ExpressionChip(
                        expression = item,
                        selected = item == expression,
                        label = "α1 · ${item.cnName}",
                        onClick = { onExpressionChange(item) },
                    )
                }
            }

            if (showAll) {
                // 按三张设计图的划分分组：基础 06-25、扩展 I 26-50、扩展 II 51-75
                listOf(
                    "基础 06-25" to FaceExpression.basic.filterNot { it in FaceExpression.alphaOne },
                    "扩展 I 26-50" to FaceExpression.extensionOne,
                    "扩展 II 51-75" to FaceExpression.extensionTwo,
                ).forEach { (title, group) ->
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        group.forEach { item ->
                            ExpressionChip(
                                expression = item,
                                selected = item == expression,
                                label = item.cnName,
                                onClick = { onExpressionChange(item) },
                            )
                        }
                    }
                }
            }
        }

        TextButton(onClick = { onShowAllChange(!showAll) }) {
            Text(
                text = if (showAll) "收起其余表情" else "展开全部 75 个表情位",
                color = Accent,
                fontSize = 13.sp,
            )
        }
        if (onExitToDisplay != null) {
            TextButton(onClick = onExitToDisplay) {
                Text(
                    text = "回到实机全屏",
                    color = Accent,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun SurfaceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
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
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (selected) Color.Black else Color.White.copy(alpha = 0.8f),
            )
        },
    )
}

@Preview
@Composable
private fun FacePreviewScreenPreview() {
    MaterialTheme {
        FacePreviewScreen()
    }
}
