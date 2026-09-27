package com.aifacemate.face

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aifacemate.face.chat.ChatClient
import com.aifacemate.face.chat.ChatConfig
import com.aifacemate.face.chat.ChatMessage
import com.aifacemate.face.chat.ChatResult
import com.aifacemate.face.data.SettingsStore
import com.aifacemate.face.feature.aibrain.data.AiBrainSettings
import com.aifacemate.face.feature.aibrain.defaultAiProviders
import com.aifacemate.face.network.NetworkClient
import com.aifacemate.face.theme.AppColors
import com.aifacemate.face.voice.VoiceCaptureResult
import com.aifacemate.face.voice.VoiceClip
import com.aifacemate.face.voice.VoicePlaybackResult
import com.aifacemate.face.voice.createNativeSpeechEngine
import com.aifacemate.face.voice.createRealtimeAudioDevice
import com.aifacemate.face.voice.createVoiceService
import com.aifacemate.face.voice.nativeSpeechAvailable
import com.aifacemate.face.voice.realtime.RealtimeConfig
import com.aifacemate.face.voice.realtime.RealtimeConversationEngine
import com.aifacemate.face.voice.realtime.RealtimeEvent
import com.aifacemate.face.voice.realtime.RealtimeSession
import com.aifacemate.face.voice.realtimeAudioAvailable
import com.aifacemate.face.voice.asr.client.AsrClient
import com.aifacemate.face.voice.asr.model.AsrRequest
import com.aifacemate.face.voice.asr.model.AsrResult
import com.aifacemate.face.voice.tts.client.TtsClient
import com.aifacemate.face.voice.tts.model.TtsRequest
import com.aifacemate.face.voice.tts.model.TtsResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Composable
fun VoiceConversationOverlay(modifier: Modifier = Modifier, onClose: () -> Unit) {
    ConversationPanel(modifier.fillMaxWidth().background(Color(0xF5181B23), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        .padding(16.dp), onClose)
}

@Composable
fun ChatScreen(onBack: () -> Unit) {
    ConversationPanel(Modifier.fillMaxSize().background(AppColors.Background).padding(20.dp), onBack)
}

@Composable
private fun ConversationPanel(modifier: Modifier, onClose: () -> Unit) {
    val preferences = remember { VoicePreferencesStore.load().forDevice() }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val voice = remember { createVoiceService() }
    val native = remember { if (nativeSpeechAvailable()) createNativeSpeechEngine() else null }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("可以开始对话") }
    var busy by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    var realtimeJob by remember { mutableStateOf<Job?>(null) }
    var turnJob by remember { mutableStateOf<Job?>(null) }

    fun show(role: String, text: String) { if (text.isNotBlank()) messages.add(ChatMessage(role, text)) }

    suspend fun askBrain(text: String): String {
        val providerId = AiBrainSettings.providerId() ?: "deepseek"
        val provider = defaultAiProviders.firstOrNull { it.id == providerId }
        val secret = AiBrainSettings.savedSecret(providerId) ?: error("请先在 AI 大脑保存模型的 API Key 和 Base URL")
        val model = AiBrainSettings.modelId(providerId)?.takeIf(String::isNotBlank)
            ?: provider?.models?.firstOrNull()?.id ?: error("请先在 AI 大脑选择模型")
        val previous = messages.toList()
        val context = if (previous.lastOrNull() == ChatMessage("user", text)) previous
            else previous + ChatMessage("user", text)
        return when (val result = ChatClient(NetworkClient()).send(ChatConfig(secret.baseUrl, secret.apiKey, model), context)) {
            is ChatResult.Success -> result.message.content
            is ChatResult.Failure -> error(result.message)
        }
    }

    fun submitText() {
        val question = draft.trim()
        if (question.isEmpty() || busy) return
        busy = true
        draft = ""; show("user", question)
        turnJob = scope.launch {
            status = "AI 大脑正在思考"
            try { show("assistant", askBrain(question)); status = "可以继续对话" }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { status = error.message ?: "对话失败" }
            finally { busy = false }
        }
    }

    fun startSingleTurn() {
        if (busy) return
        busy = true
        turnJob = scope.launch {
            try {
                val input = if (preferences.mode == VoiceMode.Native) {
                    check(nativeSpeechAvailable()) { "系统语音服务不可用" }
                    status = "系统正在聆听"
                    checkNotNull(native).recognize()
                } else {
                    check(preferences.mimoApiKey.isNotBlank()) { "请先在声音设置中填写 MiMo API Key" }
                    status = "正在录音（最多 6 秒）"
                    val recorded = voice.record()
                    val clip = when (recorded) {
                        is VoiceCaptureResult.Success -> recorded.clip
                        is VoiceCaptureResult.Failure -> error(recorded.reason)
                    }
                    status = "MiMo 正在识别"
                    val request = AsrRequest(apiKey = preferences.mimoApiKey, baseUrl = preferences.mimoBaseUrl,
                        audio = clip.bytes, mimeType = clip.mimeType)
                    when (val recognized = AsrClient(NetworkClient()).transcribe(request)) {
                        is AsrResult.Success -> recognized.text
                        is AsrResult.Failure -> error(recognized.message)
                    }
                }.trim()
                check(input.isNotEmpty()) { "没有识别到声音" }
                show("user", input)
                status = "AI 大脑正在思考"
                val answer = askBrain(input)
                show("assistant", answer)
                status = "正在播放回复"
                if (preferences.mode == VoiceMode.Native) {
                    checkNotNull(native).speak(answer)
                } else {
                    val response = TtsClient(NetworkClient()).synthesize(TtsRequest(apiKey = preferences.mimoApiKey,
                        baseUrl = preferences.mimoBaseUrl, text = answer, voice = preferences.mimoVoice, format = "wav"))
                    val audio = when (response) {
                        is TtsResult.Success -> response.audio
                        is TtsResult.Failure -> error(response.message)
                    }
                    when (val result = voice.play(VoiceClip(audio, "audio/wav"))) {
                        VoicePlaybackResult.Complete -> Unit
                        is VoicePlaybackResult.Failure -> error(result.reason)
                    }
                }
                status = "可以继续对话"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { status = error.message ?: "语音对话失败" }
            finally { busy = false }
        }
    }

    fun startRealtime() {
        if (live || !realtimeAudioAvailable()) return
        realtimeJob = scope.launch {
            live = true; status = "正在连接实时语音"
            var session: RealtimeSession? = null
            val device = try { createRealtimeAudioDevice() }
            catch (error: Exception) { status = error.message ?: "音频设备不可用"; live = false; return@launch }
            var capture: Job? = null
            try {
                val config = when (preferences.provider) {
                    RealtimeProvider.Qwen -> RealtimeConfig.Qwen(
                        apiKey = preferences.qwenApiKey, workspaceEndpoint = preferences.qwenEndpoint,
                        model = preferences.qwenModel, enableDelegation = true,
                        instructions = "日常对话直接回答。需要复杂推理或执行任务时使用 delegate_to_brain。",
                    )
                    RealtimeProvider.Doubao -> RealtimeConfig.Doubao(
                        appId = preferences.doubaoAppId, accessKey = preferences.doubaoAccessKey,
                        appKey = preferences.doubaoAppKey, sessionId = randomSessionId(),
                    )
                }
                val connected = RealtimeConversationEngine().connect(config)
                session = connected
                capture = launch {
                    try { device.capture { connected.sendAudio(com.aifacemate.face.voice.realtime.RealtimeAudio(it)) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        status = error.message ?: "麦克风采集失败"
                        throw error
                    }
                }
                status = "实时聆听中"
                connected.events.collect { event ->
                    when (event) {
                        is RealtimeEvent.InputText -> if (event.final) show("user", event.text)
                        is RealtimeEvent.OutputText -> if (event.final) show("assistant", event.text)
                        is RealtimeEvent.OutputAudio -> device.play(event)
                        is RealtimeEvent.ToolCall -> {
                            status = "AI 大脑正在处理任务"
                            val answer = try { askBrain(event.task) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) { "任务执行失败：${error.message}" }
                            connected.submitToolResult(event.callId, answer)
                        }
                        RealtimeEvent.InterruptPlayback -> { device.interrupt(); status = "实时聆听中" }
                        RealtimeEvent.OutputComplete -> { device.drain(); status = "实时聆听中" }
                        RealtimeEvent.Speaking -> status = "正在回答"
                        is RealtimeEvent.Failure -> error(event.message)
                        RealtimeEvent.Closed -> error("实时连接已关闭")
                        else -> Unit
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { status = error.message ?: "实时对话失败" }
            finally {
                runCatching { device.close() }
                withContext(NonCancellable) {
                    withTimeoutOrNull(2_000) { capture?.cancelAndJoin() }
                    runCatching { session?.close() }
                }
                live = false
            }
        }
    }

    val currentRealtime = rememberUpdatedState(realtimeJob)
    val currentTurn = rememberUpdatedState(turnJob)
    DisposableEffect(Unit) {
        onDispose { currentRealtime.value?.cancel(); currentTurn.value?.cancel(); voice.stop(); native?.stop() }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("对话 · ${preferences.mode.label}", color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("关闭") }
        }
        Text(status, color = AppColors.Amber)
        Column(Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 180.dp).verticalScroll(rememberScrollState())) {
            messages.takeLast(8).forEach { msg ->
                Text("${if (msg.role == "user") "我" else "小星"}：${msg.content}", color = Color.White, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft, { draft = it }, label = { Text("输入文字") }, modifier = Modifier.weight(1f), singleLine = true)
            Button(onClick = ::submitText, enabled = draft.isNotBlank() && !busy) { Text("发送") }
        }
        if (preferences.mode == VoiceMode.Realtime) {
            Button(onClick = { if (live) realtimeJob?.cancel() else startRealtime() }, enabled = realtimeAudioAvailable()) {
                Text(if (live) "停止实时对话" else "开始实时对话")
            }
        } else {
            Button(onClick = ::startSingleTurn, enabled = !busy && (preferences.mode != VoiceMode.Native || nativeSpeechAvailable())) {
                Text(if (busy) "处理中" else "说一句")
            }
        }
    }
}

private fun randomSessionId(): String {
    fun hex(count: Int) = (1..count).joinToString("") { Random.nextInt(16).toString(16) }
    return "${hex(8)}-${hex(4)}-4${hex(3)}-${(8 + Random.nextInt(4)).toString(16)}${hex(3)}-${hex(12)}"
}
