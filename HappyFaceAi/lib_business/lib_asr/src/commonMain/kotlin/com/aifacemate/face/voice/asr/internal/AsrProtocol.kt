package com.aifacemate.face.voice.asr.internal

import com.aifacemate.face.voice.asr.catalog.AsrProvider
import com.aifacemate.face.voice.asr.model.AsrRequest

internal fun asrEndpoint(provider: AsrProvider, request: AsrRequest): String = provider.baseUrl.trimEnd('/')
internal fun multipartBoundary(): String = "----HappyFaceAiAsrBoundary"
