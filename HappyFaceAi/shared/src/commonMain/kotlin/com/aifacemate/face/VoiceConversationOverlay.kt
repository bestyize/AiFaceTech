package com.aifacemate.face

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aifacemate.face.chat.ChatClient
import com.aifacemate.face.chat.ChatConfig
import com.aifacemate.face.chat.ChatMessage
import com.aifacemate.face.chat.ChatResult
import com.aifacemate.face.feature.aibrain.data.AiBrainSettings
import com.aifacemate.face.feature.aibrain.defaultAiProviders
import com.aifacemate.face.network.NetworkClient
import com.aifacemate.face.voice.VoiceCaptureResult
import com.aifacemate.face.voice.SpeechPcmPlayer
import com.aifacemate.face.voice.createSpeechPcmPlayer
import com.aifacemate.face.voice.streamSpokenReply
import com.aifacemate.face.voice.createNativeSpeechEngine
import com.aifacemate.face.voice.createRealtimeAudioDevice
import com.aifacemate.face.voice.createVoiceService
import com.aifacemate.face.voice.systemAsrAvailable
import com.aifacemate.face.voice.systemTtsAvailable
import com.aifacemate.face.voice.realtime.RealtimeConfig
import com.aifacemate.face.voice.realtime.RealtimeConversationEngine
import com.aifacemate.face.voice.realtime.RealtimeEvent
import com.aifacemate.face.voice.realtime.RealtimeSession
import com.aifacemate.face.voice.realtimeAudioAvailable
import com.aifacemate.face.voice.asr.client.AsrClient
import com.aifacemate.face.voice.asr.client.SenseVoiceAsr
import com.aifacemate.face.voice.asr.model.AsrRequest
import com.aifacemate.face.voice.asr.model.AsrResult
import com.aifacemate.face.voice.tts.client.TtsClient
import com.aifacemate.face.voice.tts.client.PiperTts
import com.aifacemate.face.voice.tts.model.TtsRequest
import com.aifacemate.face.voice.tts.model.SpeechAudioChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.time.TimeSource
import com.aifacemate.face.voice.VoiceObservation
import com.aifacemate.face.voice.pcm16Activity
import com.aifacemate.face.behavior.proactive.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

enum class VoiceVisualState { Idle, Listening, Processing, Speaking }

private sealed interface ReplyPlayback {
    data class Audio(val chunk: SpeechAudioChunk) : ReplyPlayback
    data class SystemText(val text: String) : ReplyPlayback
}

@Composable
fun VoiceConversationOverlay(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onVisualStateChange: (VoiceVisualState) -> Unit = {},
    onUserText: (String) -> Unit = {},
    onSpeechObservation: (VoiceObservation) -> Unit = {},
    proactiveOpening: ProactiveOpening? = null,
    onProactiveNoResponse: () -> Unit = {},
) {
    ConversationPanel(modifier.fillMaxSize(), onClose, overlay = true, onVisualStateChange = onVisualStateChange,
        onUserText = onUserText, onSpeechObservation = onSpeechObservation,
        proactiveOpening = proactiveOpening, onProactiveNoResponse = onProactiveNoResponse)
}

@Composable
fun ChatScreen(onBack: () -> Unit) {
    ConversationPanel(Modifier.fillMaxSize(), onBack)
}

