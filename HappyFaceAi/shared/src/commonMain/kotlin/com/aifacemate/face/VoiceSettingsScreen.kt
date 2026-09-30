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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.theme.AppColors
import com.aifacemate.face.voice.asr.client.SenseVoiceAsr
import com.aifacemate.face.voice.systemAsrAvailable
import com.aifacemate.face.voice.systemTtsAvailable
import com.aifacemate.face.voice.realtimeAudioAvailable
import com.aifacemate.face.voice.tts.client.PiperTts
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
    var piperInstalled by remember { mutableStateOf(PiperTts.installed()) }
    var piperBusy by remember { mutableStateOf(false) }
    var piperProgress by remember { mutableStateOf(0) }
    var piperError by remember { mutableStateOf<String?>(null) }
    VoiceSettingsContent(
        preferences = draft,
        onPreferencesChange = { draft = it },
        onBack = onBack,
        onSave = { VoicePreferencesStore.save(draft); onBack() },
        systemAsrAvailable = systemAsrAvailable(),
        systemTtsAvailable = systemTtsAvailable(),
        piperSupported = PiperTts.supported(),
        realtimeAvailable = realtimeAudioAvailable(),
        senseVoiceSupported = SenseVoiceAsr.supported(),
        modelInstalled = modelInstalled,
        modelBusy = modelBusy,
        modelProgress = modelProgress,
        modelError = modelError,
        piperInstalled = piperInstalled,
        piperBusy = piperBusy,
        piperProgress = piperProgress,
        piperError = piperError,
        onPiperAction = {
            piperBusy = true
            piperProgress = 0
            piperError = null
            scope.launch {
                try {
                    if (piperInstalled) PiperTts.remove()
                    else {
                        var last = -1
                        PiperTts.install { downloaded, total ->
                            val percent = (downloaded * 100 / total).toInt()
                            if (percent != last) {
                                last = percent
                                scope.launch { piperProgress = percent }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    piperError = error.message ?: "Piper 模型操作失败"
                } finally {
                    piperInstalled = PiperTts.installed()
                    piperBusy = false
                }
            }
        },
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
    systemAsrAvailable: Boolean,
    systemTtsAvailable: Boolean,
    piperSupported: Boolean = false,
    realtimeAvailable: Boolean,
    senseVoiceSupported: Boolean,
    modelInstalled: Boolean = false,
    modelBusy: Boolean = false,
    modelProgress: Int = 0,
    modelError: String? = null,
    piperInstalled: Boolean = false,
    piperBusy: Boolean = false,
    piperProgress: Int = 0,
    piperError: String? = null,
    onPiperAction: () -> Unit = {},
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
            val landscape = maxWidth > maxHeight
            val iconRail = maxWidth < 400.dp
            val railWidth = if (iconRail) 64.dp else (88 * fontScale).dp
            Column(
                Modifier.widthIn(max = 1320.dp).fillMaxSize().padding(horizontal = inset),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
            ) {
                if (!split && !landscape) Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VoiceBackButton(showBackLabel, onClick = onBack)
                    Column(Modifier.weight(1f)) {
                        Text("声音与对话", color = VoiceText, fontSize = if (compact) 18.sp else 24.sp, fontWeight = FontWeight.SemiBold)
                        if (!compact) Text("让小星用你喜欢的方式听与说", color = VoiceMuted, fontSize = 12.sp)
                    }
                    VoiceSaveButton(horizontalPadding = if (compact) 14.dp else 22.dp, onClick = onSave)
                }

                @Composable
                fun ModeChoices() {
                    VoiceMode.entries.forEach { mode ->
                        val available = when (mode) {
                            VoiceMode.Realtime -> realtimeAvailable
                            VoiceMode.Traditional -> true
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
                fun FlowPreview() {
                    val route = if (preferences.mode == VoiceMode.Realtime) {
                        if (preferences.provider == RealtimeProvider.Qwen) "千问实时服务" else "豆包实时服务"
                    } else {
                        val asr = when (preferences.traditionalAsr) {
                            TraditionalAsr.System -> "系统识别"
                            TraditionalAsr.Mimo -> "MiMo 识别"
                            TraditionalAsr.SenseVoice -> "SenseVoice 本地识别"
                        }
                        val tts = when (preferences.traditionalTts) {
                            TraditionalTts.System -> "系统朗读"
                            TraditionalTts.Mimo -> "MiMo 朗读"
                            TraditionalTts.Piper -> "Piper 本地朗读"
                        }
                        "$asr → AI 大脑 → $tts"
                    }
                    VoicePanel(Modifier.fillMaxWidth().testTag("voice.flow.preview"), "当前语音流程", compact) {
                        Text(route, color = VoiceGold, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        VoiceHint(if (preferences.mode == VoiceMode.Realtime)
                            "实时服务负责连续聆听、理解并生成语音；独立识别与朗读选项不会参与此模式。"
                        else "AI 大脑负责生成回复；语音识别与朗读分别配置，可自由组合。")
                        if (preferences.keepFaceVisible) VoiceHint(
                            if (preferences.mode == VoiceMode.Realtime || preferences.traditionalAsr == TraditionalAsr.System)
                                "倾听时随声音轻微活动；识别服务提供中间文字时，可边听边回应表情。"
                            else "录音时随声音轻微活动，结束识别后按内容回应表情。边听边按内容回应可选择系统识别或实时语音。"
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
                            FlowPreview()
                            @Composable
                            fun SelectionCard(modifier: Modifier) {
                                VoicePanel(modifier, if (preferences.mode == VoiceMode.Realtime) "实时语音服务" else "语音输入 · ASR", compact) {
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
                                                    description = when (asr) {
                                                        TraditionalAsr.System -> if (systemAsrAvailable) "使用设备提供的识别服务" else "此设备不可用"
                                                        TraditionalAsr.Mimo -> "在线识别，需要 MiMo API Key"
                                                        TraditionalAsr.SenseVoice -> if (senseVoiceSupported) "下载模型后在本机识别" else "此设备暂不可用"
                                                    },
                                                    selected = preferences.traditionalAsr == asr,
                                                    enabled = when (asr) {
                                                        TraditionalAsr.System -> systemAsrAvailable
                                                        TraditionalAsr.SenseVoice -> senseVoiceSupported
                                                        TraditionalAsr.Mimo -> true
                                                    },
                                                    modifier = Modifier.fillMaxWidth().testTag("voice.asr.${asr.name}"),
                                                    onClick = { onPreferencesChange(preferences.copy(traditionalAsr = asr)) })
                                            }
                                            if (preferences.traditionalAsr == TraditionalAsr.System) {
                                                VoiceHint("系统服务可能需要网络，具体识别能力取决于设备设置。")
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
                                                VoiceHint("本地识别无需密钥；配置 MiMo 后可在线回退。")
                                            }
                                            VoiceHint("识别完成后，由 AI 大脑生成回复。")
                                        }
                                    }
                                }
                            }

                            @Composable
                            fun ConnectionCard(modifier: Modifier) {
                                VoicePanel(modifier, if (preferences.mode == VoiceMode.Traditional) "语音输出 · TTS" else "服务连接", compact) {
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
                                            TraditionalTts.entries.forEach { tts ->
                                                val description = when (tts) {
                                                    TraditionalTts.System -> if (systemTtsAvailable) "使用设备已安装的语音引擎" else "此设备不可用"
                                                    TraditionalTts.Mimo -> "在线合成，可选择 MiMo 音色"
                                                    TraditionalTts.Piper -> if (piperSupported) "中文离线合成；首次需下载约 21 MB 模型文件" else "此设备暂不可用"
                                                }
                                                VoiceChoice(tts.label, description = description,
                                                    selected = preferences.traditionalTts == tts,
                                                    enabled = when (tts) {
                                                        TraditionalTts.System -> systemTtsAvailable
                                                        TraditionalTts.Mimo -> true
                                                        TraditionalTts.Piper -> piperSupported
                                                    },
                                                    modifier = Modifier.fillMaxWidth().testTag("voice.tts.${tts.name}"),
                                                    onClick = { onPreferencesChange(preferences.copy(traditionalTts = tts)) })
                                            }
                                            if (preferences.traditionalTts == TraditionalTts.Piper) {
                                                VoiceHint(if (piperInstalled) "Piper 中文 Chaowen INT8 模型已下载；合成在本机离线运行。"
                                                    else "采用 sherpa-onnx Piper 中文单说话人 INT8 模型，不占用 APK 空间。")
                                                if (piperBusy) {
                                                    LinearProgressIndicator(progress = { piperProgress / 100f }, modifier = Modifier.fillMaxWidth())
                                                    VoiceHint("Piper 模型处理中 · $piperProgress%")
                                                }
                                                piperError?.let { Text(it, color = AppColors.Amber, fontSize = 13.sp) }
                                                OutlinedButton(onClick = onPiperAction, enabled = !piperBusy,
                                                    modifier = Modifier.testTag("voice.piper.action").heightIn(min = 48.dp)) {
                                                    Text(if (piperInstalled) "删除 Piper 模型" else "下载 Piper 模型")
                                                }
                                            }
                                            if (preferences.traditionalAsr == TraditionalAsr.Mimo ||
                                                preferences.traditionalTts == TraditionalTts.Mimo ||
                                                (preferences.traditionalAsr == TraditionalAsr.SenseVoice && preferences.mimoApiKey.isNotBlank())) {
                                                val mimoUses = buildList {
                                                    when (preferences.traditionalAsr) {
                                                        TraditionalAsr.Mimo -> add("在线识别")
                                                        TraditionalAsr.SenseVoice -> if (preferences.mimoApiKey.isNotBlank()) add("本地失败时在线回退识别")
                                                        TraditionalAsr.System -> Unit
                                                    }
                                                    if (preferences.traditionalTts == TraditionalTts.Mimo) add("在线朗读")
                                                }.joinToString("与")
                                                VoiceHint("MiMo API Key：用于$mimoUses。")
                                                VoiceField("MiMo API Key", preferences.mimoApiKey, "voice.mimo.key", secret = true) { onPreferencesChange(preferences.copy(mimoApiKey = it)) }
                                                VoiceField("MiMo API Base URL", preferences.mimoBaseUrl, "voice.mimo.baseUrl") { onPreferencesChange(preferences.copy(mimoBaseUrl = it)) }
                                            } else if (preferences.traditionalAsr == TraditionalAsr.SenseVoice) {
                                                VoiceHint("如需 SenseVoice 识别失败时在线回退，可配置 MiMo API Key。")
                                                VoiceField("MiMo API Key（可选）", preferences.mimoApiKey, "voice.mimo.key", secret = true) { onPreferencesChange(preferences.copy(mimoApiKey = it)) }
                                                VoiceField("MiMo API Base URL", preferences.mimoBaseUrl, "voice.mimo.baseUrl") { onPreferencesChange(preferences.copy(mimoBaseUrl = it)) }
                                            }
                                            if (preferences.traditionalTts == TraditionalTts.Mimo) {
                                                VoiceField("MiMo 音色", preferences.mimoVoice, "voice.mimo.voice", placeholder = "例如 Chloe") { onPreferencesChange(preferences.copy(mimoVoice = it)) }
                                            }
                                        }
                                    }
                                }
                            }

                            if (spacious) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                                    SelectionCard(Modifier.weight(1f))
                                    ConnectionCard(Modifier.weight(1f))
                                }
                            } else {
                                SelectionCard(Modifier.fillMaxWidth())
                                ConnectionCard(Modifier.fillMaxWidth())
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
                    Row(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.width(if (spacious) 208.dp else 180.dp).fillMaxHeight().padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                VoiceBackButton(showLabel = true, onClick = onBack)
                                Text("声音与对话", color = VoiceText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("对话方式", color = VoiceMuted, fontSize = 13.sp)
                                ModeChoices()
                                VoiceHint("选择方式后，在右侧配置服务。保存后用于下一次对话。")
                            }
                            VoiceSaveButton(Modifier.fillMaxWidth(), onClick = onSave)
                        }
                        Configuration(Modifier.weight(1f).fillMaxHeight())
                    }
                } else if (landscape) {
                    Row(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.width(railWidth).fillMaxHeight().padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            VoiceBackButton(showLabel = !iconRail, Modifier.fillMaxWidth(), onClick = onBack)
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("voice.rail"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                VoiceMode.entries.forEach { mode ->
                                    val available = when (mode) {
                                        VoiceMode.Realtime -> realtimeAvailable
                                        VoiceMode.Traditional -> true
                                    }
                                    VoiceChoice(if (iconRail) mode.tinyLabel() else mode.shortLabel(),
                                        description = if (!available) "不可用" else null,
                                        selected = preferences.mode == mode, enabled = available,
                                        modifier = Modifier.fillMaxWidth().testTag("voice.mode.${mode.name}")
                                            .semantics { contentDescription = mode.shortLabel() },
                                        onClick = { onPreferencesChange(preferences.copy(mode = mode)) })
                                }
                            }
                            VoiceSaveButton(Modifier.fillMaxWidth(), horizontalPadding = 4.dp, onClick = onSave)
                        }
                        Configuration(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VoiceMode.entries.forEach { mode ->
                            val available = when (mode) {
                                VoiceMode.Realtime -> realtimeAvailable
                                VoiceMode.Traditional -> true
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

private fun VoiceMode.tinyLabel() = when (this) {
    VoiceMode.Realtime -> "实时"
    VoiceMode.Traditional -> "分段"
}

private fun VoiceMode.shortLabel() = when (this) {
    VoiceMode.Realtime -> "实时语音"
    VoiceMode.Traditional -> "分段语音"
}

private fun VoiceMode.summary() = when (this) {
    VoiceMode.Realtime -> "连续聆听，自然接话"
    VoiceMode.Traditional -> "说完后识别 → AI 大脑回复 → 语音朗读"
}

@Composable
private fun VoiceBackButton(showLabel: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.testTag("voice.back").heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = if (showLabel) 12.dp else 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Canvas(Modifier.size(16.dp).semantics { contentDescription = "返回" }) {
            drawLine(VoiceGold, Offset(size.width * .75f, size.height * .12f), Offset(size.width * .3f, size.height * .5f), 2.dp.toPx())
            drawLine(VoiceGold, Offset(size.width * .3f, size.height * .5f), Offset(size.width * .75f, size.height * .88f), 2.dp.toPx())
        }
        if (showLabel) {
            Spacer(Modifier.width(4.dp))
            Text("返回", fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun VoiceSaveButton(modifier: Modifier = Modifier, horizontalPadding: Dp = 22.dp, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.testTag("voice.save").heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
    ) { Text("保存", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
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
