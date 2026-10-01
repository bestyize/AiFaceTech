package com.aifacemate.face

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        EffectsPreviewContent(preferences, onChange, onClose)
    }
}

@Composable
fun EffectsPreviewContent(preferences: FaceEffectsPreferences, onChange: (FaceEffectsPreferences) -> Unit, onClose: () -> Unit) {
    val clock = remember { TimeSource.Monotonic.markNow() }
    val controller = remember { EffectController() }
    var scene by remember { mutableStateOf<EffectScene?>(null) }
    var sequence by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(EffectId.Fireworks) }
    LaunchedEffect(preferences.enabled) { if (!preferences.enabled) { controller.clear(); scene = null } }
    LaunchedEffect(scene?.instanceId) {
        while (scene != null) { delay(100); scene = controller.sceneAt(clock.elapsedNow().inWholeMilliseconds) }
    }
    val expression = when (selected) {
        EffectId.Fireworks -> FaceExpression.CELEBRATING
        EffectId.Hearts -> FaceExpression.BASHFUL_SMILE
        EffectId.RainToSun -> FaceExpression.COMFORTING
        EffectId.StarReward -> FaceExpression.APPROVING
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFF1C779), onPrimary = Color(0xFF221D13))) {
    CompositionLocalProvider(LocalContentColor provides Color(0xFFF4F3EF)) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0C1011)).safeDrawingPadding().padding(12.dp)) {
        val wide = maxWidth >= 640.dp
        @Composable fun Preview(modifier: Modifier) {
            Box(modifier.background(Color.Black).clipToBounds()) {
                FaceDisplay(expression = expression)
                EffectLayer(scene, { clock.elapsedNow().inWholeMilliseconds }, strength = preferences.strength, useEmoji = preferences.emoji)
            }
        }
        @Composable fun Controls(modifier: Modifier) {
            Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            selected = effect; sequence++
                            val next = EffectScene("preview-$sequence", effect, clock.elapsedNow().inWholeMilliseconds, 100, sequence)
                            controller.clear(); controller.play(next, next.startedAtMillis); scene = next
                        }) { Text(effect.label) }
                }
                TextButton(onClick = { controller.clear(); scene = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("effects.stop")) { Text("停止特效") }
            }
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("惊喜特效", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("effects.close")) { Text("返回猫脸") }
            }
            if (wide) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Preview(Modifier.weight(1f).fillMaxHeight())
                Controls(Modifier.width(220.dp).fillMaxHeight())
            } else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Preview(Modifier.weight(1f).fillMaxWidth())
                Controls(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
    }
    }
}
