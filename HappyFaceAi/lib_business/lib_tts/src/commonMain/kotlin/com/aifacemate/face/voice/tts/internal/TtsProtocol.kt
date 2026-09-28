package com.aifacemate.face.voice.tts.internal

import com.aifacemate.face.voice.tts.catalog.TtsProvider
import com.aifacemate.face.voice.tts.model.TtsRequest
import kotlin.io.encoding.Base64

internal fun String.base64Decode(): ByteArray = Base64.decode(this)

internal fun ttsEndpoint(provider: TtsProvider, request: TtsRequest): String = provider.baseUrl.trimEnd('/')
internal fun jsonEscape(value: String): String = buildString {
    value.forEach { c -> when (c) { '\\' -> append("\\\\"); '"' -> append("\\\""); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t"); else -> append(c) } }
}
