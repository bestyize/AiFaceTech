package com.aifacemate.face.behavior.proactive

import com.aifacemate.face.behavior.BehaviorEvent
import com.aifacemate.face.behavior.FacialAppearance

data class ProactiveOpening(val id: Long, val text: String)

/** Independent trigger policy. Observations are visual cues, not a diagnosis of the person's emotions. */
class ProactiveConversationController {
    private var firstSmileAt: Long? = null
    private var lastSampleAt: Long? = null
    private var confirmations = 0
    private var armed = true
    private var nextAllowedAt = 0L
    private var sequence = 0L

    fun observe(event: BehaviorEvent.Vision, eligible: Boolean, nowMillis: Long): ProactiveOpening? {
        if (!event.faceAnalyzed || event.capturedAtMillis > nowMillis || nowMillis - event.capturedAtMillis > 1000 ||
            lastSampleAt?.let { event.capturedAtMillis <= it } == true) return null
        if (lastSampleAt?.let { event.capturedAtMillis - it > 1500 } == true) resetEvidence()
        lastSampleAt = event.capturedAtMillis
        if (event.appearance != FacialAppearance.Smile || event.personVisible != true) {
            armed = true; resetEvidence(); return null
        }
        if (!eligible) { resetEvidence(); return null }
        if (!armed || nowMillis < nextAllowedAt) return null
        val began = firstSmileAt ?: event.capturedAtMillis.also { firstSmileAt = it }
        confirmations++
        if (confirmations < 3 || event.capturedAtMillis - began < 1200) return null
        armed = false
        nextAllowedAt = nowMillis + 60_000
        return ProactiveOpening(++sequence, RuleOpeningTextGenerator.generate(FacialAppearance.Smile))
    }

    fun resetEvidence() { firstSmileAt = null; confirmations = 0 }
    fun conversationEnded(nowMillis: Long) {
        nextAllowedAt = maxOf(nextAllowedAt, nowMillis + 60_000)
        armed = false
        resetEvidence()
    }
}

/** Replaceable wording strategy; a future implementation can generate text through the configured LLM. */
interface OpeningTextGenerator { fun generate(cue: FacialAppearance): String }

object RuleOpeningTextGenerator : OpeningTextGenerator {
    override fun generate(cue: FacialAppearance): String = when (cue) {
        FacialAppearance.Smile -> "哈哈，看你笑得很开心，遇到什么开心的事了吗？"
        FacialAppearance.Sad -> "想不想聊一聊？我在这里听你说。"
        FacialAppearance.Surprise -> "看到什么有趣的事了吗？愿意和我分享一下吗？"
    }
}
