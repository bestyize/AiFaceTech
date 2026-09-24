package com.aifacemate.face.voice.asr.catalog

data class AsrProvider(val id: String, val name: String, val baseUrl: String, val models: List<String>)

val defaultAsrProviders = listOf(
    AsrProvider("xiaomi-asr", "小米 ASR", "https://api.xiaomimimo.com/v1/audio/transcriptions", listOf("mimo-asr")),
)
