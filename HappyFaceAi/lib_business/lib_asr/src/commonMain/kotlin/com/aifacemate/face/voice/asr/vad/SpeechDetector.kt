package com.aifacemate.face.voice.asr.vad

/** Streaming voice activity detection on 16 kHz mono samples in [-1, 1]. Not thread-safe. */
interface SpeechDetector : AutoCloseable {
    /** Feeds samples of any length; returns whether speech is currently detected. */
    fun accept(samples: FloatArray): Boolean

    /** Flushes buffered audio and returns every speech span so far, in samples from the first accepted sample. */
    fun finish(): List<IntRange>
}

/** Silero VAD v5 bundled with the app. Null where the native runtime is unavailable (iOS) or fails to load. */
expect fun createSpeechDetector(): SpeechDetector?

/** Silero VAD settings shared by every platform; validated against noise and quiet speech samples. */
internal object SileroSettings {
    const val RESOURCE = "vad/silero_vad_v5.onnx"
    const val FILE_NAME = "silero_vad_v5.onnx"
    const val SIZE = 2_313_101L
    const val SHA256 = "6b99cbfd39246b6706f98ec13c7c50c6b299181f2474fa05cbc8046acc274396"
    const val WINDOW = 512
    const val THRESHOLD = .5f
    const val MIN_SILENCE_SECONDS = .25f
    const val MIN_SPEECH_SECONDS = .25f
    const val MAX_SPEECH_SECONDS = 20f
}

/** Feeds the native detector whole 512-sample windows and collects the spans it reports. */
internal abstract class WindowedSpeechDetector : SpeechDetector {
    private val pending = FloatArray(SileroSettings.WINDOW)
    private var filled = 0
    private val spans = ArrayList<IntRange>()

    protected abstract fun acceptWindow(window: FloatArray)
    protected abstract fun speechDetected(): Boolean
    /** Start sample and length of each completed segment, removing them from the native queue. */
    protected abstract fun drainSegments(): List<Pair<Int, Int>>
    protected abstract fun flushNative()

    final override fun accept(samples: FloatArray): Boolean {
        var offset = 0
        while (offset < samples.size) {
            val count = minOf(SileroSettings.WINDOW - filled, samples.size - offset)
            samples.copyInto(pending, filled, offset, offset + count)
            filled += count
            offset += count
            if (filled == SileroSettings.WINDOW) {
                acceptWindow(pending.copyOf())
                filled = 0
                collect()
            }
        }
        return speechDetected()
    }

    final override fun finish(): List<IntRange> {
        if (filled > 0) {
            acceptWindow(pending.copyOf().also { it.fill(0f, filled) })
            filled = 0
        }
        flushNative()
        collect()
        return spans.toList()
    }

    private fun collect() {
        drainSegments().forEach { (start, length) -> if (length > 0) spans += start until start + length }
    }
}

object SpeechAudio {
    const val SAMPLE_RATE = 16_000

    fun pcm16ToFloat(pcm: ByteArray, count: Int = pcm.size): FloatArray {
        require(count in 0..pcm.size)
        return FloatArray(count / 2) { i -> ((pcm[2 * i].toInt() and 255) or (pcm[2 * i + 1].toInt() shl 8)).toShort() / 32768f }
    }

    /**
     * Keeps the speech spans of 16 kHz PCM16 mono audio, each widened by [paddingMillis] and merged where they
     * overlap, so leading/trailing noise never reaches recognition. Null when the spans hold less than
     * [minSpeechMillis] of speech: the recording only contained noise or silence.
     */
    fun keepSpeech(pcm: ByteArray, spans: List<IntRange>, paddingMillis: Int = 200, minSpeechMillis: Int = 250): ByteArray? {
        val samples = pcm.size / 2
        val speech = spans.sumOf { (minOf(it.last, samples - 1) - maxOf(it.first, 0) + 1).coerceAtLeast(0) }
        if (speech < minSpeechMillis * SAMPLE_RATE / 1000) return null
        val padding = paddingMillis * SAMPLE_RATE / 1000
        val merged = ArrayList<IntRange>()
        for (span in spans.sortedBy { it.first }) {
            val start = (span.first - padding).coerceAtLeast(0)
            val end = (span.last + padding).coerceAtMost(samples - 1)
            if (start > end) continue
            val last = merged.lastOrNull()
            if (last != null && start <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, end)
            else merged += start..end
        }
        val output = ByteArray(merged.sumOf { (it.last - it.first + 1) * 2 })
        var at = 0
        for (range in merged) {
            val bytes = (range.last - range.first + 1) * 2
            pcm.copyInto(output, at, range.first * 2, range.first * 2 + bytes)
            at += bytes
        }
        return output
    }
}

/** Recognizers produce text such as "." or "。" from noise; a real utterance contains a letter or digit. */
fun String.hasSpokenContent(): Boolean = any { it.isLetterOrDigit() }
