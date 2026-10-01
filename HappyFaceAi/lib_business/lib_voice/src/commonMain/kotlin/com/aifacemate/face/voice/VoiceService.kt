package com.aifacemate.face.voice

data class VoiceClip(val bytes: ByteArray, val mimeType: String = "audio/wav")
sealed interface VoiceCaptureResult {
    data class Success(val clip: VoiceClip) : VoiceCaptureResult
    /** The recording held only noise or silence, so nothing should be recognized or sent. */
    data object NoSpeech : VoiceCaptureResult
    data class Failure(val reason: String) : VoiceCaptureResult
}
sealed interface VoicePlaybackResult { data object Complete : VoicePlaybackResult; data class Failure(val reason: String) : VoicePlaybackResult }

interface VoiceService {
    val observations: kotlinx.coroutines.flow.Flow<VoiceObservation> get() = kotlinx.coroutines.flow.emptyFlow()
    suspend fun record(maxDurationMs: Long = 6_000): VoiceCaptureResult
    /** End capture and return the audio recorded so far from [record]. */
    fun finishRecording()
    suspend fun play(clip: VoiceClip): VoicePlaybackResult
    /** Cancel capture or playback without submitting a conversation turn. */
    fun stop()
}

expect fun createVoiceService(): VoiceService
