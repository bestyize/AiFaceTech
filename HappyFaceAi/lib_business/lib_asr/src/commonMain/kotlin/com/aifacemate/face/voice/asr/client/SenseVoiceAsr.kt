package com.aifacemate.face.voice.asr.client

/** The model is downloaded on demand. Android is the first supported runtime. */
expect object SenseVoiceAsr {
    fun supported(): Boolean
    fun installed(): Boolean
    suspend fun install(onProgress: (Long, Long) -> Unit = { _, _ -> })
    suspend fun remove()
    suspend fun transcribe(wav: ByteArray): String
}
