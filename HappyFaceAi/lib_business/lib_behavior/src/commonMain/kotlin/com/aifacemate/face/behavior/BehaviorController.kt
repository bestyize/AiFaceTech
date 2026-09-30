package com.aifacemate.face.behavior

import com.aifacemate.face.model.FaceExpression

enum class InteractionPhase { Idle, Listening, Processing, Speaking }
enum class VisualGesture { ThumbsUp, OpenPalm, Victory, Fist, Pointing, Pinch }
enum class FacialAppearance { Smile, Sad, Surprise }
enum class BehaviorEffectCue { Celebration, Affection, Comfort, Reward }
data class BehaviorEffectIntent(val sequence: Long, val cue: BehaviorEffectCue, val createdAtMillis: Long, val priority: Int)

sealed interface BehaviorEvent {
    data class PhaseChanged(val phase: InteractionPhase) : BehaviorEvent
    data class BaseChanged(val expression: FaceExpression) : BehaviorEvent
    data object Petted : BehaviorEvent
    data class UserText(val text: String) : BehaviorEvent
    data class ListeningText(val text: String) : BehaviorEvent
    data class SpeechLevel(val level: Float) : BehaviorEvent
    data object VoiceCancelled : BehaviorEvent
    /** Adapter supplies filtered evidence; capturedAtMillis uses the controller's monotonic clock. */
    data class Vision(
        val personVisible: Boolean?, val thumbsUp: Boolean?, val capturedAtMillis: Long,
        val gesture: VisualGesture? = null,
        val faceAnalyzed: Boolean = false,
        val appearance: FacialAppearance? = null,
    ) : BehaviorEvent
    data object VisionStopped : BehaviorEvent
    data object ResetInteraction : BehaviorEvent
}

