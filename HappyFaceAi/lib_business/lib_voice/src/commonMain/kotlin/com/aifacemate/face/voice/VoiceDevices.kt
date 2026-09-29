package com.aifacemate.face.voice

import com.aifacemate.face.voice.realtime.RealtimeEvent

interface NativeSpeechEngine {
    /** Recognize one utterance with the platform speech service. */
    suspend fun recognize(): String
    /** Stop listening and await recognition of the audio already received. */
    fun finishRecognition()
    /** Speak one answer and return after it finishes. */
    suspend fun speak(text: String)
    fun stop()
}

expect fun systemAsrAvailable(): Boolean
expect fun systemTtsAvailable(): Boolean
expect fun createNativeSpeechEngine(): NativeSpeechEngine

interface RealtimeAudioDevice : AutoCloseable {
    /** Capture 16 kHz mono PCM16 frames continuously until cancelled or closed. */
    suspend fun capture(onFrame: suspend (ByteArray) -> Unit)
    /** Play provider audio in sequence. */
    suspend fun play(audio: RealtimeEvent.OutputAudio)
    /** Await local playback after the server's OutputComplete event. */
    suspend fun drain()
    fun interrupt()
}

expect fun realtimeAudioAvailable(): Boolean
expect fun createRealtimeAudioDevice(): RealtimeAudioDevice

/** Decode float32 from Doubao or PCM16 from Qwen to mono PCM16 at 24 kHz. */
fun playbackPcm16(audio: RealtimeEvent.OutputAudio): ByteArray {
    require(audio.sampleRate == 24_000) { "Expected 24 kHz playback" }
    return when (audio.format) {
        "pcm_s16le" -> audio.bytes.also { require(it.size % 2 == 0) }
        "pcm_f32le" -> {
            require(audio.bytes.size % 4 == 0)
            ByteArray(audio.bytes.size / 2).also { result ->
                for (i in result.indices step 2) {
                    val pos = i * 2
                    val bits = (audio.bytes[pos].toInt() and 255) or
                        ((audio.bytes[pos + 1].toInt() and 255) shl 8) or
                        ((audio.bytes[pos + 2].toInt() and 255) shl 16) or
                        ((audio.bytes[pos + 3].toInt() and 255) shl 24)
                    val sample = Float.fromBits(bits).coerceIn(-1f, 1f)
                    val signed = (sample * if (sample < 0f) 32768f else 32767f).toInt()
                    result[i] = signed.toByte()
                    result[i + 1] = (signed shr 8).toByte()
                }
            }
        }
        else -> error("Unsupported audio format: ${audio.format}")
    }
}
