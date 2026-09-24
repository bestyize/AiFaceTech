package com.aifacemate.face.voice.realtime.provider.qwen

import com.aifacemate.face.voice.realtime.RealtimeConfig
import com.aifacemate.face.voice.realtime.RealtimeEvent
import com.aifacemate.face.voice.realtime.RealtimeSession
import com.aifacemate.face.voice.realtime.internal.SocketSession
import com.aifacemate.face.voice.realtime.internal.openSocket

import io.ktor.client.HttpClient
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal class QwenRealtimeSession private constructor(
    private val config: RealtimeConfig.Qwen,
    client: HttpClient,
    socket: WebSocketSession,
) : SocketSession(client, socket) {
    private var responding = false

    override suspend fun open() {
        send(Frame.Text(buildJsonObject {
            put("type", "session.update")
            putJsonObject("session") {
                put("model", config.model)
                put("voice", config.voice)
                put("instructions", config.instructions)
                putJsonArray("modalities") { add(kotlinx.serialization.json.JsonPrimitive("text")); add(kotlinx.serialization.json.JsonPrimitive("audio")) }
                putJsonObject("audio") {
                    putJsonObject("input") { putJsonObject("format") { put("type", "pcm"); put("sample_rate", 16000) } }
                    putJsonObject("output") { putJsonObject("format") { put("type", "pcm"); put("sample_rate", 24000) } }
                }
                putJsonObject("turn_detection") { put("type", "server_vad") }
            }
        }.toString()))
    }

    override fun audioFrame(bytes: ByteArray): Frame = Frame.Text(buildJsonObject {
        put("type", "input_audio_buffer.append")
        put("audio", Base64.Default.encode(bytes))
    }.toString())

    override suspend fun cancelOutput() {
        if (responding) send(Frame.Text("""{"type":"response.cancel"}"""))
        responding = false
    }

    override suspend fun finish() { send(Frame.Text("""{"type":"session.finish"}""")) }

    override suspend fun receive(frame: Frame) {
        if (frame !is Frame.Text) return
        val data = Json.parseToJsonElement(frame.readText()).jsonObject
        fun value(key: String) = data[key]?.jsonPrimitive?.content.orEmpty()
        when (value("type")) {
            "session.updated" -> markReady()
            "input_audio_buffer.speech_started" -> {
                responding = false
                emit(RealtimeEvent.InterruptPlayback)
                emit(RealtimeEvent.Listening)
            }
            "input_audio_buffer.speech_stopped" -> emit(RealtimeEvent.Thinking)
            "conversation.item.input_audio_transcription.completed" -> emit(RealtimeEvent.InputText(value("transcript"), true))
            "response.created" -> { responding = true; emit(RealtimeEvent.Thinking) }
            "response.audio_transcript.delta", "response.text.delta" -> emit(RealtimeEvent.OutputText(value("delta"), false))
            "response.audio_transcript.done", "response.text.done" -> emit(RealtimeEvent.OutputText(value("transcript").ifEmpty { value("text") }, true))
            "response.audio.delta" -> {
                if (responding) {
                    emit(RealtimeEvent.Speaking)
                    emit(RealtimeEvent.OutputAudio(Base64.Default.decode(value("delta")), "pcm_s16le", 24000))
                }
            }
            "response.done" -> { responding = false; emit(RealtimeEvent.Listening) }
            "error" -> fail(data["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Qwen realtime error")
        }
    }

    companion object {
        suspend fun connect(config: RealtimeConfig.Qwen): RealtimeSession {
            require(config.apiKey.isNotBlank() && config.model.isNotBlank()) { "Qwen credentials and model required" }
            require(Regex("^[A-Za-z0-9._-]+$").matches(config.model)) { "Invalid Qwen model name" }
            val endpoint = config.workspaceEndpoint.trimEnd('/')
            require(Regex("^wss://[A-Za-z0-9-]+\\.(cn-beijing|ap-southeast-1)\\.maas\\.aliyuncs\\.com$").matches(endpoint)) {
                "Expected a regional Aliyun workspace WSS endpoint"
            }
            val (client, socket) = openSocket("$endpoint/api-ws/v1/realtime?model=${config.model}", mapOf("Authorization" to "Bearer ${config.apiKey}"))
            val session = QwenRealtimeSession(config, client, socket)
            session.start()
            try { session.awaitReady() } catch (error: Throwable) { session.close(); throw error }
            return session
        }
    }
}
