package com.aifacemate.face.voice.tts.client

import com.aifacemate.face.voice.tts.catalog.defaultTtsProviders
import com.aifacemate.face.voice.tts.internal.jsonEscape
import com.aifacemate.face.voice.tts.model.TtsRequest
import com.aifacemate.face.voice.tts.model.TtsResult
import com.aifacemate.face.network.NetworkClient

/** 只编排 MiMo 协议，不直接依赖平台网络 API。 */
class TtsClient(private val network: NetworkClient) {
    suspend fun synthesize(request: TtsRequest): TtsResult {
        if (request.apiKey.isBlank()) return TtsResult.Failure("请先填写 TTS API Key")
        if (request.text.isBlank()) return TtsResult.Failure("请输入要合成的文字")
        val provider = defaultTtsProviders.firstOrNull { it.id == request.providerId }
            ?: return TtsResult.Failure("不支持的 TTS 供应商：${request.providerId}")
        val body = "{\"model\":\"${jsonEscape(request.model)}\",\"input\":\"${jsonEscape(request.text)}\",\"voice\":\"${jsonEscape(request.voice)}\",\"response_format\":\"${jsonEscape(request.format)}\",\"speed\":${request.speed.coerceIn(.25f, 4f)}}"
        val response = network.postJson(provider.baseUrl, mapOf("Authorization" to "Bearer ${request.apiKey.trim()}"), body)
        if (response.statusCode !in 200..299) return TtsResult.Failure("TTS 请求失败（HTTP ${response.statusCode}）", response.statusCode)
        if (response.body.isEmpty()) return TtsResult.Failure("TTS 返回了空音频", response.statusCode)
        return TtsResult.Success(response.body, response.contentType ?: "audio/mpeg")
    }
}
