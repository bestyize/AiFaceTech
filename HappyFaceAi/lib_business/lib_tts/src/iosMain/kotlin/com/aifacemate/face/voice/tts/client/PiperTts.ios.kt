package com.aifacemate.face.voice.tts.client

import com.aifacemate.face.voice.tts.model.TtsResult

actual object PiperTts {
    actual fun supported(): Boolean = false
    actual fun prepareRuntime(): Boolean = false
    actual fun installed(): Boolean = false
    actual suspend fun install(onProgress: (Long, Long) -> Unit) { error("Piper 本地语音目前仅支持 Android 与桌面端") }
    actual suspend fun remove() = Unit
    actual suspend fun synthesize(text: String): TtsResult = TtsResult.Failure("Piper 本地语音目前仅支持 Android 与桌面端")
}
