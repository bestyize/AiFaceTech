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

    @Test fun detectorEvidenceReplacesLoudness() {
        val window = ListeningWindow(0, true)
        window.voice(false, 100)
        // A loud fan is ignored once the recorder reports detector state.
        (200L..6000L step 100).forEach { window.activity(.6f, it) }
        assertEquals(ListeningDecision.Continue, window.decision(11_999))
        assertEquals(ListeningDecision.NoResponse, window.decision(12_000))
        val speaking = ListeningWindow(0, true)
        (1000L..2000L step 100).forEach { speaking.voice(true, it) }
        speaking.voice(false, 2100)
        assertEquals(ListeningDecision.Continue, speaking.decision(3399))
        assertEquals(ListeningDecision.Submit, speaking.decision(3400))
    }

    @Test fun steadyNoiseAndShortKnocksAreNotSpeechWithoutADetector() {
        val fan = ListeningWindow(0, true)
        (0L..11_900L step 100).forEach { fan.activity(.3f, it) }
        assertEquals(ListeningDecision.NoResponse, fan.decision(12_000))
        val knocks = ListeningWindow(0, true)
        (0L..11_900L step 100).forEach { knocks.activity(if (it % 1000 == 0L) .9f else .05f, it) }
        assertEquals(ListeningDecision.NoResponse, knocks.decision(12_000))
    }

    @Test fun speechAboveTheRoomNoiseCountsAfterAMoment() {
        val window = ListeningWindow(0, true)
        (0L..2000L step 100).forEach { window.activity(.12f, it) }
        (2100L..2900L step 100).forEach { window.activity(.6f, it) }
        (3000L..6000L step 100).forEach { window.activity(.12f, it) }
        // Speech from 2.1 s to 2.9 s; submitted 1.4 s after the last voiced sample.
        assertEquals(ListeningDecision.Continue, window.decision(4299))
        assertEquals(ListeningDecision.Submit, window.decision(4300))
    }
}
