package com.aifacemate.face.behavior

import com.aifacemate.face.model.FaceExpression
import kotlin.test.*

class VoiceInteractionTest {
    @Test fun stoppedReplyDoesNotReplayConfirmedEmotion() {
        val controller = BehaviorController()
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Processing), 0)
        controller.accept(BehaviorEvent.UserText("谢谢"), 100)
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Speaking), 200)
        controller.accept(BehaviorEvent.VoiceCancelled, 300)
        assertEquals(FaceExpression.IDLE, controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Idle), 400).expression)
    }
    @Test fun provisionalContentChangesWhileListeningAndRevisionRetractsIt() {
        val controller = BehaviorController()
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Listening), 0)
        assertEquals(FaceExpression.BASHFUL_SMILE, controller.accept(BehaviorEvent.ListeningText("你真可爱"), 100).expression)
        assertEquals(InteractionPhase.Listening, controller.state(100).phase)
        assertEquals(FaceExpression.LISTENING, controller.accept(BehaviorEvent.ListeningText("你不可爱"), 200).expression)
        controller.accept(BehaviorEvent.ListeningText("我很难过"), 300)
        assertEquals(FaceExpression.COMFORTING, controller.state(300).expression)
        assertEquals(FaceExpression.LISTENING, controller.state(2800).expression)
    }

    @Test fun stalePartialEventsCannotChangeProcessingOrNextTurn() {
        val controller = BehaviorController()
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Listening), 0)
        controller.accept(BehaviorEvent.ListeningText("我很难过"), 100)
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Processing), 200)
        assertEquals(FaceExpression.THINKING, controller.accept(BehaviorEvent.ListeningText("你真可爱"), 300).expression)
        assertEquals(FaceExpression.LISTENING, controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Listening), 400).expression)
    }

    @Test fun finalMeaningSurvivesLongReplyButNotCancellation() {
        val controller = BehaviorController()
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Processing), 0)
        controller.accept(BehaviorEvent.UserText("谢谢你"), 100)
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Speaking), 1000)
        assertEquals(FaceExpression.SPEAKING, controller.state(12000).expression)
        assertEquals(FaceExpression.GRATEFUL, controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Idle), 15000).expression)
        assertEquals(FaceExpression.IDLE, controller.accept(BehaviorEvent.ResetInteraction, 15100).expression)
    }

    @Test fun realtimeReplyCanReturnToListeningWithBriefContentResponse() {
        val controller = BehaviorController()
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Listening), 0)
        controller.accept(BehaviorEvent.UserText("好消息，我成功了"), 100)
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Speaking), 200)
        assertEquals(FaceExpression.HAPPY, controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Listening), 10000).expression)
        assertEquals(FaceExpression.LISTENING, controller.accept(BehaviorEvent.ListeningText("帮我查一下"), 10100).expression)
    }

    @Test fun levelIsFiniteBoundedDecaysAndOnlyAffectsListening() {
        val controller = BehaviorController()
        assertEquals(0f, controller.accept(BehaviorEvent.SpeechLevel(1f), 0).listeningActivity)
        controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Listening), 0)
        val active = controller.accept(BehaviorEvent.SpeechLevel(10f), 100).listeningActivity
        assertTrue(active > 0f && active <= 1f)
        assertEquals(active, controller.accept(BehaviorEvent.SpeechLevel(Float.NaN), 100).listeningActivity)
        assertEquals(0f, controller.state(500).listeningActivity)
        controller.accept(BehaviorEvent.SpeechLevel(1f), 600)
        assertEquals(0f, controller.accept(BehaviorEvent.PhaseChanged(InteractionPhase.Processing), 700).listeningActivity)
    }

    @Test fun confirmedTextSupportsSevenIntentsAndExplicitNegation() {
        val expected = mapOf("你好" to FaceExpression.GREETING, "你真可爱" to FaceExpression.BASHFUL_SMILE,
            "我不开心" to FaceExpression.COMFORTING, "我很开心" to FaceExpression.HAPPY,
            "谢谢" to FaceExpression.GRATEFUL, "对不起" to FaceExpression.REASSURED, "晚安" to FaceExpression.GOOD_NIGHT)
        for ((text, expression) in expected) assertEquals(expression, BehaviorController().accept(BehaviorEvent.UserText(text), 0).expression)
        assertEquals(FaceExpression.IDLE, BehaviorController().accept(BehaviorEvent.UserText("我不难过"), 0).expression)
        assertEquals(FaceExpression.IDLE, BehaviorController().accept(BehaviorEvent.UserText("我不喜欢你"), 0).expression)
    }
}
