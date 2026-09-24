package com.aifacemate.face.voice.asr.client

import com.aifacemate.face.voice.asr.catalog.defaultAsrProviders
import com.aifacemate.face.voice.asr.internal.asrEndpoint
import com.aifacemate.face.voice.asr.internal.multipartBoundary
import com.aifacemate.face.voice.asr.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

actual suspend fun transcribeAudio(request: AsrRequest): AsrResult = withContext(Dispatchers.IO) {
    if (request.apiKey.isBlank()) return@withContext AsrResult.Failure("请先填写 ASR API Key")
    if (request.audio.isEmpty()) return@withContext AsrResult.Failure("录音内容为空")
    val provider = defaultAsrProviders.firstOrNull { it.id == request.providerId }
        ?: return@withContext AsrResult.Failure("不支持的 ASR 供应商：${request.providerId}")
    try {
        val boundary = multipartBoundary()
        val connection = (URI.create(asrEndpoint(provider, request)).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15_000; readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer ${request.apiKey.trim()}")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        connection.outputStream.use { out ->
            fun field(name: String, value: String) {
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            field("model", request.model)
            request.language?.takeIf { it.isNotBlank() }?.let { field("language", it) }
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${request.fileName}\"\r\nContent-Type: ${request.mimeType}\r\n\r\n".toByteArray())
            out.write(request.audio)
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) AsrResult.Failure("ASR 请求失败（HTTP $code）", code)
        else {
            val text = Regex("\\\"(text|transcript)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\"])*)\\\"").find(body)?.groupValues?.get(2)?.replace("\\\"", "\"")
            if (text.isNullOrBlank()) AsrResult.Failure("ASR 返回格式无法解析", code) else AsrResult.Success(text)
        }
    } catch (error: Exception) { AsrResult.Failure("无法连接 ASR 服务：${error.message ?: "网络错误"}") }
}
