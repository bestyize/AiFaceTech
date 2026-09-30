package com.aifacemate.face
import com.aifacemate.face.feature.aibrain.domain.*
import com.aifacemate.face.feature.aibrain.presentation.*

import com.aifacemate.face.theme.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.aifacemate.face.behavior.*
import kotlin.time.TimeSource

@Composable
fun App(onBackHandler: @Composable (Boolean, () -> Unit) -> Unit = { _, _ -> }) {
    MaterialTheme(colorScheme = darkColorScheme(primary = AppColors.Amber, background = AppColors.Background, surface = AppColors.Card)) {
        var routeName by rememberSaveable { mutableStateOf(LifeRoute.Living.name) }
        val route = LifeRoute.valueOf(routeName)
        var cameraReturnRouteName by rememberSaveable { mutableStateOf(LifeRoute.Vision.name) }
        var expressionName by rememberSaveable { mutableStateOf(FaceExpression.IDLE.name) }
        val expression = FaceExpression.valueOf(expressionName)
        var diffuseGlow by rememberSaveable { mutableStateOf(DEFAULT_DIFFUSE_GLOW) }
        var hud by remember { mutableStateOf(false) }
        var voiceOverlay by remember { mutableStateOf(false) }
        var visionNotice by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(visionNotice) { if (visionNotice != null) { delay(4000); visionNotice = null } }
        val behavior = remember { BehaviorController() }
        val clock = remember { TimeSource.Monotonic.markNow() }
        val now = { clock.elapsedNow().inWholeMilliseconds }
        var behaviorState by remember { mutableStateOf(behavior.state(now())) }
        val dispatch: (BehaviorEvent) -> Unit = { behaviorState = behavior.accept(it, now()) }
        LaunchedEffect(expression) { dispatch(BehaviorEvent.BaseChanged(expression)) }
        LaunchedEffect(Unit) { while (true) { behaviorState = behavior.state(now()); delay(50) } }
        LaunchedEffect(voiceOverlay, route) {
            if (!voiceOverlay || route != LifeRoute.Living) dispatch(BehaviorEvent.ResetInteraction)
        }
        val changes = remember { mutableStateListOf<RecentChange>() }
        val haptic = LocalHapticFeedback.current
        val navigate: (LifeRoute) -> Unit = {
            if (it == LifeRoute.Camera) {
                cameraReturnRouteName = if (route == LifeRoute.Living) LifeRoute.Living.name else LifeRoute.Vision.name
            }
            routeName = it.name; hud = false; voiceOverlay = false
            dispatch(BehaviorEvent.ResetInteraction)
        }
        onBackHandler(route != LifeRoute.Living || hud || voiceOverlay) {
            if (voiceOverlay) voiceOverlay = false
            else if (hud) hud = false else navigate(when (route) {
                LifeRoute.LifeCenter -> LifeRoute.Living
                LifeRoute.Camera -> LifeRoute.valueOf(cameraReturnRouteName)
                else -> LifeRoute.LifeCenter
            })
        }
        LaunchedEffect(hud) { if (hud) { delay(3500); hud = false } }
        when (route) {
            LifeRoute.Living -> Box(Modifier.fillMaxSize()) {
                LivingPerception(now = now, onEvent = dispatch, onNotice = { visionNotice = it })
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
                    expression = behaviorState.expression,
                    diffuseGlow = diffuseGlow,
                    contentOffsetY = if (voiceOverlay) VOICE_FACE_OFFSET_Y else 0.dp,
                )
                if (hud) Row(
                    Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp)
                        .background(Color(0xEE191C24), RoundedCornerShape(28.dp)).padding(8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { voiceOverlay = true; hud = false }) { Text("对话") }
                    TextButton(onClick = { dispatch(BehaviorEvent.Petted); hud = false }) { Text("抚摸") }
                    TextButton(onClick = { navigate(LifeRoute.Camera) }) { Text("视觉") }
                    TextButton(onClick = { navigate(LifeRoute.LifeCenter) }) { Text("生命中心") }
                }
                if (voiceOverlay) VoiceConversationOverlay(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onClose = { voiceOverlay = false },
                    onVisualStateChange = { dispatch(BehaviorEvent.PhaseChanged(when (it) {
                        VoiceVisualState.Idle -> InteractionPhase.Idle
                        VoiceVisualState.Listening -> InteractionPhase.Listening
                        VoiceVisualState.Processing -> InteractionPhase.Processing
                        VoiceVisualState.Speaking -> InteractionPhase.Speaking
                    })) },
                    onUserText = { dispatch(BehaviorEvent.UserText(it)) },
                )
                visionNotice?.let { AppToast(it) }
            }
            LifeRoute.Conversation -> ChatScreen(onBack = { navigate(LifeRoute.Living) })
            LifeRoute.Voice -> VoiceSettingsScreen(onBack = { navigate(LifeRoute.LifeCenter) })
            LifeRoute.Vision -> VisionSettingsScreen(onBack = { navigate(LifeRoute.LifeCenter) }, onOpenCamera = { navigate(LifeRoute.Camera) })
            LifeRoute.Camera -> CameraScreen(onBack = { navigate(LifeRoute.valueOf(cameraReturnRouteName)) })
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
