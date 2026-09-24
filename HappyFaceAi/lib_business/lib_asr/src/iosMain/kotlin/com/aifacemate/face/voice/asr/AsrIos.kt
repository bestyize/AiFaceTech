package com.aifacemate.face.voice.asr.client

actual suspend fun transcribeAudio(request: AsrRequest): AsrResult =
    AsrResult.Failure("iOS 端暂未接入语音识别网络实现")
