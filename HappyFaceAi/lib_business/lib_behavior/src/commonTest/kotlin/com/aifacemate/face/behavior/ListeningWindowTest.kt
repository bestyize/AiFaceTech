package com.aifacemate.face.behavior

import com.aifacemate.face.behavior.proactive.*
import kotlin.test.*

class ListeningWindowTest {
    @Test fun silenceWithoutSpeechDoesNotSubmitEmptyTurn() {
        val window = ListeningWindow(0, true)
        window.activity(.01f, 1000)
        assertEquals(ListeningDecision.Continue, window.decision(11999))
        assertEquals(ListeningDecision.NoResponse, window.decision(12000))
    }
    @Test fun segmentedSpeechIsSubmittedOnlyAfterSilence() {
        val window = ListeningWindow(0, true)
        window.activity(.2f, 1000)
        assertEquals(ListeningDecision.Continue, window.decision(2399))
        window.text("今天有好消息", 2200)
        assertEquals(ListeningDecision.Continue, window.decision(3599))
        assertEquals(ListeningDecision.Submit, window.decision(3600))
        window.activity(.5f, 29900)
        assertEquals(ListeningDecision.Submit, window.decision(30000))
    }
    @Test fun realtimeServiceKeepsItsOwnTurnDetectionAndHasABound() {
        val window = ListeningWindow(0, false)
        window.activity(.5f, 1000)
        assertEquals(ListeningDecision.Continue, window.decision(5000))
        assertEquals(ListeningDecision.NoResponse, window.decision(30000))
    }
}
