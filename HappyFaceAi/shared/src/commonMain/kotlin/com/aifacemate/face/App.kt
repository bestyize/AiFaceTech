package com.aifacemate.face

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.aifacemate.face.feature.aibrain.AiBrainRoute
import com.aifacemate.face.feature.lifecenter.*
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.ui.*
import kotlinx.coroutines.delay

@Composable
fun App(onBackHandler: @Composable (Boolean, () -> Unit) -> Unit = { _, _ -> }) {
    MaterialTheme(colorScheme = darkColorScheme(primary = LifeColors.Amber, background = LifeColors.Background, surface = LifeColors.Card)) {
        var routeName by rememberSaveable { mutableStateOf(LifeRoute.Living.name) }
        val route = LifeRoute.valueOf(routeName)
        var expressionName by rememberSaveable { mutableStateOf(FaceExpression.IDLE.name) }
        val expression = FaceExpression.valueOf(expressionName)
        var diffuseGlow by rememberSaveable { mutableStateOf(DEFAULT_DIFFUSE_GLOW) }
        var hud by remember { mutableStateOf(false) }
        var petting by remember { mutableStateOf(false) }
        var conversationNotice by remember { mutableStateOf(false) }
        val changes = remember { mutableStateListOf<RecentChange>() }
        val haptic = LocalHapticFeedback.current
        val navigate: (LifeRoute) -> Unit = { routeName = it.name; hud = false; conversationNotice = false; petting = false }
        onBackHandler(route != LifeRoute.Living || hud) {
            if (hud) hud = false else navigate(if (route == LifeRoute.LifeCenter) LifeRoute.Living else LifeRoute.LifeCenter)
        }
        LaunchedEffect(hud) { if (hud) { delay(3500); hud = false } }
        LaunchedEffect(petting) { if (petting) { delay(2400); petting = false } }
        when (route) {
            LifeRoute.Living -> Box(Modifier.fillMaxSize()) {
                FaceDisplay(
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { hud = !hud },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                navigate(LifeRoute.LifeCenter)
                            },
                        )
                    },
                    expression = if (petting) FaceExpression.HAPPY else expression,
                    diffuseGlow = diffuseGlow,
                )
                if (hud) Row(
                    Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp)
                        .background(Color(0xEE191C24), RoundedCornerShape(28.dp)).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { conversationNotice = true; hud = false }) { Text("对话") }
                    TextButton(onClick = { petting = true; hud = false }) { Text("抚摸") }
                    TextButton(onClick = { navigate(LifeRoute.LifeCenter) }) { Text("生命中心") }
                }
                if (conversationNotice) AppDialog(
                    title = "还在学习说话",
                    symbol = "♫",
                    description = "语音与 AI 对话还没有接入。现在可以先轻点「抚摸」，看看小星的回应。",
                    onDismiss = { conversationNotice = false },
                )
            }
            LifeRoute.LifeCenter -> LifeCenterRoute(LifeCenterUiState(recentChanges = changes.toList()), expression, diffuseGlow, navigate)
            LifeRoute.Brain -> AiBrainRoute(onBack = { navigate(LifeRoute.LifeCenter) })
            LifeRoute.Appearance -> Column(Modifier.fillMaxSize().background(LifeColors.Background).safeDrawingPadding()) {
                FacePreviewScreen(
                    modifier = Modifier.weight(1f), expression = expression,
                    onExpressionChange = {
                        if (it != expression) {
                            expressionName = it.name
                            changes.add(0, RecentChange("换上了新的表情", "${it.cnName} · 本次陪伴"))
                            if (changes.size > 5) changes.removeAt(changes.lastIndex)
                        }
                    },
                    diffuseGlow = diffuseGlow,
                    onDiffuseGlowChange = { diffuseGlow = it },
                    onBack = { navigate(LifeRoute.LifeCenter) },
                )
            }
            else -> FeaturePlaceholder(defaultFeatures.first { it.route == route }) { navigate(LifeRoute.LifeCenter) }
        }
    }
}
