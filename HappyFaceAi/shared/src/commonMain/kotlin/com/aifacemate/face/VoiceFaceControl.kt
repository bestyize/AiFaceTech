package com.aifacemate.face

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

enum class VoiceFacePhase { Idle, Listening, Processing, Speaking }

val VOICE_FACE_OFFSET_Y = (-24).dp

data class VoiceFaceControlState(
    val phase: VoiceFacePhase = VoiceFacePhase.Idle,
    val status: String = "可以开始对话",
    val realtime: Boolean = false,
    val actionEnabled: Boolean = true,
    val notice: String? = null,
) {
    val actionLabel: String get() = when {
        notice != null -> "查看对话提示"
        realtime && phase != VoiceFacePhase.Idle -> "结束实时对话"
        phase == VoiceFacePhase.Listening -> "结束说话并提交"
        phase == VoiceFacePhase.Speaking -> "停止朗读"
        else -> "开始说话"
    }
}

/** Face-only presentation. Taps control voice; long-press opens the transcript/details. */
@Composable
fun VoiceFaceControl(
    state: VoiceFaceControlState,
    onVoice: () -> Unit,
    onOpenConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val compact = maxWidth.value / fontScale < 400f || maxHeight.value / fontScale < 220f
        val spacious = !compact && maxWidth >= 700.dp
        val label = if (compact) {
            when {
                state.notice != null -> "详情"
                !state.actionEnabled && state.phase == VoiceFacePhase.Idle -> "不可用"
                state.phase == VoiceFacePhase.Idle -> "说话"
                state.phase == VoiceFacePhase.Listening -> "聆听"
                state.phase == VoiceFacePhase.Processing -> "处理中"
                else -> "回答"
            }
        } else state.notice ?: when (state.phase) {
            VoiceFacePhase.Idle -> if (state.actionEnabled) "点击说话" else "暂不可用"
            VoiceFacePhase.Listening -> "正在聆听"
            VoiceFacePhase.Processing -> "正在处理"
            VoiceFacePhase.Speaking -> "正在回答"
        }
        val hint = when {
            state.notice != null -> "点击查看详情"
            !state.actionEnabled && state.phase == VoiceFacePhase.Idle -> "长按查看详情"
            state.phase == VoiceFacePhase.Idle -> "长按查看对话记录"
            state.realtime -> "再次点击，结束对话"
            state.phase == VoiceFacePhase.Listening -> "再次点击，结束说话"
            state.phase == VoiceFacePhase.Speaking -> "再次点击，停止朗读"
            else -> "长按查看对话记录"
        }
        val ink = when {
            state.notice != null -> Color(0xFFE7C793)
            state.phase == VoiceFacePhase.Listening -> Color(0xFFB6EEF4)
            state.phase == VoiceFacePhase.Speaking -> Color(0xFFD3F1F4)
            else -> Color(0xFFCDD8DB)
        }
        val width = if (compact) (280f + 120f * (fontScale - 1f)).dp
            else (maxWidth * .4f).coerceIn(220.dp, 320.dp)
        val primary = rememberUpdatedState(onVoice)
        val open = rememberUpdatedState(onOpenConversation)
        val current = rememberUpdatedState(state)
        val canTap = state.notice != null || state.actionEnabled
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = if (compact) 8.dp else 12.dp)
                .width(width.coerceAtMost((maxWidth - 16.dp).coerceAtLeast(48.dp)))
                .heightIn(min = if (compact) 48.dp else if (spacious) 72.dp else 60.dp)
                .background(Color(0xBF141B1F), RoundedCornerShape(percent = 50))
                .testTag("voice.face.control")
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "语音对话"
                    stateDescription = state.status
                    if (canTap) onClick(label = state.actionLabel) {
                        if (current.value.notice != null) open.value() else primary.value()
                        true
                    }
                    onLongClick(label = "查看对话记录和详细提示") { open.value(); true }
                }
                .pointerInput(canTap) {
                    detectTapGestures(
                        onTap = {
                            if (current.value.notice != null) open.value()
                            else if (current.value.actionEnabled) primary.value()
                        },
                        onLongPress = { open.value() },
                    )
                }
                .padding(horizontal = if (compact) 8.dp else 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
                VoiceFaceMic(ink, Modifier.size(if (compact) 16.dp else if (spacious) 26.dp else 24.dp))
                if (!compact && state.notice == null && state.phase != VoiceFacePhase.Idle) {
                    VoiceFaceActivity(state.phase, ink, Modifier.size(width = 28.dp, height = 22.dp).testTag("voice.face.activity"))
                }
                Text(label, Modifier.testTag("voice.face.label").widthIn(max = if (compact) (220f + 20f * (fontScale - 1f)).dp else 320.dp), color = ink,
                    fontSize = if (compact) (6f / fontScale).sp else if (spacious) 24.sp else 18.sp,
                    lineHeight = if (compact) (8f / fontScale).sp else if (spacious) 29.sp else 23.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!compact) {
                Spacer(Modifier.height(3.dp))
                Text(hint.take(8), Modifier.testTag("voice.face.hint"), color = Color(0xFF8A9B9F), fontSize = 11.sp,
                    lineHeight = 15.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
        }
    }
}

@Composable
private fun VoiceFaceMic(ink: Color, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = size.width * .075f
        drawRoundRect(ink, Offset(size.width * .34f, size.height * .06f),
            Size(size.width * .32f, size.height * .54f), CornerRadius(size.width * .16f))
        drawArc(ink, 0f, 180f, false, Offset(size.width * .2f, size.height * .24f),
            Size(size.width * .6f, size.height * .5f), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(ink, Offset(size.width * .5f, size.height * .76f), Offset(size.width * .5f, size.height * .91f), stroke, StrokeCap.Round)
        drawLine(ink, Offset(size.width * .35f, size.height * .91f), Offset(size.width * .65f, size.height * .91f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun VoiceFaceActivity(phase: VoiceFacePhase, ink: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition()
    val progress by transition.animateFloat(0f, 6.283185f,
        infiniteRepeatable(tween(if (phase == VoiceFacePhase.Processing) 1600 else 1200, easing = LinearEasing)))
    // Decorative activity, not an audio level meter: capture APIs do not expose amplitude here.
    Canvas(modifier) {
        if (phase == VoiceFacePhase.Processing) {
            repeat(3) { index ->
                drawCircle(ink.copy(alpha = .4f + .6f * ((sin(progress - index * 1.5f) + 1f) / 2f)),
                    size.width / 12f, Offset(size.width * (index + .5f) / 3f, size.height / 2f))
            }
        } else {
            repeat(5) { index ->
                val height = size.height * (.25f + .65f * ((sin(progress + index * 1.4f) + 1f) / 2f))
                drawRoundRect(ink.copy(alpha = .85f), Offset(size.width * index / 5f, (size.height - height) / 2f),
                    Size(size.width / 10f, height), CornerRadius(size.width / 20f))
            }
        }
    }
}