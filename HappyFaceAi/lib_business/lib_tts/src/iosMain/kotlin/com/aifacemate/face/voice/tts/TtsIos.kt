package com.aifacemate.face.voice.tts.client

actual suspend fun synthesizeSpeech(request: TtsRequest): TtsResult =
    TtsResult.Failure("iOS 端暂未接入语音合成网络实现")
