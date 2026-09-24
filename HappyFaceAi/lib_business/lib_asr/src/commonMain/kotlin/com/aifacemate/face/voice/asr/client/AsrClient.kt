package com.aifacemate.face.voice.asr.client

import com.aifacemate.face.voice.asr.catalog.defaultAsrProviders
import com.aifacemate.face.voice.asr.model.AsrRequest
import com.aifacemate.face.voice.asr.model.AsrResult
import com.aifacemate.face.network.MultipartPart
import com.aifacemate.face.network.NetworkClient


/** 只编排 MiMo ASR 协议，不直接依赖平台网络 API。 */
class AsrClient(private val network: NetworkClient) {
    suspend fun transcribe(request: AsrRequest): AsrResult {
        if (request.apiKey.isBlank()) return AsrResult.Failure("请先填写 ASR API Key")
        if (request.audio.isEmpty()) return AsrResult.Failure("录音内容为空")
        val provider = defaultAsrProviders.firstOrNull { it.id == request.providerId }
            ?: return AsrResult.Failure("不支持的 ASR 供应商：${request.providerId}")
        val parts = buildList {
            add(MultipartPart(name = "model", value = request.model))
            request.language?.takeIf { it.isNotBlank() }?.let { add(MultipartPart(name = "language", value = it)) }
            add(MultipartPart(name = "file", fileName = request.fileName, mimeType = request.mimeType, bytes = request.audio))
        }
        val response = network.postMultipart(provider.baseUrl, mapOf("Authorization" to "Bearer ${request.apiKey.trim()}"), parts)
        if (response.statusCode !in 200..299) return AsrResult.Failure("ASR 请求失败（HTTP ${response.statusCode}）", response.statusCode)
        val text = Regex("\\\"(text|transcript)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\"])*)\\\"").find(response.text)?.groupValues?.get(2)?.replace("\\\"", "\"")
        return if (text.isNullOrBlank()) AsrResult.Failure("ASR 返回格式无法解析", response.statusCode) else AsrResult.Success(text)
    }
}
