package com.aifacemate.face.feature.lifecenter

import com.aifacemate.face.theme.AppColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.model.FaceExpression

@Composable
fun LifeCenterRoute(state: LifeCenterUiState, expression: FaceExpression, diffuseGlow: Float, onNavigate: (LifeRoute) -> Unit) {
    LifeCenterScreen(state, expression, diffuseGlow, onNavigate)
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun LifeCenterScreen(state: LifeCenterUiState, expression: FaceExpression, diffuseGlow: Float, onNavigate: (LifeRoute) -> Unit) {
    LifeCenterGrid(state.features, onNavigate)
}

/**
 * 页面顶栏：竖屏时返回按钮在左、标题紧随其后，对齐设计稿左上角的 ‹；
 * 横屏时返回按钮收到右上角（[backOnRight]），跟调试台右上角的操作区一致，拇指更容易够到。
 * 按钮是箭头加「返回」两个字，[backLabel] 给无障碍朗读更完整的去向。
 */
@Composable
fun LifeTopBar(title: String, backLabel: String, compact: Boolean = false, backOnRight: Boolean = false, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(if (compact) 36.dp else 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
    ) {
        if (!backOnRight) BackButton(backLabel, compact, onBack)
        Text(
            title,
            Modifier.weight(1f),
            color = Color.White,
            fontSize = if (compact) 17.sp else 20.sp,
            lineHeight = if (compact) 21.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        if (backOnRight) BackButton(backLabel, compact, onBack)
    }
}

/** 返回按钮：箭头矢量绘制（不依赖字体里的 ‹ 字形），后面跟「返回」两个字。 */
@Composable
private fun BackButton(label: String, compact: Boolean, onBack: () -> Unit) {
    val height = if (compact) 32.dp else 36.dp
    Surface(
        onClick = onBack,
        modifier = Modifier.height(height).semantics { contentDescription = label },
        shape = RoundedCornerShape(height / 2),
        color = AppColors.Card,
        border = BorderStroke(1.dp, AppColors.Border),
    ) {
        Row(
            Modifier.padding(start = if (compact) 9.dp else 11.dp, end = if (compact) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
        ) {
            Canvas(Modifier.size(height * .34f)) {
                val chevron = Path().apply {
                    moveTo(size.width * .74f, 0f)
                    lineTo(size.width * .2f, size.height * .5f)
                    lineTo(size.width * .74f, size.height)
                }
                drawPath(
                    chevron,
                    AppColors.Amber,
                    style = Stroke(size.minDimension * .2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            Text(
                "返回",
                color = AppColors.Amber,
                fontSize = if (compact) 13.sp else 14.sp,
                lineHeight = if (compact) 17.sp else 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LifeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(AppColors.Card, RoundedCornerShape(24.dp)).border(1.dp, AppColors.Border, RoundedCornerShape(24.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun FeaturePlaceholder(feature: LifeCenterFeature, onBack: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.Background).safeDrawingPadding()) {
        val compact = maxHeight < 540.dp
        val landscape = maxWidth > maxHeight
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 24.dp, vertical = if (compact) 4.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
        ) {
            LifeTopBar(feature.title, "返回生命中心", compact = compact, backOnRight = landscape, onBack = onBack)
            LifeCard(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(feature.symbol, color = feature.accent, fontSize = if (compact) 32.sp else 42.sp)
                Text("这份能力正在准备中", color = Color.White, fontSize = if (compact) 20.sp else 24.sp)
                Text(feature.subtitle, color = AppColors.Secondary)
                Text("当前版本尚未接入此能力。你可以先到「表情与外观」认识小星的不同表情。", color = AppColors.Secondary)
                Button(onClick = onBack) { Text("回到生命中心") }
            }
        }
    }
}
