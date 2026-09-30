package com.aifacemate.face.behavior

import com.aifacemate.face.model.FaceExpression

enum class InteractionPhase { Idle, Listening, Processing, Speaking }

sealed interface BehaviorEvent {
    data class PhaseChanged(val phase: InteractionPhase) : BehaviorEvent
    data class BaseChanged(val expression: FaceExpression) : BehaviorEvent
    data object Petted : BehaviorEvent
    data class UserText(val text: String) : BehaviorEvent
    /** Adapter supplies filtered evidence; capturedAtMillis uses the controller's monotonic clock. */
    data class Vision(val personVisible: Boolean, val thumbsUp: Boolean, val capturedAtMillis: Long) : BehaviorEvent
    data object VisionStopped : BehaviorEvent
    data object ResetInteraction : BehaviorEvent
}

data class BehaviorState(
    val expression: FaceExpression,
    val phase: InteractionPhase,
    val reason: String,
    val expiresAtMillis: Long? = null,
)

/** Single-owner deterministic rules. No hardware, network, wall clock or UI lifecycle dependencies. */
class BehaviorController {
    private var base = FaceExpression.IDLE
    private var phase = InteractionPhase.Idle
    private data class Reaction(val expression: FaceExpression, val reason: String, val priority: Int, val until: Long)
    private var reaction: Reaction? = null
    private val cooldowns = mutableMapOf<String, Long>()
    private var personFrames = 0
    private var gestureFrames = 0
    private var personPresent = false
    private var gesturePresent = false
    private var lastPersonAt: Long? = null
    private var lastVisionAt: Long? = null

    fun accept(event: BehaviorEvent, nowMillis: Long): BehaviorState {
        expire(nowMillis)
        when (event) {
            is BehaviorEvent.BaseChanged -> base = event.expression
            is BehaviorEvent.PhaseChanged -> phase = event.phase
            BehaviorEvent.Petted -> react(FaceExpression.HAPPY, "petting", 30, 2400, 500, nowMillis)
            is BehaviorEvent.UserText -> {
                val text = event.text.trim().lowercase()
                when {
                    listOf("难过", "伤心", "不开心", "心情不好").any { it in text } ->
                        react(FaceExpression.COMFORTING, "comfort", 20, 8000, 1000, nowMillis)
                    listOf("真可爱", "好可爱", "你很棒", "真棒", "喜欢你").any { it in text } ->
                        react(FaceExpression.BASHFUL_SMILE, "praise", 20, 8000, 1000, nowMillis)
                    listOf("你好", "早上好", "晚上好", "hello").any { it in text } ->
                        react(FaceExpression.GREETING, "greeting_text", 20, 5000, 3000, nowMillis)
                }
            }
            is BehaviorEvent.Vision -> {
                if (event.capturedAtMillis > nowMillis || nowMillis - event.capturedAtMillis > 1000 ||
                    lastVisionAt?.let { event.capturedAtMillis <= it } == true) return state(nowMillis)
                if (lastVisionAt?.let { event.capturedAtMillis - it > 1000 } == true) clearVision()
                lastVisionAt = event.capturedAtMillis
                if (event.personVisible) {
                    lastPersonAt = nowMillis
                    personFrames++
                    if (!personPresent && personFrames >= 3) {
                        personPresent = true
                        if (phase == InteractionPhase.Idle) react(FaceExpression.GREETING, "person_arrived", 10, 2400, 15000, nowMillis)
                    }
                } else personFrames = 0
                if (event.thumbsUp) {
                    gestureFrames++
                    if (!gesturePresent && gestureFrames >= 3) {
                        gesturePresent = true
                        if (phase == InteractionPhase.Idle) react(FaceExpression.APPROVING, "thumbs_up", 10, 2400, 5000, nowMillis)
                    }
                } else { gestureFrames = 0; gesturePresent = false }
            }
            BehaviorEvent.VisionStopped -> clearVision()
            BehaviorEvent.ResetInteraction -> { phase = InteractionPhase.Idle; reaction = null; clearVision() }
        }
        return state(nowMillis)
    }

    fun state(nowMillis: Long): BehaviorState {
        expire(nowMillis)
        val expression = when (phase) {
            InteractionPhase.Listening -> FaceExpression.LISTENING
            InteractionPhase.Processing -> FaceExpression.THINKING
            InteractionPhase.Speaking -> FaceExpression.SPEAKING
            InteractionPhase.Idle -> reaction?.expression ?: base
        }
        return BehaviorState(expression, phase,
            if (phase != InteractionPhase.Idle) phase.name.lowercase() else reaction?.reason ?: "base",
            if (phase == InteractionPhase.Idle) reaction?.until else null)
    }

    private fun expire(now: Long) {
        if (reaction?.let { now >= it.until } == true) reaction = null
        if (lastPersonAt?.let { now - it >= 2000 } == true) { personPresent = false; personFrames = 0; lastPersonAt = null }
        if (lastVisionAt?.let { now - it > 1000 } == true) clearVision()
    }

    private fun react(expression: FaceExpression, reason: String, priority: Int, duration: Long, cooldown: Long, now: Long) {
        if (cooldowns[reason]?.let { now < it } == true || reaction?.let { it.priority > priority } == true) return
        reaction = Reaction(expression, reason, priority, now + duration)
        cooldowns[reason] = now + cooldown
    }

    private fun clearVision() {
        personFrames = 0; gestureFrames = 0; personPresent = false; gesturePresent = false
        lastPersonAt = null; lastVisionAt = null
    }
}
