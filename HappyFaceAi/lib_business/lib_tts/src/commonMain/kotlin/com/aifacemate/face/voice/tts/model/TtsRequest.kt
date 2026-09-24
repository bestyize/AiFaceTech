package com.aifacemate.face.voice.tts.model

data class TtsRequest(
    val providerId: String = "xiaomi-mimo",
    val apiKey: String,
    val text: String,
    val model: String = "mimo-v2-tts",
    val voice: String = "mimo-default",
    val format: String = "mp3",
    val speed: Float = 1f,
)
