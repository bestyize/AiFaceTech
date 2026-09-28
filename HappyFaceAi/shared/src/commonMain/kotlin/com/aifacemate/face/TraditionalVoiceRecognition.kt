package com.aifacemate.face

import kotlinx.coroutines.CancellationException

/** Local recognition is independent of optional online fallback and speech synthesis. */
internal suspend fun recognizeTraditionalSpeech(
    asr: TraditionalAsr,
    hasOnlineKey: Boolean,
    local: suspend () -> String,
    online: suspend () -> String,
    onFallback: () -> Unit = {},
): String {
    if (asr == TraditionalAsr.Mimo) {
        check(hasOnlineKey) { "请先在声音设置中填写 MiMo 在线识别的 API Key" }
        return online()
    }
    return try {
        local().ifBlank { error("本地未识别到声音") }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        if (!hasOnlineKey) throw IllegalStateException("SenseVoice 本地识别失败：${error.message ?: "未知错误"}", error)
        onFallback()
        online()
    }
}
