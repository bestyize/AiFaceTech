package com.aifacemate.face.voice.asr.client

import com.aifacemate.face.voice.asr.model.AsrRequest
import com.aifacemate.face.voice.asr.model.AsrResult

/** 跨平台 ASR 调用入口；平台层负责具体网络实现。 */
expect suspend fun transcribeAudio(request: AsrRequest): AsrResult
