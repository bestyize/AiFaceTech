package com.aifacemate.face.voice.realtime

import kotlinx.coroutines.flow.Flow
import com.aifacemate.face.voice.realtime.provider.doubao.DoubaoRealtimeSession
import com.aifacemate.face.voice.realtime.provider.qwen.QwenRealtimeSession

/** One input format across providers: mono, signed 16-bit little-endian PCM at 16 kHz. */
data class RealtimeAudio(val bytes: ByteArray)

sealed interface RealtimeConfig {
    /** Access keys must be supplied at runtime, never committed to source control. */
    data class Doubao(
        val appId: String,
        val accessKey: String,
        val appKey: String,
        val sessionId: String,
        val systemRole: String = "",
    ) : RealtimeConfig

    /** Workspace-specific endpoint for the selected region, e.g. wss://ID.cn-beijing.maas.aliyuncs.com. */
    data class Qwen(
        val apiKey: String,
        val workspaceEndpoint: String,
        val model: String = "qwen3.5-omni-flash-realtime",
        val voice: String = "Tina",
        val instructions: String = "",
    ) : RealtimeConfig
}

sealed interface RealtimeEvent {
    data object Ready : RealtimeEvent
    data object Listening : RealtimeEvent
    data object Thinking : RealtimeEvent
    data object Speaking : RealtimeEvent
    /** Immediately discard queued speaker audio on this event. */
    data object InterruptPlayback : RealtimeEvent
    data class InputText(val text: String, val final: Boolean) : RealtimeEvent
    /** Recognition can fail for one utterance while the conversation remains connected. */
    data class TranscriptionFailed(val message: String) : RealtimeEvent
    data class OutputText(val text: String, val final: Boolean) : RealtimeEvent
    /** Output format is provider-specific; pass format and sample rate to the audio decoder. */
    data class OutputAudio(val bytes: ByteArray, val format: String, val sampleRate: Int) : RealtimeEvent
    /** The server finished generating audio; switch back to Listening after the local player drains. */
    data object OutputComplete : RealtimeEvent
    data class Failure(val message: String) : RealtimeEvent
    data object Closed : RealtimeEvent
}

interface RealtimeSession {
    /** Single collector. Subscribe before sending audio to avoid a delayed UI. */
    val events: Flow<RealtimeEvent>
    suspend fun sendAudio(audio: RealtimeAudio)
    /** Cancel generated output where supported and stop local playback immediately. */
    suspend fun interrupt()
    suspend fun close()
}

/** Creates a fresh session each time; ASR/TTS one-shot clients remain independent. */
class RealtimeConversationEngine {
    suspend fun connect(config: RealtimeConfig): RealtimeSession = when (config) {
        is RealtimeConfig.Doubao -> DoubaoRealtimeSession.connect(config)
        is RealtimeConfig.Qwen -> QwenRealtimeSession.connect(config)
    }
}
