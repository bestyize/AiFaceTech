package com.aifacemate.face.voice.realtime.provider.doubao

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoubaoOutputGateTest {
    @Test fun interruptionDropsRemainderUntilEndOfTurn() {
        val gate = DoubaoOutputGate()
        assertTrue(gate.sentenceStarted())
        gate.interrupt()
        assertFalse(gate.accepts())
        assertFalse(gate.sentenceStarted()) // The old response may contain another sentence.
        assertFalse(gate.ended())
        assertTrue(gate.sentenceStarted())
        assertTrue(gate.accepts())
        assertTrue(gate.ended())
    }
}
