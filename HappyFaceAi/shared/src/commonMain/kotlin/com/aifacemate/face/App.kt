package com.aifacemate.face
import com.aifacemate.face.feature.aibrain.domain.*
import com.aifacemate.face.feature.aibrain.presentation.*

import com.aifacemate.face.theme.AppColors

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
import com.aifacemate.face.feature.aibrain.presentation.AiBrainRoute
import com.aifacemate.face.feature.lifecenter.*
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.ui.*
import kotlinx.coroutines.delay

@Composable
fun App(onBackHandler: @Composable (Boolean, () -> Unit) -> Unit = { _, _ -> }) {
    MaterialTheme(colorScheme = darkColorScheme(primary = AppColors.Amber, background = AppColors.Background, surface = AppColors.Card)) {
        var routeName by rememberSaveable { mutableStateOf(LifeRoute.Living.name) }
        val route = LifeRoute.valueOf(routeName)
        var expressionName by rememberSaveable { mutableStateOf(FaceExpression.IDLE.name) }
        val expression = FaceExpression.valueOf(expressionName)
        var diffuseGlow by rememberSaveable { mutableStateOf(DEFAULT_DIFFUSE_GLOW) }
        var hud by remember { mutableStateOf(false) }
        var voiceOverlay by remember { mutableStateOf(false) }
        var voiceVisualState by remember { mutableStateOf(VoiceVisualState.Idle) }
        var petting by remember { mutableStateOf(false) }
        val changes = remember { mutableStateListOf<RecentChange>() }
        val haptic = LocalHapticFeedback.current
        val navigate: (LifeRoute) -> Unit = { routeName = it.name; hud = false; petting = false; voiceOverlay = false }
        onBackHandler(route != LifeRoute.Living || hud || voiceOverlay) {
            if (voiceOverlay) voiceOverlay = false
            else if (hud) hud = false else navigate(if (route == LifeRoute.LifeCenter) LifeRoute.Living else LifeRoute.LifeCenter)
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
                    expression = when {
                        petting -> FaceExpression.HAPPY
                        voiceOverlay -> when (voiceVisualState) {
                            VoiceVisualState.Idle -> expression
                            VoiceVisualState.Listening -> FaceExpression.LISTENING
                            VoiceVisualState.Speaking -> FaceExpression.SPEAKING
                        }
                        else -> expression
                    },
                    diffuseGlow = diffuseGlow,
                )
                if (hud) Row(
                    Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp)
                        .background(Color(0xEE191C24), RoundedCornerShape(28.dp)).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { voiceOverlay = true; hud = false }) { Text("对话") }
                    TextButton(onClick = { petting = true; hud = false }) { Text("抚摸") }
                    TextButton(onClick = { navigate(LifeRoute.LifeCenter) }) { Text("生命中心") }
                }
                if (voiceOverlay) VoiceConversationOverlay(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onClose = { voiceOverlay = false },
                    onVisualStateChange = { voiceVisualState = it },
                )
            }
            LifeRoute.Conversation -> ChatScreen(onBack = { navigate(LifeRoute.Living) })
            LifeRoute.Voice -> VoiceSettingsScreen(onBack = { navigate(LifeRoute.LifeCenter) })
            LifeRoute.LifeCenter -> LifeCenterRoute(LifeCenterUiState(recentChanges = changes.toList()), expression, diffuseGlow, navigate)
            LifeRoute.Brain -> AiBrainRoute(onBack = { navigate(LifeRoute.LifeCenter) })
            LifeRoute.Appearance -> Column(Modifier.fillMaxSize().background(AppColors.Background).safeDrawingPadding()) {
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
