package com.aifacemate.face.behavior.proactive

enum class ListeningDecision { Continue, Submit, NoResponse }

/**
 * One automatic listening window. Speech evidence comes from a voice activity detector when the recorder has
 * one; otherwise loudness must rise clearly above the room's own noise for a moment, so a fan, a television hum
 * or a single knock does not count as the user talking. Silence is activity evidence, not an emotion.
 */
class ListeningWindow(private val beganAt: Long, private val segmented: Boolean) {
    private var lastSpeechAt: Long? = null
    private var detectorPresent = false
    private var noiseFloor: Float? = null
    private var loudSince: Long? = null

    /** Voice activity detector state; once seen, loudness is no longer used as evidence. */
    fun voice(active: Boolean, now: Long) {
        detectorPresent = true
        if (active) lastSpeechAt = now
    }

    fun activity(level: Float, now: Long) {
        if (detectorPresent || !level.isFinite()) return
        val floor = noiseFloor?.let { if (level < it) level else it + (level - it) * FLOOR_RISE } ?: level
        noiseFloor = floor
        if (level >= maxOf(MIN_LEVEL, floor + MARGIN)) {
            val since = loudSince ?: now.also { loudSince = it }
            if (now - since >= SUSTAINED_MS) lastSpeechAt = now
        } else loudSince = null
    }

    fun text(text: String, now: Long) { if (text.isNotBlank()) lastSpeechAt = now }

    fun decision(now: Long): ListeningDecision {
        val last = lastSpeechAt
        return when {
            last == null && now - beganAt >= 12_000 -> ListeningDecision.NoResponse
            segmented && last != null && now - last >= 1400 -> ListeningDecision.Submit
            now - beganAt >= 30_000 -> if (segmented && last != null) ListeningDecision.Submit else ListeningDecision.NoResponse
            else -> ListeningDecision.Continue
        }
    }

    private companion object {
        const val MIN_LEVEL = .08f
        const val MARGIN = .15f
        /** The floor drops at once to quieter levels and rises slowly, so it follows the room, not the voice. */
        const val FLOOR_RISE = .01f
        const val SUSTAINED_MS = 200L
    }
}
