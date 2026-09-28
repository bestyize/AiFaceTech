package com.aifacemate.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.theme.AppColors
import com.aifacemate.face.voice.asr.client.SenseVoiceAsr
import com.aifacemate.face.voice.nativeSpeechAvailable
import com.aifacemate.face.voice.realtimeAudioAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val VoiceGold = Color(0xFFF1C779)
private val VoiceText = Color(0xFFF4F3EF)
private val VoiceMuted = Color(0xFFA7AAA8)
private val VoiceBackground = Color(0xFF0C1011)
private val VoiceBorder = Color(0xFF48443A)
private val VoiceShape = RoundedCornerShape(16.dp)

@Composable
fun VoiceSettingsScreen(onBack: () -> Unit) {
    var draft by remember { mutableStateOf(VoicePreferencesStore.load().forDevice()) }
    val scope = rememberCoroutineScope()
    var modelInstalled by remember { mutableStateOf(SenseVoiceAsr.installed()) }
    var modelBusy by remember { mutableStateOf(false) }
    var modelProgress by remember { mutableStateOf(0) }
    var modelError by remember { mutableStateOf<String?>(null) }
    VoiceSettingsContent(
        preferences = draft,
        onPreferencesChange = { draft = it },
        onBack = onBack,
        onSave = { VoicePreferencesStore.save(draft); onBack() },
        nativeAvailable = nativeSpeechAvailable(),
        realtimeAvailable = realtimeAudioAvailable(),
        senseVoiceSupported = SenseVoiceAsr.supported(),
        modelInstalled = modelInstalled,
        modelBusy = modelBusy,
        modelProgress = modelProgress,
        modelError = modelError,
        onModelAction = {
            modelBusy = true
            modelProgress = 0
            modelError = null
            scope.launch {
                try {
                    if (modelInstalled) SenseVoiceAsr.remove()
                    else {
                        var last = -1
                        SenseVoiceAsr.install { downloaded, total ->
                            val percent = (downloaded * 100 / total).toInt()
                            if (percent != last) {
                                last = percent
                                scope.launch { modelProgress = percent }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    modelError = error.message ?: "模型操作失败"
                } finally {
                    modelInstalled = SenseVoiceAsr.installed()
                    modelBusy = false
                }
            }
        },
    )
}

/** Pure UI: previews and interaction checks never read or write saved credentials. */
@Composable
fun VoiceSettingsContent(
    preferences: VoicePreferences,
    onPreferencesChange: (VoicePreferences) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    nativeAvailable: Boolean,
    realtimeAvailable: Boolean,
    senseVoiceSupported: Boolean,
    modelInstalled: Boolean = false,
    modelBusy: Boolean = false,
    modelProgress: Int = 0,
    modelError: String? = null,
    onModelAction: () -> Unit = {},
) {
    val colors = darkColorScheme(
        primary = VoiceGold, onPrimary = Color(0xFF221D13),
        secondary = VoiceGold, background = VoiceBackground,
        surface = Color(0xFF191C1C), onSurface = VoiceText,
        onSurfaceVariant = VoiceMuted, outline = VoiceBorder,
    )
    MaterialTheme(colorScheme = colors) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(VoiceBackground).safeDrawingPadding().imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val split = maxWidth.value / fontScale >= 680f && maxHeight >= 300.dp
            val spacious = maxWidth.value / fontScale >= 1100f && maxHeight >= 500.dp
            val compact = maxHeight < 400.dp || maxWidth < 600.dp
            val showBackLabel = maxWidth.value / fontScale >= 420f
            val inset = if (compact) 12.dp else 20.dp
            Column(
                Modifier.widthIn(max = 1320.dp).fillMaxSize().padding(horizontal = inset),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("voice.back").heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Canvas(Modifier.size(16.dp).semantics { contentDescription = "返回" }) {
                            drawLine(VoiceGold, Offset(size.width * .75f, size.height * .12f), Offset(size.width * .3f, size.height * .5f), 2.dp.toPx())
                            drawLine(VoiceGold, Offset(size.width * .3f, size.height * .5f), Offset(size.width * .75f, size.height * .88f), 2.dp.toPx())
                        }
                        if (showBackLabel) {
                            Spacer(Modifier.width(4.dp))
                            Text("返回", fontSize = 13.sp)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("声音与对话", color = VoiceText, fontSize = if (compact) 18.sp else 24.sp, fontWeight = FontWeight.SemiBold)
                        if (!compact) Text("让小星用你喜欢的方式听与说", color = VoiceMuted, fontSize = 12.sp)
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.testTag("voice.save").heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = if (compact) 14.dp else 22.dp),
                    ) { Text("保存", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }

                @Composable
                fun ModeChoices() {
                    VoiceMode.entries.forEach { mode ->
                        val available = when (mode) {
                            VoiceMode.Realtime -> realtimeAvailable
                            VoiceMode.Traditional -> true
                            VoiceMode.Native -> nativeAvailable
                        }
                        VoiceChoice(
                            label = mode.shortLabel(),
                            description = if (!available) "此设备不可用" else mode.summary(),
                            selected = preferences.mode == mode,
                            enabled = available,
                            modifier = Modifier.testTag("voice.mode.${mode.name}").fillMaxWidth(),
                            onClick = { onPreferencesChange(preferences.copy(mode = mode)) },
                        )
                    }
                }

                @Composable
                fun Configuration(modifier: Modifier) {
                    // Each mode starts at the top of its own configuration area.
                    key(preferences.mode) {
                        Column(
                            modifier.verticalScroll(rememberScrollState()).testTag("voice.configuration"),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!compact) {
                                Text(preferences.mode.shortLabel(), color = VoiceText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                Text(preferences.mode.summary(), color = VoiceMuted, fontSize = 13.sp)
                            }
                            @Composable
                            fun SelectionCard(modifier: Modifier) {
                                VoicePanel(modifier, if (preferences.mode == VoiceMode.Realtime) "实时语音服务"
                                    else if (preferences.mode == VoiceMode.Traditional) "语音识别" else "系统语音", compact) {
                                    when (preferences.mode) {
                                        VoiceMode.Realtime -> {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                RealtimeProvider.entries.forEach { provider ->
                                                    VoiceChoice(provider.label, selected = preferences.provider == provider,
                                                        modifier = Modifier.weight(1f).testTag("voice.provider.${provider.name}"),
                                                        onClick = { onPreferencesChange(preferences.copy(provider = provider)) })
                                                }
                                            }
                                            if (preferences.provider == RealtimeProvider.Qwen) {
                                                VoiceHint("对话模型")
                                                listOf("qwen3.5-omni-flash-realtime", "qwen3.5-omni-plus-realtime").forEach { model ->
                                                    VoiceChoice(
                                                        if (model.contains("plus")) "Omni Plus · 更强能力" else "Omni Flash · 快速响应",
                                                        selected = preferences.qwenModel == model,
                                                        modifier = Modifier.fillMaxWidth().testTag("voice.qwen.$model"),
                                                        onClick = { onPreferencesChange(preferences.copy(qwenModel = model)) },
                                                    )
                                                }
                                                VoiceHint("日常交流由千问回答，复杂问题可交给 AI 大脑协助。")
                                            } else VoiceHint("豆包直接完成实时听说；当前没有接入 AI 大脑委托。")
                                        }
                                        VoiceMode.Traditional -> {
                                            TraditionalAsr.entries.forEach { asr ->
                                                VoiceChoice(asr.label,
                                                    description = if (asr == TraditionalAsr.Mimo) "无需下载模型，联网识别"
                                                        else if (senseVoiceSupported) "下载后可在本机识别" else "仅 Android 支持",
                                                    selected = preferences.traditionalAsr == asr,
                                                    enabled = asr != TraditionalAsr.SenseVoice || senseVoiceSupported,
                                                    modifier = Modifier.fillMaxWidth().testTag("voice.asr.${asr.name}"),
                                                    onClick = { onPreferencesChange(preferences.copy(traditionalAsr = asr)) })
                                            }
                                            if (preferences.traditionalAsr == TraditionalAsr.SenseVoice) {
                                                VoiceHint(if (modelInstalled) "SenseVoiceSmall INT8 已下载"
                                                    else "首次需下载约 239 MB 模型，不占用安装包空间。")
                                                if (modelBusy) {
                                                    LinearProgressIndicator(progress = { modelProgress / 100f }, modifier = Modifier.fillMaxWidth())
                                                    VoiceHint("模型处理中 · $modelProgress%")
                                                }
                                                modelError?.let { Text(it, color = AppColors.Amber, fontSize = 13.sp) }
                                                OutlinedButton(onClick = onModelAction, enabled = !modelBusy,
                                                    modifier = Modifier.testTag("voice.model.action").heightIn(min = 48.dp)) {
                                                    Text(if (modelInstalled) "删除本地模型" else "下载本地模型")
                                                }
                                                VoiceHint("本地识别无需密钥；配置 MiMo 后可在线回退并朗读回复。")
                                            }
                                            VoiceHint("先返回识别文字；已配置 AI 大脑时继续生成回答。")
                                        }
                                        VoiceMode.Native -> {
                                            VoiceHint(if (nativeAvailable) "使用设备自带的语音识别和朗读，无需语音服务密钥。"
                                                else "当前设备没有可用的系统语音服务。")
                                            VoiceHint("回答仍由 AI 大脑生成，请先在 AI 大脑中保存模型配置。")
                                        }
                                    }
                                }
                            }

                            @Composable
                            fun ConnectionCard(modifier: Modifier) {
                                VoicePanel(modifier, if (preferences.mode == VoiceMode.Traditional) "MiMo 语音合成与回退" else "连接配置", compact) {
                                    key(preferences.mode, preferences.provider) {
                                        if (preferences.mode == VoiceMode.Realtime) {
                                            if (preferences.provider == RealtimeProvider.Qwen) {
                                                VoiceField("工作空间 WSS 地址", preferences.qwenEndpoint, "voice.qwen.endpoint") { onPreferencesChange(preferences.copy(qwenEndpoint = it)) }
                                                VoiceField("API Key", preferences.qwenApiKey, "voice.qwen.key", secret = true) { onPreferencesChange(preferences.copy(qwenApiKey = it)) }
                                            } else {
                                                VoiceField("App ID", preferences.doubaoAppId, "voice.doubao.appId") { onPreferencesChange(preferences.copy(doubaoAppId = it)) }
                                                VoiceField("Access Key", preferences.doubaoAccessKey, "voice.doubao.accessKey", secret = true) { onPreferencesChange(preferences.copy(doubaoAccessKey = it)) }
                                                VoiceField("App Key", preferences.doubaoAppKey, "voice.doubao.appKey", secret = true) { onPreferencesChange(preferences.copy(doubaoAppKey = it)) }
                                            }
                                        } else {
                                            VoiceHint(if (preferences.traditionalAsr == TraditionalAsr.SenseVoice)
                                                "可选配置：开启语音朗读和在线识别回退。"
                                                else "MiMo 在线识别与朗读使用同一份配置。")
                                            VoiceField("MiMo API Key", preferences.mimoApiKey, "voice.mimo.key", secret = true) { onPreferencesChange(preferences.copy(mimoApiKey = it)) }
                                            VoiceField("API Base URL", preferences.mimoBaseUrl, "voice.mimo.baseUrl") { onPreferencesChange(preferences.copy(mimoBaseUrl = it)) }
                                            VoiceField("音色", preferences.mimoVoice, "voice.mimo.voice", placeholder = "例如 Chloe") { onPreferencesChange(preferences.copy(mimoVoice = it)) }
                                        }
                                    }
                                }
                            }

                            if (spacious && preferences.mode != VoiceMode.Native) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                                    SelectionCard(Modifier.weight(1f))
                                    ConnectionCard(Modifier.weight(1f))
                                }
                            } else {
                                SelectionCard(Modifier.fillMaxWidth())
                                if (preferences.mode != VoiceMode.Native) ConnectionCard(Modifier.fillMaxWidth())
                            }
                            VoicePanel(Modifier.fillMaxWidth(), "对话与表情", compact) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("voice.keepFaceVisible")
                                        .toggleable(value = preferences.keepFaceVisible, role = Role.Switch,
                                            onValueChange = { onPreferencesChange(preferences.copy(keepFaceVisible = it)) }),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("保持猫脸显示", color = VoiceText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        VoiceHint("轻点底部语音条启停，长按查看记录或输入文字。")
                                    }
                                    Switch(checked = preferences.keepFaceVisible, onCheckedChange = null)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                if (split) {
                    Row(Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(
                            Modifier.width(if (spacious) 208.dp else 180.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("对话方式", color = VoiceMuted, fontSize = 13.sp)
                            ModeChoices()
                            VoiceHint("选择方式后，在右侧配置服务。保存后用于下一次对话。")
                        }
                        Configuration(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VoiceMode.entries.forEach { mode ->
                            val available = when (mode) {
                                VoiceMode.Realtime -> realtimeAvailable
                                VoiceMode.Traditional -> true
                                VoiceMode.Native -> nativeAvailable
                            }
                            VoiceChoice(mode.shortLabel(), description = if (!available) "此设备不可用" else null,
                                selected = preferences.mode == mode, enabled = available,
                                modifier = Modifier.weight(1f).testTag("voice.mode.${mode.name}"),
                                onClick = { onPreferencesChange(preferences.copy(mode = mode)) })
                        }
                    }
                    Configuration(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

private fun VoiceMode.shortLabel() = when (this) {
    VoiceMode.Realtime -> "实时对话"
    VoiceMode.Traditional -> "传统对话"
    VoiceMode.Native -> "系统语音"
}

private fun VoiceMode.summary() = when (this) {
    VoiceMode.Realtime -> "连续聆听，自然接话"
    VoiceMode.Traditional -> "识别、思考，再朗读回答"
    VoiceMode.Native -> "使用设备自带的听与说"
}

@Composable
private fun VoicePanel(modifier: Modifier, title: String, compact: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(VoiceShape).background(Brush.linearGradient(listOf(Color(0xFF222525), Color(0xFF131617))))
            .border(1.dp, VoiceBorder, VoiceShape).padding(if (compact) 12.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(3.dp, 16.dp).background(VoiceGold, RoundedCornerShape(2.dp)))
            Text(title, color = VoiceText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun VoiceChoice(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.heightIn(min = 48.dp).clip(shape)
            .background(if (selected) Color(0xFF393329) else Color(0xFF171B1B))
            .border(1.dp, if (selected) VoiceGold else VoiceBorder.copy(alpha = .65f), shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = if (!enabled) VoiceMuted.copy(alpha = .6f) else if (selected) VoiceGold else VoiceText,
            fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        description?.let { Text(it, color = VoiceMuted, fontSize = 12.sp) }
    }
}

@Composable
private fun VoiceHint(text: String) { Text(text, color = VoiceMuted, fontSize = 12.sp) }

@Composable
private fun VoiceField(
    label: String,
    value: String,
    tag: String,
    secret: Boolean = false,
    placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontSize = 13.sp) },
        placeholder = placeholder?.let { { Text(it, fontSize = 13.sp) } },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
        singleLine = true, shape = RoundedCornerShape(12.dp),
        visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) {
            { TextButton(onClick = { revealed = !revealed }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Text(if (revealed) "隐藏" else "显示", fontSize = 12.sp)
            } }
        } else null,
    )
}