data class BehaviorState(
    val expression: FaceExpression,
    val phase: InteractionPhase,
    val reason: String,
    val expiresAtMillis: Long? = null,
    val listeningActivity: Float = 0f,
    val effectIntent: BehaviorEffectIntent? = null,
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
    private var lastPersonSampleAt: Long? = null
    private var lastGestureSampleAt: Long? = null
    private var lastGestureReceivedAt: Long? = null
    private var gestureCandidate: VisualGesture? = null
    private var faceCandidate: FacialAppearance? = null
    private var faceFrames = 0
    private var faceResponded = false
    private var lastFaceSampleAt: Long? = null
    private var listeningMeaning: SpeechMeaning? = null
    private var listeningUntil = 0L
    private var speechLevel = 0f
    private var levelAt: Long? = null
    private var pendingReplyMeaning: SpeechMeaning? = null
    private var pendingReplyUntil = 0L
    private var effectSequence = 0L
    private var effectIntent: BehaviorEffectIntent? = null

    fun accept(event: BehaviorEvent, nowMillis: Long): BehaviorState {
        expire(nowMillis)
        when (event) {
            is BehaviorEvent.BaseChanged -> base = event.expression
            is BehaviorEvent.PhaseChanged -> {
                val previous = phase
                phase = event.phase
                if (previous != phase) clearListening()
                if (phase == InteractionPhase.Listening && previous != phase) {
                    if (previous == InteractionPhase.Speaking && nowMillis < pendingReplyUntil) {
                        listeningMeaning = pendingReplyMeaning
                        listeningUntil = nowMillis + 2400
                    }
                    pendingReplyMeaning = null
                }
                if (phase == InteractionPhase.Idle && previous != phase) {
                    pendingReplyMeaning?.takeIf { nowMillis < pendingReplyUntil }?.let {
                        if (reaction?.priority?.let { priority -> priority > 20 } != true)
                            reaction = Reaction(it.expression, it.reason, 20, nowMillis + 2400)
                    }
                    pendingReplyMeaning = null
                }
            }
            BehaviorEvent.Petted -> react(FaceExpression.HAPPY, "petting", 30, 2400, 500, nowMillis)
            is BehaviorEvent.UserText -> {
                if (reaction?.priority == 20) reaction = null
                pendingReplyMeaning = speechMeaning(event.text)
                pendingReplyUntil = nowMillis + 60_000
                pendingReplyMeaning?.let {
                    react(it.expression, it.reason, 20, 8000, 1000, nowMillis)
                    if (phase == InteractionPhase.Listening) {
                        listeningMeaning = it; listeningUntil = nowMillis + 2500
                    }
                }
            }
            is BehaviorEvent.ListeningText -> if (phase == InteractionPhase.Listening) {
                listeningMeaning = speechMeaning(event.text)
                listeningUntil = nowMillis + 2500
            }
            is BehaviorEvent.SpeechLevel -> if (phase == InteractionPhase.Listening && event.level.isFinite()) {
                speechLevel = speechLevel * .55f + event.level.coerceIn(0f, 1f) * .45f
                levelAt = nowMillis
            }
            is BehaviorEvent.Vision -> {
                if (event.capturedAtMillis > nowMillis || nowMillis - event.capturedAtMillis > 1000) return state(nowMillis)
                if (event.personVisible != null && lastPersonSampleAt?.let { event.capturedAtMillis <= it } != true) {
                    if (lastPersonSampleAt?.let { event.capturedAtMillis - it > 2500 } == true) personFrames = 0
                    lastPersonSampleAt = event.capturedAtMillis
                    if (event.personVisible) {
                        lastPersonAt = nowMillis
                        personFrames++
                        if (!personPresent && personFrames >= 3) {
                            personPresent = phase != InteractionPhase.Idle ||
                                react(FaceExpression.GREETING, "person_arrived", 10, 2400, 5000, nowMillis)
                        }
                    } else personFrames = 0
                }
                if (event.thumbsUp != null && lastGestureSampleAt?.let { event.capturedAtMillis <= it } != true) {
                    if (lastGestureSampleAt?.let { event.capturedAtMillis - it > 1500 } == true) gestureFrames = 0
                    lastGestureSampleAt = event.capturedAtMillis
                    lastGestureReceivedAt = nowMillis
                    val gesture = event.gesture ?: if (event.thumbsUp) VisualGesture.ThumbsUp else null
                    if (gesture != gestureCandidate) {
                        gestureCandidate = gesture; gestureFrames = 0; gesturePresent = false
                    }
                    if (gesture != null) {
                        gestureFrames++
                        if (!gesturePresent && gestureFrames >= 3) {
                            gesturePresent = phase != InteractionPhase.Idle ||
                                react(gesture.expression(), gesture.reason(), 10, 2400, 2000, nowMillis)
                        }
                    } else { gestureFrames = 0; gesturePresent = false }
                }
                if (event.faceAnalyzed && lastFaceSampleAt?.let { event.capturedAtMillis <= it } != true) {
                    if (lastFaceSampleAt?.let { event.capturedAtMillis - it > 1500 } == true) {
                        faceFrames = 0; faceResponded = false
                    }
                    lastFaceSampleAt = event.capturedAtMillis
                    if (event.appearance != faceCandidate) {
                        faceCandidate = event.appearance; faceFrames = 0; faceResponded = false
                    }
                    val appearance = event.appearance
                    if (appearance != null && ++faceFrames >= 3 && !faceResponded) {
                        faceResponded = phase != InteractionPhase.Idle ||
                            react(appearance.expression(), "face_${appearance.name.lowercase()}", 5, 2400, 3000, nowMillis)
                    }
                }
            }
            BehaviorEvent.VisionStopped -> clearVision()
            BehaviorEvent.VoiceCancelled -> {
                effectIntent = null
                phase = InteractionPhase.Idle; pendingReplyMeaning = null; clearListening()
                if (reaction?.priority == 20) reaction = null
            }
            BehaviorEvent.ResetInteraction -> {
                effectIntent = null
                phase = InteractionPhase.Idle; reaction = null; pendingReplyMeaning = null
                clearListening(); clearVision()
            }
        }
        return state(nowMillis)
    }

    fun state(nowMillis: Long): BehaviorState {
        expire(nowMillis)
        val expression = when (phase) {
            InteractionPhase.Listening -> listeningMeaning?.expression ?: FaceExpression.LISTENING
            InteractionPhase.Processing -> FaceExpression.THINKING
            InteractionPhase.Speaking -> FaceExpression.SPEAKING
            InteractionPhase.Idle -> reaction?.expression ?: base
        }
        return BehaviorState(expression, phase,
            if (phase == InteractionPhase.Listening && listeningMeaning != null) "listening_${listeningMeaning!!.reason}"
            else if (phase != InteractionPhase.Idle) phase.name.lowercase() else reaction?.reason ?: "base",
            if (phase == InteractionPhase.Idle) reaction?.until else null,
            if (phase == InteractionPhase.Listening) levelAt?.let { speechLevel * (1f - (nowMillis - it).coerceAtLeast(0) / 400f).coerceIn(0f, 1f) } ?: 0f else 0f,
            effectIntent?.takeIf { nowMillis - it.createdAtMillis in 0..1500 })
    }

    private fun expire(now: Long) {
        if (reaction?.let { now >= it.until } == true) reaction = null
        if (now >= listeningUntil) listeningMeaning = null
        if (lastPersonAt?.let { now - it >= 2000 } == true) { personPresent = false; personFrames = 0; lastPersonAt = null }
        if (lastGestureReceivedAt?.let { now - it > 1500 } == true) {
            gestureFrames = 0; gesturePresent = false; gestureCandidate = null; lastGestureReceivedAt = null
        }
        if (lastFaceSampleAt?.let { now - it > 1500 } == true) {
            faceCandidate = null; faceFrames = 0; faceResponded = false
        }
    }

    private fun react(expression: FaceExpression, reason: String, priority: Int, duration: Long, cooldown: Long, now: Long): Boolean {
        if (cooldowns[reason]?.let { now < it } == true || reaction?.let { it.priority > priority } == true) return false
        reaction = Reaction(expression, reason, priority, now + duration)
        val cue = when (reason) {
            "gesture_victory", "good_news" -> BehaviorEffectCue.Celebration
            "praise", "petting" -> BehaviorEffectCue.Affection
            "comfort" -> BehaviorEffectCue.Comfort
            "thumbs_up" -> BehaviorEffectCue.Reward
            else -> null
        }
        if (cue != null) effectIntent = BehaviorEffectIntent(++effectSequence, cue, now, priority)
        cooldowns[reason] = now + cooldown
        return true
    }

    private fun clearVision() {
        personFrames = 0; gestureFrames = 0; personPresent = false; gesturePresent = false
        lastPersonAt = null; lastPersonSampleAt = null; lastGestureSampleAt = null; lastGestureReceivedAt = null
        gestureCandidate = null; faceCandidate = null; faceFrames = 0; faceResponded = false; lastFaceSampleAt = null
    }
    private fun clearListening() { listeningMeaning = null; speechLevel = 0f; levelAt = null }
}

private fun VisualGesture.expression() = when (this) {
    VisualGesture.ThumbsUp -> FaceExpression.APPROVING
    VisualGesture.OpenPalm -> FaceExpression.GREETING
    VisualGesture.Victory -> FaceExpression.CELEBRATING
    VisualGesture.Fist -> FaceExpression.PLAYFUL
    VisualGesture.Pointing -> FaceExpression.CURIOUS
    VisualGesture.Pinch -> FaceExpression.WINK
}
private fun VisualGesture.reason() = if (this == VisualGesture.ThumbsUp) "thumbs_up" else "gesture_${name.lowercase()}"
private fun FacialAppearance.expression() = when (this) {
    FacialAppearance.Smile -> FaceExpression.HAPPY
    FacialAppearance.Sad -> FaceExpression.COMFORTING
    FacialAppearance.Surprise -> FaceExpression.CURIOUS
}
