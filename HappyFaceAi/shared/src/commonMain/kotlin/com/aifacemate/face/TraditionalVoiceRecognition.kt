package com.aifacemate.face

import com.aifacemate.face.voice.asr.vad.hasSpokenContent
import kotlinx.coroutines.CancellationException

/**
 * Local recognition is independent of optional online fallback and speech synthesis. An empty result means
 * no speech was recognized; it is not an error and must not be sent as a turn.
 */
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
    val text = try {
        local()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        if (!hasOnlineKey) throw IllegalStateException("SenseVoice 本地识别失败：${error.message ?: "未知错误"}", error)
        onFallback()
        return online()
    }
    if (text.hasSpokenContent()) return text
    if (!hasOnlineKey) return ""
    onFallback()
    return online()
}
