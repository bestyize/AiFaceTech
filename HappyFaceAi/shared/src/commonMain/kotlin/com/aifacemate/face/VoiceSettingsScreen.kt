package com.aifacemate.face

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.theme.AppColors
import com.aifacemate.face.voice.nativeSpeechAvailable
import com.aifacemate.face.voice.realtimeAudioAvailable
import com.aifacemate.face.voice.asr.client.SenseVoiceAsr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun VoiceSettingsScreen(onBack: () -> Unit) {
    var draft by remember { mutableStateOf(VoicePreferencesStore.load().forDevice()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var modelInstalled by remember { mutableStateOf(SenseVoiceAsr.installed()) }
    var modelBusy by remember { mutableStateOf(false) }
    var modelProgress by remember { mutableStateOf(0) }
    var modelError by remember { mutableStateOf<String?>(null) }
    val nativeAvailable = remember { nativeSpeechAvailable() }
    val realtimeAvailable = remember { realtimeAudioAvailable() }
    Column(
        Modifier.fillMaxSize().background(AppColors.Background).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text("声音与对话", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text("选择小星听你说话与回答的方式", color = AppColors.Secondary)

        VoiceMode.entries.forEach { mode ->
            val available = when (mode) {
                VoiceMode.Realtime -> realtimeAvailable
                VoiceMode.Traditional -> true
                VoiceMode.Native -> nativeAvailable
            }
            val description = when (mode) {
                VoiceMode.Realtime -> "连续听说；对话由实时模型处理，需要的任务由 AI 大脑模型协助"
                VoiceMode.Traditional -> "MiMo 或 SenseVoice 识别；MiMo 合成；AI 大脑负责推理"
                VoiceMode.Native -> if (available) "系统识别与朗读；AI 大脑模型负责推理" else "当前设备没有可用的系统语音服务"
            }
            Row(
                Modifier.fillMaxWidth().background(AppColors.Card, RoundedCornerShape(16.dp))
                    .clickable(enabled = available) { draft = draft.copy(mode = mode) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = draft.mode == mode, onClick = { draft = draft.copy(mode = mode) }, enabled = available)
                Column(Modifier.weight(1f)) {
                    Text(mode.label, color = if (available) Color.White else AppColors.Secondary, fontWeight = FontWeight.SemiBold)
                    Text(description, color = AppColors.Secondary, fontSize = 12.sp)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(AppColors.Card, RoundedCornerShape(16.dp))
                .clickable { draft = draft.copy(keepFaceVisible = !draft.keepFaceVisible) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("对话时保持表情显示", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("开启后猫脸保持主画面。轻点底部语音条启停，长按查看记录、输入文字或关闭对话。", color = AppColors.Secondary, fontSize = 12.sp)
            }
            Switch(checked = draft.keepFaceVisible, onCheckedChange = { draft = draft.copy(keepFaceVisible = it) })
        }

        when (draft.mode) {
            VoiceMode.Realtime -> {
                SectionTitle("实时对话模型")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RealtimeProvider.entries.forEach { provider ->
                        FilterChip(selected = draft.provider == provider, onClick = { draft = draft.copy(provider = provider) }, label = { Text(provider.label) })
                    }
                }
                if (draft.provider == RealtimeProvider.Qwen) {
                    listOf("qwen3.5-omni-flash-realtime", "qwen3.5-omni-plus-realtime").forEach { model ->
                        Row(Modifier.fillMaxWidth().clickable { draft = draft.copy(qwenModel = model) }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = draft.qwenModel == model, onClick = { draft = draft.copy(qwenModel = model) })
                            Text(model, color = Color.White, fontSize = 13.sp)
                        }
                    }
                    Field("千问工作空间 WSS 地址", draft.qwenEndpoint) { draft = draft.copy(qwenEndpoint = it) }
                    Field("千问 API Key", draft.qwenApiKey, secret = true) { draft = draft.copy(qwenApiKey = it) }
                } else {
                    Text("豆包端到端实时语音", color = Color.White)
                    Field("豆包 App ID", draft.doubaoAppId) { draft = draft.copy(doubaoAppId = it) }
                    Field("豆包 Access Key", draft.doubaoAccessKey, secret = true) { draft = draft.copy(doubaoAccessKey = it) }
                    Field("豆包 App Key", draft.doubaoAppKey, secret = true) { draft = draft.copy(doubaoAppKey = it) }
                }
                Text("复杂任务由 AI 大脑当前选择的 LLM 执行。千问实时模型支持任务调用；豆包仅在协议支持的范围内处理。", color = AppColors.Secondary, fontSize = 12.sp)
            }
            VoiceMode.Traditional -> {
                SectionTitle("语音识别")
                TraditionalAsr.entries.forEach { asr ->
                    val enabled = asr != TraditionalAsr.SenseVoice || SenseVoiceAsr.supported()
                    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { draft = draft.copy(traditionalAsr = asr) },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = draft.traditionalAsr == asr,
                            onClick = { draft = draft.copy(traditionalAsr = asr) }, enabled = enabled)
                        Text(asr.label, color = if (enabled) Color.White else AppColors.Secondary)
                    }
                }
                if (draft.traditionalAsr == TraditionalAsr.SenseVoice) {
                    Text(if (modelInstalled) "SenseVoiceSmall INT8 已下载，可以离线识别"
                        else "模型约 239 MB，首次使用前需下载；不包含在安装包中",
                        color = AppColors.Secondary, fontSize = 12.sp)
                    if (modelBusy) Text("模型处理中 $modelProgress%", color = AppColors.Amber)
                    modelError?.let { Text(it, color = AppColors.Amber, fontSize = 12.sp) }
                    Button(onClick = {
                        modelBusy = true; modelError = null
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
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) { modelError = error.message ?: "模型操作失败" }
                            finally { modelInstalled = SenseVoiceAsr.installed(); modelBusy = false }
                        }
                    }, enabled = !modelBusy) { Text(if (modelInstalled) "删除本地模型" else "下载 SenseVoice 模型") }
                    Text("本地识别不可用时自动回退 MiMo 在线识别。", color = AppColors.Secondary, fontSize = 12.sp)
                }
                SectionTitle("MiMo 在线语音")
                Text("MiMo 提供语音合成，也用于本地识别失败时回退", color = AppColors.Secondary, fontSize = 12.sp)
                Field("MiMo API Key", draft.mimoApiKey, secret = true) { draft = draft.copy(mimoApiKey = it) }
                Field("MiMo API Base URL", draft.mimoBaseUrl) { draft = draft.copy(mimoBaseUrl = it) }
                Field("音色（例如 Chloe）", draft.mimoVoice) { draft = draft.copy(mimoVoice = it) }
            }
            VoiceMode.Native -> Text("使用设备系统的语音识别与朗读；无需配置语音模型 Key。", color = AppColors.Secondary)
        }
        Text("AI 大脑中的模型仍负责传统/系统模式的推理，也负责实时模式收到的委托任务。", color = AppColors.Secondary, fontSize = 12.sp)
        Button(onClick = { VoicePreferencesStore.save(draft); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("保存语音设置") }
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }

@Composable private fun Field(label: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}
