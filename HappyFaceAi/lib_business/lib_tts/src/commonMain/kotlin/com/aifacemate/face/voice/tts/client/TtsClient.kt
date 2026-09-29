package com.aifacemate.face.voice.tts.client

import com.aifacemate.face.network.NetworkClient
import com.aifacemate.face.voice.tts.catalog.defaultTtsProviders
import com.aifacemate.face.voice.tts.internal.base64Decode
import com.aifacemate.face.voice.tts.model.*
import kotlinx.serialization.json.*

class TtsClient(private val network: NetworkClient) {
    suspend fun synthesize(request: TtsRequest): TtsResult {
        if (request.apiKey.isBlank()) return TtsResult.Failure("请先填写 TTS API Key")
        if (request.text.isBlank()) return TtsResult.Failure("请输入要合成的文字")
        val endpoint = endpoint(request) ?: return TtsResult.Failure("不支持的 TTS 供应商：${request.providerId}")
        val response = network.postJson(endpoint, mapOf("api-key" to request.apiKey.trim()), requestBody(request, streaming = false))
        if (response.statusCode !in 200..299) return TtsResult.Failure("TTS 请求失败（HTTP ${response.statusCode}）", response.statusCode)
        val root = runCatching { Json.parseToJsonElement(response.text).jsonObject }.getOrElse {
            return TtsResult.Failure("TTS 返回格式无法解析", response.statusCode)
        }
        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message") as? JsonObject
        val audio = (message?.get("audio") ?: root["audio"]) as? JsonObject
        val encoded = audio?.get("data")?.jsonPrimitive?.contentOrNull
        return if (encoded.isNullOrBlank()) TtsResult.Failure("TTS 返回格式无法解析", response.statusCode)
            else TtsResult.Success(encoded.base64Decode(), "audio/${request.format}")
    }

    /** MiMo built-in voices: PCM16LE mono at 24 kHz, emitted before response completion. */
    suspend fun stream(request: TtsRequest, onAudio: suspend (SpeechAudioChunk) -> Unit) {
        require(request.apiKey.isNotBlank()) { "请先填写 MiMo API Key" }
        require(request.text.isNotBlank()) { "请输入要合成的文字" }
        require(request.model == "mimo-v2.5-tts") { "当前流式播放支持 MiMo 预置音色模型" }
        val endpoint = endpoint(request) ?: error("不支持的 TTS 供应商：${request.providerId}")
        val alignment = Pcm16Alignment()
        var received = false
        var completed = false
        val response = network.postEventStream(endpoint, mapOf("api-key" to request.apiKey.trim()), requestBody(request, streaming = true)) { event ->
            val chunk = decodeTtsStreamEvent(event)
            val pcm = alignment.append(chunk.audio)
            var offset = 0
            while (offset < pcm.size) {
                val end = minOf(offset + 12_000, pcm.size) // 250 ms at 24 kHz, bounding queue memory.
                onAudio(SpeechAudioChunk(pcm.copyOfRange(offset, end), 24_000))
                received = true; offset = end
            }
            if (chunk.finished) completed = true
            !chunk.finished
        }
        check(response.statusCode in 200..299) { "MiMo 合成失败（HTTP ${response.statusCode}）" }
        check(completed) { "MiMo 音频连接提前断开" }
        alignment.finish()
        check(received) { "MiMo 没有返回有效音频" }
    }

    private fun endpoint(request: TtsRequest): String? {
        val provider = defaultTtsProviders.firstOrNull { it.id == request.providerId } ?: return null
        val base = (request.baseUrl ?: provider.baseUrl).trim().trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }
}

internal fun requestBody(request: TtsRequest, streaming: Boolean): String = buildJsonObject {
    put("model", request.model); put("stream", streaming)
    putJsonArray("messages") {
        add(buildJsonObject { put("role", "user"); put("content", "") })
        add(buildJsonObject { put("role", "assistant"); put("content", request.text) })
    }
    putJsonObject("audio") { put("format", if (streaming) "pcm16" else request.format); put("voice", request.voice) }
}.toString()

internal data class TtsStreamChunk(val audio: ByteArray = ByteArray(0), val finished: Boolean = false)

internal fun decodeTtsStreamEvent(event: String): TtsStreamChunk {
    if (event.isBlank()) return TtsStreamChunk()
    if (event.trim() == "[DONE]") return TtsStreamChunk(finished = true)
    val root = Json.parseToJsonElement(event).jsonObject
    root["error"]?.takeIf { it != JsonNull }?.let { failure ->
        error((failure as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: "MiMo 流式合成失败")
    }
    val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return TtsStreamChunk()
    val message = choice["message"] as? JsonObject
    val delta = choice["delta"] as? JsonObject
    val audio = (delta ?: message)?.get("audio") as? JsonObject
    val encoded = audio?.get("data")?.jsonPrimitive?.contentOrNull
    val finish = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
    check(finish == null || finish == "stop") { "MiMo 音频未完整生成（${finish}）" }
    return TtsStreamChunk(encoded?.takeIf { it.isNotEmpty() }?.base64Decode() ?: ByteArray(0), finish != null || message != null)
}

/** Network event boundaries need not coincide with 16-bit sample boundaries. */
internal class Pcm16Alignment {
    private var pending: Byte? = null
    fun append(bytes: ByteArray): ByteArray {
        val all = pending?.let { byteArrayOf(it) + bytes } ?: bytes
        pending = if (all.size % 2 == 1) all.last() else null
        return if (pending == null) all else all.copyOf(all.size - 1)
    }
    fun finish() { check(pending == null) { "MiMo 返回了不完整的 PCM16 采样" } }
}