@Composable
private fun ConversationPanel(
    modifier: Modifier,
    onClose: () -> Unit,
    overlay: Boolean = false,
    onVisualStateChange: (VoiceVisualState) -> Unit = {},
    onUserText: (String) -> Unit = {},
    onSpeechObservation: (VoiceObservation) -> Unit = {},
    proactiveOpening: ProactiveOpening? = null,
    onProactiveNoResponse: () -> Unit = {},
) {
    val currentSpeechObservation = rememberUpdatedState(onSpeechObservation)
    val observationsActive = remember { mutableStateOf(true) }
    val currentUserText = rememberUpdatedState(onUserText)
    val preferences = remember { VoicePreferencesStore.load().forDevice() }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val voice = remember { createVoiceService() }
    val native = remember { if (systemAsrAvailable() || systemTtsAvailable()) createNativeSpeechEngine() else null }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("可以开始对话") }
    var busy by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    var realtimeJob by remember { mutableStateOf<Job?>(null) }
    var turnJob by remember { mutableStateOf<Job?>(null) }
    var detailsExpanded by remember { mutableStateOf(false) }
    var facePhase by remember { mutableStateOf(VoiceFacePhase.Idle) }
    var notice by remember { mutableStateOf<String?>(null) }
    var stoppingRealtime by remember { mutableStateOf(false) }
    var replyStreaming by remember { mutableStateOf(false) }
    var pcmPlayer by remember { mutableStateOf<SpeechPcmPlayer?>(null) }
    val listeningClock = remember { TimeSource.Monotonic.markNow() }
    var automaticFirstListen by remember { mutableStateOf(false) }
    var automaticWindow by remember { mutableStateOf<ListeningWindow?>(null) }
    fun observe(observation: VoiceObservation) {
        if (facePhase == VoiceFacePhase.Listening && observationsActive.value) {
            val time = listeningClock.elapsedNow().inWholeMilliseconds
            when (observation) {
                is VoiceObservation.Level -> automaticWindow?.activity(observation.value, time)
                is VoiceObservation.PartialText -> automaticWindow?.text(observation.text, time)
                VoiceObservation.Cancelled -> Unit
            }
        }
        if (observationsActive.value && overlay && preferences.keepFaceVisible && facePhase == VoiceFacePhase.Listening)
            currentSpeechObservation.value(observation)
    }
    fun cancelFeedback() {
        automaticFirstListen = false; automaticWindow = null
        if (observationsActive.value && overlay && preferences.keepFaceVisible) currentSpeechObservation.value(VoiceObservation.Cancelled)
    }
    LaunchedEffect(voice, native) {
        launch { voice.observations.collect { observe(it) } }
        native?.let { launch { it.observations.collect { observe(it) } } }
    }

    suspend fun synthesizeSentence(sentence: String, emit: suspend (ReplyPlayback) -> Unit) {
        when (preferences.traditionalTts) {
            TraditionalTts.System -> {
                check(systemTtsAvailable()) { "系统语音合成不可用" }
                emit(ReplyPlayback.SystemText(sentence))
            }
            TraditionalTts.Piper -> PiperTts.stream(sentence) { emit(ReplyPlayback.Audio(it)) }
            TraditionalTts.Mimo -> TtsClient(NetworkClient()).stream(TtsRequest(apiKey = preferences.mimoApiKey,
                baseUrl = preferences.mimoBaseUrl, text = sentence, voice = preferences.mimoVoice)) {
                emit(ReplyPlayback.Audio(it))
            }
        }
    }

    suspend fun speakOpening(text: String) {
        var player: SpeechPcmPlayer? = null
        var spoken = false
        try {
            val result = streamSpokenReply<ReplyPlayback>(
                generate = { it(text) },
                synthesize = ::synthesizeSentence,
                play = { part -> when (part) {
                    is ReplyPlayback.SystemText -> checkNotNull(native).speak(part.text)
                    is ReplyPlayback.Audio -> {
                        val output = player ?: createSpeechPcmPlayer().also { player = it; pcmPlayer = it }
                        output.write(part.chunk)
                    }
                } },
                drain = { player?.drain() },
                onSpeechStart = { spoken = true; facePhase = VoiceFacePhase.Speaking; status = "正在主动和你打招呼" },
            )
            result.speechFailure?.let { throw it }
            check(spoken) { "主动朗读没有产生音频，请检查声音配置" }
        } finally { player?.close(); pcmPlayer = null }
    }

    suspend fun answerWithSpeech() {
        replyStreaming = true
        status = "AI 大脑正在思考"
        val providerId = AiBrainSettings.providerId() ?: "deepseek"
        val provider = defaultAiProviders.firstOrNull { it.id == providerId }
        val secret = AiBrainSettings.savedSecret(providerId) ?: error("请先配置 AI 大脑")
        val model = AiBrainSettings.modelId(providerId)?.takeIf(String::isNotBlank)
            ?: provider?.models?.firstOrNull()?.id ?: error("请先在 AI 大脑选择模型")
        val context = messages.toList() // Capture history before adding the streaming assistant row.
        val index = messages.size
        messages.add(ChatMessage("assistant", ""))
        val brain = ChatClient(NetworkClient())
        val ttsClient = TtsClient(NetworkClient())
        var player: SpeechPcmPlayer? = null
        suspend fun updateText(text: String) = withContext(Dispatchers.Main) {
            messages[index] = ChatMessage("assistant", text)
        }
        suspend fun generate(onDelta: suspend (String) -> Unit) {
            when (val result = brain.stream(ChatConfig(secret.baseUrl, secret.apiKey, model), context, onDelta)) {
                is ChatResult.Success -> Unit
                is ChatResult.Failure -> error(result.message)
            }
        }
        try {
            if (preferences.traditionalTts == TraditionalTts.Mimo && preferences.mimoApiKey.isBlank()) {
                val text = StringBuilder()
                generate { text.append(it); updateText(text.toString()) }
                status = "已生成文字回复；配置 MiMo 或选择系统 / Piper 本地语音合成后可朗读"
                notice = "查看回复"
                return
            }
            val result = streamSpokenReply<ReplyPlayback>(
                generate = ::generate,
                onText = ::updateText,
                synthesize = { sentence, emit ->
                    when (preferences.traditionalTts) {
                        TraditionalTts.System -> {
                            check(systemTtsAvailable()) { "系统语音合成不可用" }
                            emit(ReplyPlayback.SystemText(sentence))
                        }
                        TraditionalTts.Piper -> PiperTts.stream(sentence) { emit(ReplyPlayback.Audio(it)) }
                        TraditionalTts.Mimo -> ttsClient.stream(TtsRequest(apiKey = preferences.mimoApiKey,
                            baseUrl = preferences.mimoBaseUrl, text = sentence, voice = preferences.mimoVoice)) {
                            emit(ReplyPlayback.Audio(it))
                        }
                    }
                },
                play = { part ->
                    when (part) {
                        is ReplyPlayback.SystemText -> checkNotNull(native).speak(part.text)
                        is ReplyPlayback.Audio -> {
                            val output = player ?: createSpeechPcmPlayer().also { player = it; pcmPlayer = it }
                            output.write(part.chunk)
                        }
                    }
                },
                drain = { player?.drain() },
                onSpeechStart = { status = "正在播放回复"; facePhase = VoiceFacePhase.Speaking },
                onSpeechFailure = { failure ->
                    player?.close(); native?.stop()
                    facePhase = VoiceFacePhase.Processing
                    status = "朗读失败，正在接收文字：${failure.message.orEmpty()}"
                },
            )
            val speechFailure = result.speechFailure
            if (speechFailure != null) {
                status = "朗读失败：${speechFailure.message.orEmpty()}；文字回复已保留"
                notice = "查看回复"
            } else status = "可以继续对话"
        } finally {
            player?.close(); pcmPlayer = null
            if (messages.getOrNull(index)?.content?.isBlank() == true) messages.removeAt(index)
            replyStreaming = false
        }
    }

    fun show(role: String, text: String) {
        if (text.isNotBlank()) {
            messages.add(ChatMessage(role, text))
            if (role == "user") automaticFirstListen = false
            if (role == "user" && observationsActive.value && overlay && preferences.keepFaceVisible) currentUserText.value(text)
        }
    }

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
        notice = null
        if (!live) facePhase = VoiceFacePhase.Processing
        draft = ""; show("user", question)
        turnJob = scope.launch {
            status = "AI 大脑正在思考"
            try { show("assistant", askBrain(question)); status = "可以继续对话" }
            catch (cancelled: CancellationException) { cancelFeedback(); throw cancelled }
            catch (error: Exception) { cancelFeedback(); status = error.message ?: "对话失败"; notice = "查看提示" }
            finally { busy = false; if (!live) facePhase = VoiceFacePhase.Idle }
        }
    }

    fun startSingleTurn() {
        if (capturing) {
            automaticFirstListen = false
            capturing = false
            facePhase = VoiceFacePhase.Processing
            status = "正在结束收音"
            if (preferences.mode == VoiceMode.Traditional && preferences.traditionalAsr == TraditionalAsr.System) native?.finishRecognition()
            else voice.finishRecording()
            return
        }
        if (busy) return
        busy = true
        capturing = true
        notice = null
        facePhase = VoiceFacePhase.Listening
        turnJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val input = if (preferences.mode == VoiceMode.Traditional && preferences.traditionalAsr == TraditionalAsr.System) {
                    check(systemAsrAvailable()) { "系统语音识别不可用" }
                    status = "系统正在聆听，点结束说话提交"
                    checkNotNull(native).recognize().also { capturing = false; facePhase = VoiceFacePhase.Processing }
                } else {
                    if (preferences.traditionalAsr == TraditionalAsr.Mimo) {
                        check(preferences.mimoApiKey.isNotBlank()) { "请先在声音设置中填写 MiMo 在线识别的 API Key" }
                    }
                    status = "正在录音，点结束说话提交（最长 60 秒）"
                    val recorded = voice.record(maxDurationMs = if (automaticFirstListen) 30_000 else 60_000)
                    capturing = false
                    facePhase = VoiceFacePhase.Processing
                    val clip = when (recorded) {
                        is VoiceCaptureResult.Success -> recorded.clip
                        is VoiceCaptureResult.Failure -> error(recorded.reason)
                    }
                    suspend fun mimoRecognize(): String {
                        status = "MiMo 正在识别"
                        val request = AsrRequest(apiKey = preferences.mimoApiKey, baseUrl = preferences.mimoBaseUrl,
                            audio = clip.bytes, mimeType = clip.mimeType)
                        return when (val recognized = AsrClient(NetworkClient()).transcribe(request)) {
                            is AsrResult.Success -> recognized.text
                            is AsrResult.Failure -> error(recognized.message)
                        }
                    }
                    recognizeTraditionalSpeech(
                        asr = preferences.traditionalAsr,
                        hasOnlineKey = preferences.mimoApiKey.isNotBlank(),
                        local = {
                            status = "SenseVoice 本地识别中"
                            SenseVoiceAsr.transcribe(clip.bytes)
                        },
                        online = { mimoRecognize() },
                        onFallback = { status = "本地识别失败，正在使用 MiMo" },
                    )
                }.trim()
                check(input.isNotEmpty()) { "没有识别到声音" }
                show("user", input)
                if (AiBrainSettings.savedSecret(AiBrainSettings.providerId() ?: "deepseek") == null) {
                    status = "已识别文字；配置 AI 大脑后可生成回答"
                    notice = "查看文字"
                    return@launch
                }
                answerWithSpeech()
            } catch (cancelled: CancellationException) { cancelFeedback(); throw cancelled }
            catch (error: Exception) { cancelFeedback(); status = error.message ?: "语音对话失败"; notice = "查看提示" }
            finally { capturing = false; busy = false; replyStreaming = false; facePhase = VoiceFacePhase.Idle }
        }
    }

    fun startRealtime() {
        if (live || !realtimeAudioAvailable()) return
        realtimeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            live = true; status = "正在连接实时语音"; notice = null; facePhase = VoiceFacePhase.Processing
            var session: RealtimeSession? = null
            val device = try { createRealtimeAudioDevice() }
            catch (error: Exception) {
                status = error.message ?: "音频设备不可用"; notice = "查看提示"
                live = false; facePhase = VoiceFacePhase.Idle; return@launch
            }
            var capture: Job? = null
            try {
                val config = when (preferences.provider) {
                    RealtimeProvider.Qwen -> RealtimeConfig.Qwen(
                        apiKey = preferences.qwenApiKey, workspaceEndpoint = preferences.qwenEndpoint,
                        model = preferences.qwenModel, enableDelegation = true,
                        instructions = "日常对话直接回答。需要复杂推理或执行任务时使用 delegate_to_brain。" +
                            proactiveOpening?.let { "你刚刚已经向用户提问：${it.text}。现在等待并回应用户的回答，不要重复开场问题。" }.orEmpty(),
                    )
                    RealtimeProvider.Doubao -> RealtimeConfig.Doubao(
                        appId = preferences.doubaoAppId, accessKey = preferences.doubaoAccessKey,
                        appKey = preferences.doubaoAppKey, sessionId = randomSessionId(),
                        systemRole = proactiveOpening?.let { "你是陪伴机器人。你刚刚已经问过：${it.text}。现在回应用户的回答，不要重复开场问题。" }.orEmpty(),
                    )
                }
                val connected = RealtimeConversationEngine().connect(config)
                session = connected
                capture = launch {
                    var activityAt = TimeSource.Monotonic.markNow()
                    try { device.capture {
                        connected.sendAudio(com.aifacemate.face.voice.realtime.RealtimeAudio(it))
                        if (activityAt.elapsedNow().inWholeMilliseconds >= 80) {
                            val level = pcm16Activity(it)
                            withContext(Dispatchers.Main) { observe(VoiceObservation.Level(level)) }
                            activityAt = TimeSource.Monotonic.markNow()
                        }
                    } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        status = error.message ?: "麦克风采集失败"
                        throw error
                    }
                }
                status = "实时聆听中"
                facePhase = VoiceFacePhase.Listening
                connected.events.collect { event ->
                    when (event) {
                        is RealtimeEvent.InputText -> if (event.final) show("user", event.text)
                            else observe(VoiceObservation.PartialText(event.text))
                        is RealtimeEvent.OutputText -> if (event.final) show("assistant", event.text)
                        is RealtimeEvent.OutputAudio -> device.play(event)
                        is RealtimeEvent.ToolCall -> {
                            status = "AI 大脑正在处理任务"
                            facePhase = VoiceFacePhase.Processing
                            val answer = try { askBrain(event.task) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) { "任务执行失败：${error.message}" }
                            connected.submitToolResult(event.callId, answer)
                        }
                        RealtimeEvent.InterruptPlayback -> { device.interrupt(); status = "实时聆听中"; facePhase = VoiceFacePhase.Listening }
                        RealtimeEvent.OutputComplete -> { device.drain(); status = "实时聆听中"; facePhase = VoiceFacePhase.Listening }
                        RealtimeEvent.Speaking -> { status = "正在回答"; facePhase = VoiceFacePhase.Speaking }
                        is RealtimeEvent.Failure -> error(event.message)
                        RealtimeEvent.Closed -> error("实时连接已关闭")
                        else -> Unit
                    }
                }
            } catch (cancelled: CancellationException) { cancelFeedback(); status = "对话已结束"; throw cancelled }
            catch (error: Exception) { cancelFeedback(); status = error.message ?: "实时对话失败"; notice = "查看提示" }
            finally {
                runCatching { device.close() }
                withContext(NonCancellable) {
                    withTimeoutOrNull(2_000) { capture?.cancelAndJoin() }
                    runCatching { session?.close() }
                }
                live = false; stoppingRealtime = false; facePhase = VoiceFacePhase.Idle
            }
        }
    }

    fun toggleVoice() {
        if (replyStreaming) {
            cancelFeedback(); turnJob?.cancel(); pcmPlayer?.close(); voice.stop(); native?.stop()
            status = "已停止回复"
            return
        }
        if (preferences.mode == VoiceMode.Realtime) {
            if (live) {
                if (!stoppingRealtime) {
                    stoppingRealtime = true; status = "正在结束对话"; facePhase = VoiceFacePhase.Processing
                    cancelFeedback()
                    realtimeJob?.cancel()
                }
            } else startRealtime()
        } else if (replyStreaming || facePhase == VoiceFacePhase.Speaking) {
            cancelFeedback()
            turnJob?.cancel(); pcmPlayer?.close(); voice.stop(); native?.stop(); status = "已停止回复"
        } else startSingleTurn()
    }

    LaunchedEffect(proactiveOpening?.id) {
        val opening = proactiveOpening ?: return@LaunchedEffect
        if (!overlay) return@LaunchedEffect
        busy = true; replyStreaming = true; notice = null; facePhase = VoiceFacePhase.Processing
        turnJob = currentCoroutineContext()[Job]
        var delivered = false
        try {
            show("assistant", opening.text)
            status = "正在准备主动提问"
            println("FaceProactive openingStarted id=${opening.id}")
            speakOpening(opening.text)
            println("FaceProactive openingPlayed id=${opening.id}")
            delivered = true
        } catch (cancelled: CancellationException) { cancelFeedback(); throw cancelled }
        catch (failure: Exception) { cancelFeedback(); status = failure.message ?: "主动朗读失败"; notice = "查看提示" }
        finally { busy = false; replyStreaming = false; facePhase = VoiceFacePhase.Idle; turnJob = null }
        if (delivered && currentCoroutineContext().isActive && observationsActive.value) {
            automaticFirstListen = true
            println("FaceProactive beginListening mode=${preferences.mode}")
            if (preferences.mode == VoiceMode.Realtime) startRealtime() else startSingleTurn()
        }
    }

    LaunchedEffect(automaticFirstListen, capturing, live, facePhase) {
        if (!automaticFirstListen || (!capturing && !live) || facePhase != VoiceFacePhase.Listening) {
            automaticWindow = null; return@LaunchedEffect
        }
        val window = ListeningWindow(listeningClock.elapsedNow().inWholeMilliseconds, preferences.mode == VoiceMode.Traditional)
        automaticWindow = window
        try {
            while (automaticFirstListen) {
                delay(100)
                when (window.decision(listeningClock.elapsedNow().inWholeMilliseconds)) {
                    ListeningDecision.Continue -> Unit
                    ListeningDecision.Submit -> { automaticFirstListen = false; startSingleTurn(); break }
                    ListeningDecision.NoResponse -> {
                        automaticFirstListen = false; cancelFeedback()
                        turnJob?.cancel(); realtimeJob?.cancel(); voice.stop(); native?.stop()
                        onProactiveNoResponse(); onClose(); break
                    }
                }
            }
        } finally { if (automaticWindow === window) automaticWindow = null }
    }

    val currentRealtime = rememberUpdatedState(realtimeJob)
    val currentTurn = rememberUpdatedState(turnJob)
    val currentPcmPlayer = rememberUpdatedState(pcmPlayer)
    val currentVisualCallback = rememberUpdatedState(onVisualStateChange)
    val visualState = when (facePhase) {
        VoiceFacePhase.Speaking -> VoiceVisualState.Speaking
        VoiceFacePhase.Listening -> VoiceVisualState.Listening
        VoiceFacePhase.Processing -> VoiceVisualState.Processing
        else -> VoiceVisualState.Idle
    }
    val voiceEnabled = if (preferences.mode == VoiceMode.Realtime) realtimeAudioAvailable() && !stoppingRealtime
        else capturing || replyStreaming || facePhase == VoiceFacePhase.Speaking ||
            (!busy && (preferences.traditionalAsr != TraditionalAsr.System || systemAsrAvailable()))
    LaunchedEffect(visualState) { if (overlay && preferences.keepFaceVisible) currentVisualCallback.value(visualState) }
    DisposableEffect(Unit) {
        onDispose {
            observationsActive.value = false
            currentVisualCallback.value(VoiceVisualState.Idle)
            currentRealtime.value?.cancel(); currentTurn.value?.cancel(); voice.stop(); native?.stop()
            currentPcmPlayer.value?.close()
        }
    }

    @Composable
    fun FullPanel(panelModifier: Modifier) = ConversationScreenContent(
        state = ConversationScreenState(
            mode = preferences.mode, messages = messages.toList(), draft = draft, status = status,
            busy = busy, voiceActive = live || capturing || replyStreaming || facePhase == VoiceFacePhase.Speaking,
            voiceEnabled = voiceEnabled,
            voiceActionLabel = if (replyStreaming) "停止回复" else null,
        ),
        onDraftChange = { draft = it }, onSend = ::submitText, onVoice = ::toggleVoice, onClose = onClose,
        modifier = panelModifier,
        onCollapse = if (overlay && preferences.keepFaceVisible) ({ detailsExpanded = false }) else null,
    )

    if (!overlay) {
        FullPanel(modifier)
    } else if (preferences.keepFaceVisible && !detailsExpanded) {
        VoiceFaceControl(
            state = VoiceFaceControlState(facePhase, status, preferences.mode == VoiceMode.Realtime, voiceEnabled, notice,
                replyActive = replyStreaming),
            onVoice = ::toggleVoice,
            onOpenConversation = { detailsExpanded = true },
            modifier = modifier,
        )
    } else {
        FullPanel(modifier)
    }
}

private fun randomSessionId(): String {
    fun hex(count: Int) = (1..count).joinToString("") { Random.nextInt(16).toString(16) }
    return "${hex(8)}-${hex(4)}-4${hex(3)}-${(8 + Random.nextInt(4)).toString(16)}${hex(3)}-${hex(12)}"
}
