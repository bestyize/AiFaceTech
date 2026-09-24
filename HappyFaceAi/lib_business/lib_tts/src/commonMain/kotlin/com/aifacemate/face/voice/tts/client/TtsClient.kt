package com.aifacemate.face.voice.tts.client

import com.aifacemate.face.voice.tts.model.TtsRequest
import com.aifacemate.face.voice.tts.model.TtsResult

/** 跨平台 TTS 调用入口；平台层负责具体网络实现。 */
expect suspend fun synthesizeSpeech(request: TtsRequest): TtsResult
