package com.aifacemate.face.voice
actual fun createVoiceService(): VoiceService = object : VoiceService { override suspend fun record(maxDurationMs:Long)=VoiceCaptureResult.Failure("iOS 音频采集尚未接入"); override suspend fun play(clip:VoiceClip)=VoicePlaybackResult.Failure("iOS 音频播放尚未接入"); override fun stop()=Unit }
