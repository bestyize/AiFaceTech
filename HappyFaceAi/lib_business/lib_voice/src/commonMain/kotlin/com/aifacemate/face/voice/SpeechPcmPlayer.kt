package com.aifacemate.face.voice

import com.aifacemate.face.voice.tts.model.SpeechAudioChunk

/** Opens only a speaker, never a microphone. A fresh player is owned by each response. */
interface SpeechPcmPlayer {
    suspend fun write(audio: SpeechAudioChunk)
    suspend fun drain()
    /** Stop and discard queued audio immediately. */
    fun close()
}

expect fun createSpeechPcmPlayer(): SpeechPcmPlayer
