package com.aifacemate.face.voice

data class VoiceClip(val bytes: ByteArray, val mimeType: String = "audio/wav")
sealed interface VoiceCaptureResult { data class Success(val clip: VoiceClip) : VoiceCaptureResult; data class Failure(val reason: String) : VoiceCaptureResult }
sealed interface VoicePlaybackResult { data object Complete : VoicePlaybackResult; data class Failure(val reason: String) : VoicePlaybackResult }

interface VoiceService {
    suspend fun record(maxDurationMs: Long = 6_000): VoiceCaptureResult
    suspend fun play(clip: VoiceClip): VoicePlaybackResult
    fun stop()
}

expect fun createVoiceService(): VoiceService
