package com.aifacemate.face.voice.realtime.provider.doubao

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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal class DoubaoRealtimeSession private constructor(
    private val config: RealtimeConfig.Doubao,
    client: HttpClient,
    socket: WebSocketSession,
) : SocketSession(client, socket) {
    override suspend fun open() { send(Frame.Binary(true, DoubaoProtocol.encode(1, "{}".encodeToByteArray()))) }

    override fun audioFrame(bytes: ByteArray): Frame = Frame.Binary(true, DoubaoProtocol.encode(200, bytes, config.sessionId, audio = true))

    override suspend fun finish() {
        send(Frame.Binary(true, DoubaoProtocol.encode(102, "{}".encodeToByteArray(), config.sessionId)))
        send(Frame.Binary(true, DoubaoProtocol.encode(2, "{}".encodeToByteArray())))
    }

    override suspend fun receive(frame: Frame) {
        if (frame !is Frame.Binary) return
        val message = DoubaoProtocol.decode(frame.readBytes())
        val raw = message.payload.decodeToString()
        val body = if (message.event != 352) runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() else null
        fun text(key: String) = body?.get(key)?.jsonPrimitive?.content.orEmpty()
        when (message.event) {
            50 -> {
                val payload = buildJsonObject {
                    putJsonObject("dialog") { if (config.systemRole.isNotBlank()) put("system_role", config.systemRole) }
                    putJsonObject("tts") {
                        putJsonObject("audio_config") {
                            put("channel", 1); put("format", "pcm"); put("sample_rate", 24000)
                        }
                    }
                }
                send(Frame.Binary(true, DoubaoProtocol.encode(100, payload.toString().encodeToByteArray(), config.sessionId)))
            }
            150 -> { markReady(); emit(RealtimeEvent.Listening) }
            450 -> { emit(RealtimeEvent.InterruptPlayback); emit(RealtimeEvent.Listening) }
            451 -> body?.get("results")?.jsonArray?.forEach { result ->
                val item = result.jsonObject
                val words = item["text"]?.jsonPrimitive?.content.orEmpty()
                val interim = item["is_interim"]?.jsonPrimitive?.content == "true"
                if (words.isNotEmpty()) emit(RealtimeEvent.InputText(words, !interim))
            }
            459 -> emit(RealtimeEvent.Thinking)
            350 -> { emit(RealtimeEvent.Speaking); if (text("text").isNotEmpty()) emit(RealtimeEvent.OutputText(text("text"), false)) }
            352 -> emit(RealtimeEvent.OutputAudio(message.payload, "pcm_f32le", 24000))
            359 -> emit(RealtimeEvent.Listening)
            550 -> emit(RealtimeEvent.OutputText(text("content"), false))
            559 -> emit(RealtimeEvent.OutputText("", true))
            51, 153 -> fail(text("error").ifEmpty { "Doubao realtime error ${message.event}" })
            else -> if (message.errorCode != null) fail("Doubao error ${message.errorCode}: $raw")
        }
    }

    companion object {
        suspend fun connect(config: RealtimeConfig.Doubao): RealtimeSession {
            require(config.appId.isNotBlank() && config.accessKey.isNotBlank() && config.appKey.isNotBlank()) { "Doubao credentials required" }
            require(Regex("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$").matches(config.sessionId)) { "Expected a new UUID session ID" }
            require(config.systemRole.length <= 1500) { "Doubao system role too long" }
            val (client, socket) = openSocket("wss://openspeech.bytedance.com/api/v3/realtime/dialogue", mapOf(
                "X-Api-App-ID" to config.appId,
                "X-Api-Access-Key" to config.accessKey,
                "X-Api-Resource-Id" to "volc.speech.dialog",
                "X-Api-App-Key" to config.appKey,
            ))
            val session = DoubaoRealtimeSession(config, client, socket)
            session.start()
            try { session.awaitReady() } catch (error: Throwable) { session.close(); throw error }
            return session
        }
    }
}
