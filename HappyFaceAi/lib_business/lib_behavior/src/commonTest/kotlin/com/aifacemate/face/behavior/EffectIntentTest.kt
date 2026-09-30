package com.aifacemate.face.behavior

import kotlin.test.*

class EffectIntentTest {
    @Test fun acceptedGestureEmitsOneStableIntentAcrossSubsequentFrames() {
        val controller = BehaviorController()
        for (time in 0L..400L step 200) controller.accept(BehaviorEvent.Vision(null, false, time, VisualGesture.Victory), time)
        val intent = assertNotNull(controller.state(400).effectIntent)
        assertEquals(BehaviorEffectCue.Celebration, intent.cue)
        for (time in 600L..1200L step 200) assertEquals(intent, controller.accept(
            BehaviorEvent.Vision(null, false, time, VisualGesture.Victory), time).effectIntent)
        assertNull(controller.state(2000).effectIntent)
    }
    @Test fun provisionalAndVisualSadnessDoNotEmitRainButConfirmedWordsDo() {
        val controller = BehaviorController()
        for (time in 0L..400L step 200) controller.accept(BehaviorEvent.Vision(null, null, time,
            faceAnalyzed = true, appearance = FacialAppearance.Sad), time)
        assertNull(controller.state(400).effectIntent)
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Listening), 500)
        assertNull(controller.accept(BehaviorEvent.ListeningText("我很难过"), 600).effectIntent)
        assertEquals(BehaviorEffectCue.Comfort, controller.accept(BehaviorEvent.UserText("我很难过"), 700).effectIntent?.cue)
        assertNull(controller.accept(BehaviorEvent.VoiceCancelled, 800).effectIntent)
    }
    @Test fun praiseAndGoodNewsUseIndependentSequences() {
        val controller = BehaviorController()
        val love = assertNotNull(controller.accept(BehaviorEvent.UserText("你真可爱"), 0).effectIntent)
        assertEquals(BehaviorEffectCue.Affection, love.cue)
        val party = assertNotNull(controller.accept(BehaviorEvent.UserText("我成功了"), 1500).effectIntent)
        assertEquals(BehaviorEffectCue.Celebration, party.cue)
        assertTrue(party.sequence > love.sequence)
        assertNull(controller.accept(BehaviorEvent.ResetInteraction, 1600).effectIntent)
    }
}
