package com.aifacemate.face.feature.lifecenter

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
import androidx.compose.ui.graphics.Brush
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
import com.aifacemate.face.ui.FaceStage

@Composable
fun LifeCenterRoute(state: LifeCenterUiState, expression: FaceExpression, diffuseGlow: Float, onNavigate: (LifeRoute) -> Unit) {
    LifeCenterScreen(state, expression, diffuseGlow, onNavigate)
}

@Composable
fun LifeCenterScreen(state: LifeCenterUiState, expression: FaceExpression, diffuseGlow: Float, onNavigate: (LifeRoute) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(LifeColors.Background).safeDrawingPadding()) {
        val wide = maxWidth >= 840.dp || (maxWidth >= 720.dp && maxWidth > maxHeight * 1.4f)
        val compact = wide && maxHeight < 540.dp
        val landscape = maxWidth > maxHeight
        val portraitHeight = if (wide) (maxHeight * .42f).coerceIn(140.dp, 360.dp) else 200.dp
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 24.dp, vertical = if (compact) 4.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)) {
            LifeTopBar("生命中心", "返回全屏", compact = compact, backOnRight = landscape) { onNavigate(LifeRoute.Living) }
            if (wide) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp)) {
                    if (compact) {
                        Column(Modifier.weight(.36f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CompactProfileCard(state.profile, expression, diffuseGlow, Modifier.weight(1f))
                            CompanionActions(onNavigate, compact = true)
                        }
                    } else {
                        Column(Modifier.weight(.43f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CompanionProfileCard(state.profile, expression, diffuseGlow, portraitHeight)
                            CompanionActions(onNavigate)
                        }
                    }
                    Column(Modifier.weight(if (compact) .64f else .57f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp)) {
                        FeatureGrid(state.features, onNavigate, compact)
                        RecentChangesTimeline(state.recentChanges)
                    }
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CompanionProfileCard(state.profile, expression, diffuseGlow, portraitHeight)
                    FeatureGrid(state.features, onNavigate)
                    RecentChangesTimeline(state.recentChanges)
                    CompanionActions(onNavigate)
                }
            }
        }
    }
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
        color = LifeColors.Card,
        border = BorderStroke(1.dp, LifeColors.Border),
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
                    LifeColors.Amber,
                    style = Stroke(size.minDimension * .2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            Text(
                "返回",
                color = LifeColors.Amber,
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
    Column(modifier.background(LifeColors.Card, RoundedCornerShape(24.dp)).border(1.dp, LifeColors.Border, RoundedCornerShape(24.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun CompanionProfileCard(profile: CompanionProfile, expression: FaceExpression, diffuseGlow: Float, portraitHeight: androidx.compose.ui.unit.Dp) {
    LifeCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(portraitHeight).background(Brush.radialGradient(listOf(LifeColors.Amber.copy(alpha = .13f), Color.Transparent)))) {
            FaceStage(expression, Modifier.fillMaxSize(), diffuseGlow = diffuseGlow)
        }
        Text(profile.name, Modifier.align(Alignment.CenterHorizontally), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("从此刻开始，慢慢熟悉彼此", Modifier.align(Alignment.CenterHorizontally), color = LifeColors.Secondary)
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("● ${profile.status}", color = LifeColors.Amber)
            Text(profile.personality, color = LifeColors.Secondary)
        }
        Text(profile.message, Modifier.align(Alignment.CenterHorizontally), color = LifeColors.Amber, fontSize = 14.sp)
    }
}

@Composable
private fun CompactProfileCard(profile: CompanionProfile, expression: FaceExpression, diffuseGlow: Float, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().background(LifeColors.Card, RoundedCornerShape(20.dp))
            .border(1.dp, LifeColors.Border, RoundedCornerShape(20.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        FaceStage(expression, Modifier.fillMaxWidth().weight(1f), diffuseGlow = diffuseGlow)
        Text(profile.name, color = Color.White, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
        Text("从此刻开始，慢慢熟悉彼此", color = LifeColors.Secondary, fontSize = 11.sp, lineHeight = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("● ${profile.status}", color = LifeColors.Amber, fontSize = 11.sp, lineHeight = 15.sp)
            Text(profile.personality, color = LifeColors.Secondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Text(profile.message, color = LifeColors.Amber, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun CompanionActions(onNavigate: (LifeRoute) -> Unit, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { onNavigate(LifeRoute.Living) }, modifier = Modifier.weight(1f).heightIn(min = if (compact) 48.dp else 56.dp), colors = ButtonDefaults.buttonColors(containerColor = LifeColors.Amber, contentColor = LifeColors.Background)) { Text("进入陪伴") }
        OutlinedButton(onClick = { onNavigate(LifeRoute.DeviceSystem) }, modifier = Modifier.weight(1f).heightIn(min = if (compact) 48.dp else 56.dp)) { Text("更多设置", color = Color.White) }
    }
}

@Composable
private fun FeatureGrid(features: List<LifeCenterFeature>, onNavigate: (LifeRoute) -> Unit, compact: Boolean = false) {
    BoxWithConstraints {
        val columns = if (maxWidth >= 380.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
            features.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                    row.forEach { feature -> FeatureCard(feature, Modifier.weight(1f), compact) { onNavigate(feature.route) } }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(feature: LifeCenterFeature, modifier: Modifier, compact: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = if (compact) 56.dp else 104.dp), shape = RoundedCornerShape(20.dp), color = LifeColors.Card, border = androidx.compose.foundation.BorderStroke(1.dp, LifeColors.Border)) {
        Row(Modifier.padding(if (compact) 8.dp else 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            Text(feature.symbol, color = feature.accent, fontSize = if (compact) 20.sp else 28.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
                Text(feature.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 14.sp else 18.sp, lineHeight = if (compact) 18.sp else 24.sp)
                Text(feature.subtitle, color = LifeColors.Secondary, fontSize = if (compact) 11.sp else 12.sp, lineHeight = if (compact) 14.sp else 18.sp)
            }
            Text("›", color = LifeColors.Secondary, fontSize = 24.sp)
        }
    }
}

@Composable
private fun RecentChangesTimeline(changes: List<RecentChange>) {
    LifeCard(Modifier.fillMaxWidth()) {
        Text("最近变化", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        if (changes.isEmpty()) Text("新的故事，从这次相遇开始。调整表情后，你会在这里看到变化。", color = LifeColors.Secondary)
        changes.take(5).forEach { change ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("●", color = LifeColors.Amber)
                Column {
                    Text(change.title, color = Color.White)
                    Text(change.description, color = LifeColors.Secondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun FeaturePlaceholder(feature: LifeCenterFeature, onBack: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(LifeColors.Background).safeDrawingPadding()) {
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
                Text(feature.subtitle, color = LifeColors.Secondary)
                Text("当前版本尚未接入此能力。你可以先到「表情与外观」认识小星的不同表情。", color = LifeColors.Secondary)
                Button(onClick = onBack) { Text("回到生命中心") }
            }
        }
    }
}
