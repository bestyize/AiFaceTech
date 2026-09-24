package com.aifacemate.face.voice.realtime.provider.qwen

import com.aifacemate.face.voice.realtime.RealtimeConfig
import com.aifacemate.face.voice.realtime.RealtimeEvent
import com.aifacemate.face.voice.realtime.RealtimeSession
import com.aifacemate.face.voice.realtime.internal.SocketSession
import com.aifacemate.face.voice.realtime.internal.openSocket

import io.ktor.client.HttpClient
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
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
    private val responses = QwenResponseTracker()

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
        if (responses.interrupt()) send(Frame.Text("""{"type":"response.cancel"}"""))
    }

    override suspend fun finish() { send(Frame.Text("""{"type":"session.finish"}""")) }

    override suspend fun receive(frame: Frame) {
        if (frame !is Frame.Text) return
        val data = Json.parseToJsonElement(frame.readText()).jsonObject
        fun value(key: String) = data[key]?.jsonPrimitive?.content.orEmpty()
        fun belongsToActiveResponse() = responses.accepts(value("response_id"))
        QwenEventDecoder.input(data)?.let { emit(it); return }
        when (value("type")) {
            "session.updated" -> markReady()
            "input_audio_buffer.speech_started" -> {
                responses.interrupt()
                emit(RealtimeEvent.InterruptPlayback)
                emit(RealtimeEvent.Listening)
            }
            "input_audio_buffer.speech_stopped" -> emit(RealtimeEvent.Thinking)
            "response.created" -> {
                val id = data["response"]?.jsonObject?.get("id")?.jsonPrimitive?.content.orEmpty()
                responses.start(id)
                if (id.isNotBlank()) emit(RealtimeEvent.Thinking)
            }
            "response.audio_transcript.delta", "response.text.delta" -> if (belongsToActiveResponse()) emit(RealtimeEvent.OutputText(value("delta"), false))
            "response.audio_transcript.done", "response.text.done" -> if (belongsToActiveResponse()) emit(RealtimeEvent.OutputText(value("transcript").ifEmpty { value("text") }, true))
            "response.audio.delta" -> {
                if (belongsToActiveResponse()) {
                    emit(RealtimeEvent.Speaking)
                    emit(QwenEventDecoder.outputAudio(data))
                }
            }
            "response.done" -> {
                val response = data["response"]?.jsonObject
                if (responses.finish(response?.get("id")?.jsonPrimitive?.content.orEmpty())) {
                    if (response?.get("status")?.jsonPrimitive?.content == "failed") emit(RealtimeEvent.Failure("Qwen response generation failed"))
                    emit(RealtimeEvent.OutputComplete)
                }
            }
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
