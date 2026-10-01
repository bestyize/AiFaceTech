package com.aifacemate.face.voice

import com.aifacemate.face.voice.asr.vad.SpeechAudio
import com.aifacemate.face.voice.asr.vad.SpeechDetector
import com.aifacemate.face.voice.asr.vad.createSpeechDetector

/**
 * Runs voice activity detection beside a 16 kHz PCM16 recorder. Without a detector (iOS, or a native load
 * failure) recordings pass through unchanged and recognition relies on the text check alone.
 */
class SpeechCapture(private val detector: SpeechDetector? = createSpeechDetector()) : AutoCloseable {
    private var chunks = 0
    private var reported: Boolean? = null

    /** Returns the observation to publish for this chunk, if any: on every change and every 100 ms of audio. */
    fun accept(pcm: ByteArray, count: Int): VoiceObservation.Voice? {
        val detector = detector ?: return null
        val active = detector.accept(SpeechAudio.pcm16ToFloat(pcm, count))
        val due = ++chunks % 5 == 0 || active != reported
        reported = active
        return if (due) VoiceObservation.Voice(active) else null
    }

    /** Speech-only PCM, the whole recording when there is no detector, or null when no speech was heard. */
    fun finish(pcm: ByteArray): ByteArray? {
        val detector = detector ?: return pcm
        return SpeechAudio.keepSpeech(pcm, detector.finish())
    }

    override fun close() { detector?.close() }
}
