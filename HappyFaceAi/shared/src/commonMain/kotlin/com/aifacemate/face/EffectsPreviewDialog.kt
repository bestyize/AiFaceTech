package com.aifacemate.face

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aifacemate.face.effects.*
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.ui.FaceDisplay
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

@Composable
internal fun EffectsPreviewDialog(preferences: FaceEffectsPreferences, onChange: (FaceEffectsPreferences) -> Unit, onClose: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var closeMenuSignal by remember { mutableStateOf(0) }
    Dialog(onDismissRequest = { if (menuOpen) closeMenuSignal++ else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        EffectsPreviewContent(preferences, onChange, onClose,
            onMenuOpenChange = { menuOpen = it }, closeMenuSignal = closeMenuSignal)
    }
}

@Composable
fun EffectsPreviewContent(preferences: FaceEffectsPreferences, onChange: (FaceEffectsPreferences) -> Unit, onClose: () -> Unit,
    onMenuOpenChange: (Boolean) -> Unit = {}, closeMenuSignal: Int = 0) {
    val clock = remember { TimeSource.Monotonic.markNow() }
    val controller = remember { EffectController() }
    var scene by remember { mutableStateOf<EffectScene?>(null) }
    var sequence by remember { mutableStateOf(0) }
    var controlsOpen by remember { mutableStateOf(false) }
    fun setMenuOpen(open: Boolean) { controlsOpen = open; onMenuOpenChange(open) }
    LaunchedEffect(closeMenuSignal) { if (closeMenuSignal > 0) setMenuOpen(false) }
    LaunchedEffect(preferences.enabled) { if (!preferences.enabled) { controller.clear(); scene = null } }
    LaunchedEffect(scene?.instanceId) {
        while (scene != null) { delay(100); scene = controller.sceneAt(clock.elapsedNow().inWholeMilliseconds) }
    }
    val expression = when (scene?.effect) {
        null -> FaceExpression.IDLE
        EffectId.Fireworks -> FaceExpression.CELEBRATING
        EffectId.Hearts -> FaceExpression.BASHFUL_SMILE
        EffectId.RainToSun -> FaceExpression.COMFORTING
        EffectId.StarReward -> FaceExpression.APPROVING
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFF1C779), onPrimary = Color(0xFF221D13))) {
    CompositionLocalProvider(LocalContentColor provides Color(0xFFF4F3EF)) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        val popupHeight = (maxHeight - 24.dp).coerceAtLeast(120.dp)
        @Composable fun Preview(modifier: Modifier) {
            BoxWithConstraints(modifier.background(Color.Black).clipToBounds().testTag("effects.preview.pane"),
                contentAlignment = Alignment.Center) {
                val faceWidth = minOf(maxWidth, maxHeight * (16f / 9f))
                Box(Modifier.width(faceWidth).aspectRatio(16f / 9f).clipToBounds().testTag("effects.preview.canvas")) {
                FaceDisplay(expression = expression)
                EffectLayer(scene, { clock.elapsedNow().inWholeMilliseconds }, strength = preferences.strength, useEmoji = preferences.emoji)
                }
            }
        }
        @Composable fun Controls(modifier: Modifier) {
            Column(modifier.verticalScroll(rememberScrollState()).testTag("effects.controls.pane"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("effects.enabled")
                    .toggleable(preferences.enabled, role = Role.Switch, onValueChange = { onChange(preferences.copy(enabled = it)) }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("开启惊喜特效", Modifier.weight(1f))
                    Switch(preferences.enabled, onCheckedChange = null)
                }
                Text("强度 ${(preferences.strength * 100).toInt()}%")
                Slider(preferences.strength, { onChange(preferences.copy(strength = it)) }, valueRange = .2f..1f)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("effects.emoji")
                    .toggleable(preferences.emoji, role = Role.Switch, onValueChange = { onChange(preferences.copy(emoji = it)) }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("使用 emoji 符号", Modifier.weight(1f))
                    Switch(preferences.emoji, onCheckedChange = null)
                }
                Text("关闭符号后使用自绘图形；点击下方按钮预览。", style = MaterialTheme.typography.bodySmall)
                EffectId.entries.forEach { effect ->
                    OutlinedButton(enabled = preferences.enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("effects.preview.${effect.name}"),
                        onClick = {
                            sequence++
                            val next = EffectScene("preview-$sequence", effect, clock.elapsedNow().inWholeMilliseconds, 100, sequence)
                            controller.clear(); controller.play(next, next.startedAtMillis); scene = next
                            setMenuOpen(false)
                        }) { Text(effect.label) }
                }
                TextButton(onClick = { controller.clear(); scene = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("effects.stop")) { Text("停止特效") }
            }
        }
        Preview(Modifier.fillMaxSize())
        if (!controlsOpen) IconButton(onClick = { setMenuOpen(true) },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp).testTag("effects.menu")
                .semantics { contentDescription = "打开特效菜单" }) {
            Canvas(Modifier.size(22.dp)) {
                repeat(3) { i ->
                    drawLine(Color.White.copy(alpha = .65f), Offset(2f, size.height * (.23f + i * .27f)),
                        Offset(size.width - 2f, size.height * (.23f + i * .27f)), 2f, StrokeCap.Round)
                }
            }
        }
        if (controlsOpen) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .30f))
                .testTag("effects.menu.scrim").clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { setMenuOpen(false) })
            Surface(modifier = Modifier.align(Alignment.Center).padding(12.dp).widthIn(max = 420.dp)
                .fillMaxWidth().heightIn(max = popupHeight).testTag("effects.menu.panel"),
                color = Color(0xF0181F22), contentColor = Color(0xFFF4F3EF), shape = RoundedCornerShape(18.dp),
                shadowElevation = 8.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { setMenuOpen(false) }, modifier = Modifier.size(48.dp).testTag("effects.menu.collapse")
                            .semantics { contentDescription = "收起特效菜单" }) {
                            Canvas(Modifier.size(18.dp)) {
                                drawLine(Color.White.copy(alpha = .8f), Offset(2f, 2f), Offset(size.width - 2f, size.height - 2f), 2f, StrokeCap.Round)
                                drawLine(Color.White.copy(alpha = .8f), Offset(size.width - 2f, 2f), Offset(2f, size.height - 2f), 2f, StrokeCap.Round)
                            }
                        }
                    }
                    Controls(Modifier.weight(1f, fill = false).fillMaxWidth())
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("effects.close")) { Text("返回猫脸") }
                }
            }
        }
    }
    }
    }
}
