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
import com.aifacemate.face.behavior.proactive.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

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
        var visionStatus by remember { mutableStateOf("未开启") }
        var proactiveOpening by remember { mutableStateOf<ProactiveOpening?>(null) }
        val proactive = remember { ProactiveConversationController() }
        val voicePreferences = remember(route) { VoicePreferencesStore.load().forDevice() }
        val proactiveActivationVersion = remember(route) { VoicePreferencesStore.proactiveActivationVersion() }
        val proactiveReady = remember(route, voicePreferences) { proactiveVoiceReady(voicePreferences) }
        var appActive by remember { mutableStateOf(true) }
        LaunchedEffect(visionNotice) { if (visionNotice != null) { delay(4000); visionNotice = null } }
        val behavior = remember { BehaviorController() }
        val clock = remember { TimeSource.Monotonic.markNow() }
        val now = { clock.elapsedNow().inWholeMilliseconds }
        LaunchedEffect(voicePreferences.proactiveCooldownSeconds) {
            proactive.configureCooldown(voicePreferences.proactiveCooldownSeconds * 1000L)
        }
        var proactiveStatus by remember { mutableStateOf("等待微笑") }
        LaunchedEffect(proactiveActivationVersion) {
            if (voicePreferences.proactiveConversation && proactiveActivationVersion > 0)
                proactive.explicitlyEnabled(now())
        }
        val closeVoice: () -> Unit = {
            if (voiceOverlay) proactive.conversationEnded(now())
            voiceOverlay = false; proactiveOpening = null
        }
        LifecycleEventEffect(Lifecycle.Event.ON_START) { appActive = true }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
            appActive = false; closeVoice(); proactive.resetEvidence()
        }
        var behaviorState by remember { mutableStateOf(behavior.state(now())) }
        var lastProactiveDiagnosticAt by remember { mutableStateOf(-3000L) }
        val dispatch: (BehaviorEvent) -> Unit = { event ->
            val time = now()
            behaviorState = behavior.accept(event, time)
            if (event is BehaviorEvent.Vision) {
                val gate = when {
                        !voicePreferences.proactiveConversation -> "disabled"
                        !appActive -> "background"
                        route != LifeRoute.Living -> "other_page"
                        voiceOverlay -> "voice_busy"
                        hud -> "controls_open"
                        !proactiveReady -> "speech_not_ready"
                        else -> proactive.status(time)
                    }
                proactiveStatus = when {
                    gate == "disabled" -> "已关闭"
                    gate == "voice_busy" -> "对话进行中"
                    gate == "speech_not_ready" -> "声音未就绪"
                    gate.startsWith("cooldown_") -> "休息 ${gate.removePrefix("cooldown_").removeSuffix("s")} 秒"
                    gate == "waiting_for_new_smile" -> "等待新一轮微笑"
                    gate.startsWith("confirming_") -> "确认微笑"
                    else -> "等待微笑"
                }
                if (event.faceAnalyzed && time - lastProactiveDiagnosticAt >= 3000) {
                    lastProactiveDiagnosticAt = time
                    println("FaceProactive gate=$gate face=${event.personVisible} cue=${event.appearance}")
                }
                proactive.observe(event,
                    eligible = appActive && route == LifeRoute.Living && !voiceOverlay && !hud && proactiveReady && voicePreferences.proactiveConversation,
                    nowMillis = time,
                )?.let { opening -> proactiveOpening = opening; voiceOverlay = true; hud = false }
            } else if (event == BehaviorEvent.VisionStopped) proactive.resetEvidence()
        }
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
            routeName = it.name; hud = false; closeVoice()
            dispatch(BehaviorEvent.ResetInteraction)
        }
        onBackHandler(route != LifeRoute.Living || hud || voiceOverlay) {
            if (voiceOverlay) closeVoice()
            else if (hud) hud = false else navigate(when (route) {
                LifeRoute.LifeCenter -> LifeRoute.Living
                LifeRoute.Camera -> LifeRoute.valueOf(cameraReturnRouteName)
                else -> LifeRoute.LifeCenter
            })
        }
        LaunchedEffect(hud) { if (hud) { delay(3500); hud = false } }
        when (route) {
            LifeRoute.Living -> Box(Modifier.fillMaxSize()) {
                LivingPerception(now = now, onEvent = dispatch, onNotice = { visionNotice = it }, onStatus = { visionStatus = it })
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
                    listeningActivity = behaviorState.listeningActivity,
                    contentOffsetY = if (voiceOverlay) VOICE_FACE_OFFSET_Y else 0.dp,
                )
                if (hud) Row(
                    Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp)
                        .background(Color(0xEE191C24), RoundedCornerShape(28.dp)).padding(8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { voiceOverlay = true; hud = false }) {
                        Text(if (voicePreferences.proactiveConversation) "对话 · $proactiveStatus" else "对话")
                    }
                    TextButton(onClick = { dispatch(BehaviorEvent.Petted); hud = false }) { Text("抚摸") }
                    TextButton(onClick = { navigate(LifeRoute.Camera) }) { Text("视觉 · $visionStatus") }
                    TextButton(onClick = { navigate(LifeRoute.LifeCenter) }) { Text("生命中心") }
                }
                if (voiceOverlay) VoiceConversationOverlay(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onClose = closeVoice,
                    proactiveOpening = proactiveOpening,
                    onProactiveNoResponse = { visionNotice = "暂时没有听到回应，我先继续陪伴你" },
                    onProactiveSessionTimeout = { visionNotice = "这一轮先聊到这里，需要时可以再开启对话" },
                    onVisualStateChange = { dispatch(BehaviorEvent.PhaseChanged(when (it) {
                        VoiceVisualState.Idle -> InteractionPhase.Idle
                        VoiceVisualState.Listening -> InteractionPhase.Listening
                        VoiceVisualState.Processing -> InteractionPhase.Processing
                        VoiceVisualState.Speaking -> InteractionPhase.Speaking
                    })) },
                    onUserText = { dispatch(BehaviorEvent.UserText(it)) },
                    onSpeechObservation = { observation -> dispatch(when (observation) {
                        com.aifacemate.face.voice.VoiceObservation.Cancelled -> BehaviorEvent.VoiceCancelled
                        is com.aifacemate.face.voice.VoiceObservation.PartialText -> BehaviorEvent.ListeningText(observation.text)
                        is com.aifacemate.face.voice.VoiceObservation.Level -> BehaviorEvent.SpeechLevel(observation.value)
                    }) },
                )
                if (visionStatus == "监测中") VisionObservationIcon(
                    modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(4.dp),
                    onClick = { navigate(LifeRoute.Camera) },
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
