package com.aifacemate.face.voice.tts.client

import com.aifacemate.face.voice.tts.catalog.defaultTtsProviders
import com.aifacemate.face.voice.tts.internal.jsonEscape
import com.aifacemate.face.voice.tts.internal.ttsEndpoint
import com.aifacemate.face.voice.tts.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

actual suspend fun synthesizeSpeech(request: TtsRequest): TtsResult = withContext(Dispatchers.IO) {
    if (request.apiKey.isBlank()) return@withContext TtsResult.Failure("请先填写 TTS API Key")
    if (request.text.isBlank()) return@withContext TtsResult.Failure("请输入要合成的文字")
    val provider = defaultTtsProviders.firstOrNull { it.id == request.providerId }
        ?: return@withContext TtsResult.Failure("不支持的 TTS 供应商：${request.providerId}")
    try {
        val connection = (URI.create(ttsEndpoint(provider, request)).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15_000; readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer ${request.apiKey.trim()}")
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("Accept", "audio/mpeg, application/json")
        }
        val body = "{\"model\":\"${jsonEscape(request.model)}\",\"input\":\"${jsonEscape(request.text)}\",\"voice\":\"${jsonEscape(request.voice)}\",\"response_format\":\"${jsonEscape(request.format)}\",\"speed\":${request.speed.coerceIn(.25f, 4f)}}"
        connection.outputStream.use { it.write(body.toByteArray()) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val bytes = stream?.use { input -> input.readBytes() } ?: ByteArray(0)
        if (code !in 200..299) TtsResult.Failure("TTS 请求失败（HTTP $code）", code)
        else if (bytes.isEmpty()) TtsResult.Failure("TTS 返回了空音频", code)
        else if (connection.contentType?.contains("json") == true) {
            val encoded = Regex("\\\"(audio|data)\\\"\\s*:\\s*\\\"([^\"]+)\\\"").find(bytes.decodeToString())?.groupValues?.get(2)
            if (encoded == null) TtsResult.Failure("TTS 返回格式无法解析", code)
            else TtsResult.Success(Base64.getDecoder().decode(encoded), "audio/mpeg")
        } else TtsResult.Success(bytes, connection.contentType ?: "audio/mpeg")
    } catch (error: Exception) { TtsResult.Failure("无法连接 TTS 服务：${error.message ?: "网络错误"}") }
}
