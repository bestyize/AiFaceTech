package com.aifacemate.face.voice

import kotlin.math.sqrt

sealed interface VoiceObservation {
    data object Cancelled : VoiceObservation
    data class PartialText(val text: String) : VoiceObservation
    data class Level(val value: Float) : VoiceObservation
}

/** Real PCM16 little-endian amplitude; never an emotion classification. */
fun pcm16Activity(bytes: ByteArray, count: Int = bytes.size): Float {
    require(count in 0..bytes.size)
    val samples = count / 2
    if (samples == 0) return 0f
    var energy = 0.0
    for (offset in 0 until samples * 2 step 2) {
        val value = (((bytes[offset].toInt() and 255) or (bytes[offset + 1].toInt() shl 8)).toShort().toInt()) / 32768.0
        energy += value * value
    }
    return ((sqrt(energy / samples) - .008) * 8).toFloat().coerceIn(0f, 1f)
}
