package com.aifacemate.face.behavior.proactive

enum class ListeningDecision { Continue, Submit, NoResponse }

/** Initial automatic listen only. A finite window; silence is activity evidence, not an emotion. */
class ListeningWindow(private val beganAt: Long, private val segmented: Boolean) {
    private var lastSpeechAt: Long? = null
    fun activity(level: Float, now: Long) {
        if (level.isFinite() && level >= .08f) lastSpeechAt = now
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
}
