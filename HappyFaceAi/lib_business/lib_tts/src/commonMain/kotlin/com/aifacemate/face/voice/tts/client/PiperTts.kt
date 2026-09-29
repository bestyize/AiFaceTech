package com.aifacemate.face.voice.tts.client

import com.aifacemate.face.voice.tts.model.TtsResult

/** Optional, on-device Chinese Piper TTS. Android and desktop support the local runtime; iOS is not yet available. */
expect object PiperTts {
    fun supported(): Boolean
    /** Prepare native inference runtime when this desktop platform requires it. */
    fun prepareRuntime(): Boolean
    fun installed(): Boolean
    suspend fun install(onProgress: (Long, Long) -> Unit = { _, _ -> })
    suspend fun remove()
    suspend fun synthesize(text: String): TtsResult
}
