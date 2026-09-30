package com.aifacemate.face.behavior

import com.aifacemate.face.model.FaceExpression
import kotlin.test.*

class BehaviorControllerTest {
    @Test fun gestureConfirmedDuringCooldownCanRespondOnceCooldownEnds() {
        val controller = BehaviorController()
        fun frame(time: Long, thumbs: Boolean = true) = controller.accept(BehaviorEvent.Vision(false, thumbs, time), time)
        frame(0); frame(200); frame(400)
        frame(600, false)
        for (time in 800L..5200L step 200) frame(time)
        assertEquals(FaceExpression.APPROVING, frame(5400).expression)
        for (time in 5600L..11000L step 200) frame(time)
        assertEquals(FaceExpression.IDLE, controller.state(11000).expression)
    }

    @Test fun visionBlockedByPettingIsNotMarkedAsAlreadyAcknowledged() {
        val controller = BehaviorController()
        controller.accept(BehaviorEvent.Petted, 0)
        for (time in 0L..2200L step 200) controller.accept(BehaviorEvent.Vision(false, true, time), time)
        assertEquals(FaceExpression.APPROVING, controller.accept(BehaviorEvent.Vision(false, true, 2400), 2400).expression)
    }

    @Test fun arrivalRearmsAfterAbsenceAndCooldown() {
        val controller = BehaviorController()
        fun frame(time: Long, person: Boolean) = controller.accept(BehaviorEvent.Vision(person, false, time), time)
        frame(0, true); frame(200, true); frame(400, true)
        for (time in 600L..3000L step 200) frame(time, false)
        frame(3200, true); frame(3400, true)
        assertEquals(FaceExpression.IDLE, frame(3600, true).expression)
        for (time in 3800L..16000L step 200) frame(time, false)
        frame(16200, true); frame(16400, true)
        assertEquals(FaceExpression.GREETING, frame(16600, true).expression)
    }

    @Test fun staleFutureAndInterruptedFramesDoNotConfirmGesture() {
        val controller = BehaviorController()
        controller.accept(BehaviorEvent.Vision(false, true, 100), 100)
        controller.accept(BehaviorEvent.Vision(false, true, 5000), 200)
        controller.accept(BehaviorEvent.Vision(false, true, 200), 1500)
        controller.accept(BehaviorEvent.Vision(false, true, 1700), 1700)
        assertEquals(FaceExpression.IDLE, controller.accept(BehaviorEvent.Vision(false, true, 1900), 1900).expression)
        assertEquals(FaceExpression.APPROVING, controller.accept(BehaviorEvent.Vision(false, true, 2100), 2100).expression)
    }

    @Test fun phasesAndReactionExpiryRestoreSelectedBase() {
        val controller = BehaviorController()
        controller.accept(BehaviorEvent.BaseChanged(FaceExpression.CURIOUS), 0)
        controller.accept(BehaviorEvent.Petted, 0)
        assertEquals(FaceExpression.HAPPY, controller.state(100).expression)
        assertEquals(FaceExpression.THINKING, controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Processing), 200).expression)
        assertEquals(FaceExpression.SPEAKING, controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Speaking), 300).expression)
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Idle), 2400)
        assertEquals(FaceExpression.CURIOUS, controller.state(2400).expression)
    }

    @Test fun arrivalRequiresFreshDistinctConsecutiveFramesAndDoesNotRepeat() {
        val controller = BehaviorController()
        fun frame(time: Long, person: Boolean = true) = controller.accept(BehaviorEvent.Vision(person, false, time), time)
        frame(0); frame(0)
        assertEquals(FaceExpression.IDLE, frame(200).expression)
        assertEquals(FaceExpression.GREETING, frame(400).expression)
        for (time in 600L..4000L step 200) frame(time)
        assertEquals(FaceExpression.IDLE, controller.state(4000).expression)
        frame(4200, false); frame(4400, false)
        assertEquals(FaceExpression.IDLE, controller.accept(BehaviorEvent.Vision(true, true, 0), 5000).expression)
    }

    @Test fun gestureConfirmationCooldownAndPriority() {
        val controller = BehaviorController()
        fun frame(time: Long, thumbs: Boolean) = controller.accept(BehaviorEvent.Vision(false, thumbs, time), time)
        frame(0, true); frame(200, true)
        assertEquals(FaceExpression.APPROVING, frame(400, true).expression)
        controller.accept(BehaviorEvent.Petted, 500)
        frame(600, false); frame(800, true); frame(1000, true)
        assertEquals(FaceExpression.HAPPY, frame(1200, true).expression)
        assertEquals(FaceExpression.IDLE, controller.state(2900).expression)
    }

    @Test fun textReactionIsDeferredByPhaseAndResetClearsIt() {
        val controller = BehaviorController()
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Processing), 0)
        assertEquals(FaceExpression.THINKING, controller.accept(BehaviorEvent.UserText("你真可爱"), 100).expression)
        assertEquals(FaceExpression.BASHFUL_SMILE, controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Idle), 1000).expression)
        assertEquals(FaceExpression.IDLE, controller.accept(BehaviorEvent.ResetInteraction, 1100).expression)
        assertEquals(FaceExpression.COMFORTING, controller.accept(BehaviorEvent.UserText("我不开心"), 1200).expression)
    }

    @Test fun interruptedEvidenceAndConversationDoNotProduceArrival() {
        val controller = BehaviorController()
        controller.accept(BehaviorEvent.Vision(true, false, 0), 0)
        controller.accept(BehaviorEvent.VisionStopped, 100)
        controller.accept(BehaviorEvent.Vision(true, false, 200), 200)
        assertEquals(FaceExpression.IDLE, controller.accept(BehaviorEvent.Vision(true, false, 400), 400).expression)
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Listening), 450)
        controller.accept(BehaviorEvent.Vision(true, false, 600), 600)
        assertEquals(FaceExpression.IDLE, controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Idle), 700).expression)
    }
}
