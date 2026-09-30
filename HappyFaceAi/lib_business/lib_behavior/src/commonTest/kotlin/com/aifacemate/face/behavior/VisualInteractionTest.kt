package com.aifacemate.face.behavior

import com.aifacemate.face.model.FaceExpression
import kotlin.test.*

class VisualInteractionTest {
    @Test fun allStaticGesturesHaveResponsesAndRequireConfirmation() {
        val expected = mapOf(VisualGesture.ThumbsUp to FaceExpression.APPROVING,
            VisualGesture.OpenPalm to FaceExpression.GREETING, VisualGesture.Victory to FaceExpression.CELEBRATING,
            VisualGesture.Fist to FaceExpression.PLAYFUL, VisualGesture.Pointing to FaceExpression.CURIOUS,
            VisualGesture.Pinch to FaceExpression.WINK)
        for ((gesture, expression) in expected) {
            val controller = BehaviorController()
            fun frame(time: Long) = controller.accept(BehaviorEvent.Vision(null, gesture == VisualGesture.ThumbsUp, time, gesture), time)
            frame(0)
            assertEquals(FaceExpression.IDLE, frame(300).expression)
            assertEquals(expression, frame(600).expression)
            for (time in 900L..6000L step 300) frame(time)
            assertEquals(FaceExpression.IDLE, controller.state(6000).expression)
        }
    }

    @Test fun changingGestureNeedsItsOwnConfirmation() {
        val controller = BehaviorController()
        controller.accept(BehaviorEvent.Vision(null, false, 0, VisualGesture.OpenPalm), 0)
        controller.accept(BehaviorEvent.Vision(null, false, 300, VisualGesture.Victory), 300)
        assertEquals(FaceExpression.IDLE, controller.accept(BehaviorEvent.Vision(null, false, 600, VisualGesture.Victory), 600).expression)
        assertEquals(FaceExpression.CELEBRATING, controller.accept(BehaviorEvent.Vision(null, false, 900, VisualGesture.Victory), 900).expression)
    }

    @Test fun visibleFaceCuesProduceResponsesWithoutInferringEmotion() {
        for ((appearance, expression) in mapOf(FacialAppearance.Smile to FaceExpression.HAPPY,
            FacialAppearance.Sad to FaceExpression.COMFORTING, FacialAppearance.Surprise to FaceExpression.CURIOUS)) {
            val controller = BehaviorController()
            for (time in 0L..600L step 300) controller.accept(
                BehaviorEvent.Vision(null, null, time, faceAnalyzed = true, appearance = appearance), time)
            assertEquals(expression, controller.state(600).expression)
        }
    }

    @Test fun gesturesDoNotInterruptListening() {
        val controller = BehaviorController()
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Listening), 0)
        for (time in 0L..600L step 300) controller.accept(BehaviorEvent.Vision(null, false, time, VisualGesture.Victory), time)
        assertEquals(FaceExpression.LISTENING, controller.state(600).expression)
        assertEquals(FaceExpression.IDLE, controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Idle), 700).expression)
    }
}
