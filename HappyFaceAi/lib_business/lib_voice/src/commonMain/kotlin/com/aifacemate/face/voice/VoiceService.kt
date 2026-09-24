package com.aifacemate.face.voice

/** Platform voice input and output boundary. Implementations are added when ASR/TTS is integrated. */
interface VoiceService {
    suspend fun listen(): VoiceInput
    suspend fun speak(text: String): VoiceOutput
}

sealed interface VoiceInput {
    data class Recognized(val text: String) : VoiceInput
    data class Unavailable(val reason: String) : VoiceInput
}

sealed interface VoiceOutput {
    data object Complete : VoiceOutput
    data class Unavailable(val reason: String) : VoiceOutput
}
