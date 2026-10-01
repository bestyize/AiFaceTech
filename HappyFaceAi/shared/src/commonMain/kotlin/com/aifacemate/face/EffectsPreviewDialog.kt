package com.aifacemate.face

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
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
import com.aifacemate.face.ui.FullscreenPreviewMenu
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
    var options by remember { mutableStateOf(false) }
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
        val effectColumns = if (maxWidth >= 260.dp) 2 else 1
        val compactControls = maxHeight < 360.dp || maxWidth < 400.dp
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
            Column(modifier.testTag("effects.controls.pane"), verticalArrangement = Arrangement.spacedBy(if (compactControls) 4.dp else 8.dp)) {
                if (options) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("effects.enabled")
                    .toggleable(preferences.enabled, role = Role.Switch, onValueChange = { onChange(preferences.copy(enabled = it)) }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("开启惊喜特效", Modifier.weight(1f))
                    Switch(preferences.enabled, onCheckedChange = null)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("强度 ${(preferences.strength * 100).toInt()}%")
                    Slider(preferences.strength, { onChange(preferences.copy(strength = it)) }, valueRange = .2f..1f,
                        modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("effects.emoji")
                    .toggleable(preferences.emoji, role = Role.Switch, onValueChange = { onChange(preferences.copy(emoji = it)) }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("使用 emoji 符号", Modifier.weight(1f))
                    Switch(preferences.emoji, onCheckedChange = null)
                }
                if (!compactControls) Text("关闭 emoji 后使用自绘图形。", style = MaterialTheme.typography.bodySmall)
                } else {
                if (!preferences.enabled) Text("特效已关闭，请在设置中开启。", style = MaterialTheme.typography.bodySmall)
                EffectId.entries.chunked(effectColumns).forEach { effects ->
                  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  effects.forEach { effect ->
                    OutlinedButton(enabled = preferences.enabled, modifier = Modifier.weight(1f).heightIn(min = 40.dp).testTag("effects.preview.${effect.name}"),
                        onClick = {
                            sequence++
                            val next = EffectScene("preview-$sequence", effect, clock.elapsedNow().inWholeMilliseconds, 100, sequence)
                            controller.clear(); controller.play(next, next.startedAtMillis); scene = next
                            setMenuOpen(false)
                        }) { Text(effect.label) }
                  }
                  }
                }
                TextButton(onClick = { controller.clear(); scene = null }, modifier = Modifier.heightIn(min = 32.dp).testTag("effects.stop")) { Text("停止特效") }
                }
            }
        }
        Preview(Modifier.fillMaxSize())
        FullscreenPreviewMenu(controlsOpen, { setMenuOpen(true) }, { setMenuOpen(false) },
            prefix = "effects", description = "特效菜单",
            onBack = onClose, backTag = "effects.close", navigation = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { options = false }, modifier = Modifier.heightIn(min = 32.dp).testTag("effects.tab.effects"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) { Text("效果", color = if (!options) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                    TextButton(onClick = { options = true }, modifier = Modifier.heightIn(min = 32.dp).testTag("effects.tab.settings"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) { Text("设置", color = if (options) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                }
            }) {
            Controls(Modifier.fillMaxWidth())
        }
    }
    }
    }
}
