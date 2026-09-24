package com.aifacemate.face.voice.tts.catalog

data class TtsProvider(val id: String, val name: String, val baseUrl: String, val models: List<String>, val voices: List<String>)

val defaultTtsProviders = listOf(
    TtsProvider("xiaomi-mimo", "小米 MiMo", "https://api.xiaomimimo.com/v1/audio/speech", listOf("mimo-v2-tts"), listOf("mimo-default")),
)
