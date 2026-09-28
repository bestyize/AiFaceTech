package com.aifacemate.face.voice.asr.internal

import com.aifacemate.face.voice.asr.catalog.AsrProvider
import com.aifacemate.face.voice.asr.model.AsrRequest
import kotlin.io.encoding.Base64

internal fun ByteArray.base64Encode(): String = Base64.encode(this)

internal fun asrEndpoint(provider: AsrProvider, request: AsrRequest): String = provider.baseUrl.trimEnd('/')
internal fun multipartBoundary(): String = "----HappyFaceAiAsrBoundary"
