package com.aifacemate.face.voice.asr.model

data class AsrRequest(
    val providerId: String = "xiaomi-asr",
    val apiKey: String,
    val baseUrl: String? = null,
    val audio: ByteArray,
    val mimeType: String = "audio/wav",
    val fileName: String = "recording.wav",
    val model: String = "mimo-v2.5-asr",
    val language: String? = null,
)
