package com.aifacemate.face.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.render.FaceSurface
import com.aifacemate.face.render.PANEL_ASPECT_RATIO

private val StageTop = Color(0xFF241B16)
private val StageBottom = Color(0xFF09090C)

private val Accent = Color(0xFFF1C779)


/**
 * 表情预览默认全屏显示，右上角菜单展开全屏半透明功能区。
 * 功能区可滚动，选择表情后自动收起。
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
    onBack: (() -> Unit)? = null,
    onMenuOpenChange: (Boolean) -> Unit = {},
    closeMenuSignal: Int = 0,
) {
    var menuOpen by remember { mutableStateOf(false) }
    fun setMenuOpen(open: Boolean) { menuOpen = open; onMenuOpenChange(open) }
    androidx.compose.runtime.LaunchedEffect(closeMenuSignal) { if (closeMenuSignal > 0) setMenuOpen(false) }
    // 外部托管表情时用外部的，否则自己管，方便单独预览这个页面
    var internalExpression by remember { mutableStateOf(FaceExpression.IDLE) }
    val currentExpression = expression ?: internalExpression
    val selectExpression: (FaceExpression) -> Unit = { selected ->
        if (onExpressionChange != null) onExpressionChange(selected) else internalExpression = selected
        setMenuOpen(false)
    }

    var animate by remember { mutableStateOf(true) }
    var surface by remember { mutableStateOf(FaceSurface.PRODUCT_MOCKUP) }
    var options by remember { mutableStateOf(false) }
    // 扩散光晕开关：只关掉最外层那圈大面积辉光，近边发光保留
    var internalDiffuseGlow by remember { mutableStateOf(DEFAULT_DIFFUSE_GLOW) }
    val currentDiffuseGlow = diffuseGlow ?: internalDiffuseGlow
    val selectDiffuseGlow: (Float) -> Unit = { value ->
        if (onDiffuseGlowChange != null) onDiffuseGlowChange(value) else internalDiffuseGlow = value
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(StageTop, StageBottom)))) {
        val compactControls = maxHeight < 360.dp || maxWidth < 600.dp
        PreviewPane(currentExpression, animate && !menuOpen, surface, currentDiffuseGlow,
            Modifier.fillMaxSize().testTag("face.preview.pane"))
        FullscreenPreviewMenu(menuOpen, { setMenuOpen(true) }, { setMenuOpen(false) },
            prefix = "face", description = "表情菜单",
            onBack = onBack) {
            ControlPane(currentExpression, selectExpression, animate, { animate = it }, surface, { surface = it },
                currentDiffuseGlow, selectDiffuseGlow, options, { options = !options }, compactControls, Modifier.fillMaxWidth())
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

@Composable
private fun ControlPane(
    expression: FaceExpression, onExpressionChange: (FaceExpression) -> Unit,
    animate: Boolean, onAnimateChange: (Boolean) -> Unit,
    surface: FaceSurface, onSurfaceChange: (FaceSurface) -> Unit,
    diffuseGlow: Float, onDiffuseGlowChange: (Float) -> Unit,
    options: Boolean,
    onOptionsToggle: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
    val gap = if (compact) 6.dp else 8.dp
    val columns = ((maxWidth.value + gap.value) / ((if (compact) 68f else 88f) + gap.value)).toInt().coerceIn(2, 10)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        PreviewSettingsSection("显示设置", options, onOptionsToggle, "face") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            CompactChip("生命感", animate, { onAnimateChange(!animate) }, Modifier.weight(1f).testTag("face.life"))
            CompactChip("扩散光晕", diffuseGlow > .01f,
                { onDiffuseGlowChange(if (diffuseGlow > .01f) 0f else DEFAULT_DIFFUSE_GLOW) }, Modifier.weight(1f).testTag("face.glow"))
        }
        Text("渲染表面", color = Color.White.copy(alpha = .65f), fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            CompactChip("整机概念图", surface == FaceSurface.PRODUCT_MOCKUP,
                { onSurfaceChange(FaceSurface.PRODUCT_MOCKUP) }, Modifier.weight(1f).testTag("face.surface.PRODUCT_MOCKUP"))
            CompactChip("实机屏幕", surface == FaceSurface.DEVICE_SCREEN,
                { onSurfaceChange(FaceSurface.DEVICE_SCREEN) }, Modifier.weight(1f).testTag("face.surface.DEVICE_SCREEN"))
        }
        HorizontalDivider(color = Color.White.copy(alpha = .08f))
        }
        Text("${expression.cnName} · ${expression.ordinal + 1} / 100",
            color = Color.White.copy(alpha = .7f), fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).testTag("face.current"),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        expressionGroups().forEach { group ->
            Text(group.title, color = Color.White.copy(alpha = .45f), fontSize = 11.sp)
            group.expressions.chunked(columns).forEach { row ->
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { item ->
                    CompactChip(item.cnName, item == expression, { onExpressionChange(item) },
                        Modifier.weight(1f).testTag("face.expression.${item.name}"), compact = compact)
                }
                repeat(columns - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
              }
            }
        }
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
    compact: Boolean = false,
) {
    val chosen = selected
    Box(
        modifier = modifier
            .heightIn(min = if (compact) 40.dp else 48.dp, max = if (compact) 40.dp else 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Accent else Color(0xFF253038))
            .clickable(onClick = onClick)
            .semantics { this.selected = chosen }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.78f),
            fontSize = 12.sp,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
