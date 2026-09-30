package com.aifacemate.face.voice

import kotlin.test.*

class VoiceObservationTest {
    @Test fun pcmSilenceAndSignedSamplesProduceBoundedActivity() {
        assertEquals(0f, pcm16Activity(byteArrayOf()))
        assertEquals(0f, pcm16Activity(ByteArray(640)))
        assertEquals(1f, pcm16Activity(byteArrayOf(-1, 127)))
        assertEquals(1f, pcm16Activity(byteArrayOf(0, -128)))
        assertEquals(0f, pcm16Activity(byteArrayOf(1, 0)))
        assertTrue(pcm16Activity(byteArrayOf(0, 16)) > pcm16Activity(byteArrayOf(0, 4)))
    }

    @Test fun unusedBufferTailDoesNotAffectMeasuredAudio() {
        assertEquals(0f, pcm16Activity(byteArrayOf(0, 0, -1, 127), 2))
        assertEquals(0f, pcm16Activity(byteArrayOf(-1), 1))
        assertFailsWith<IllegalArgumentException> { pcm16Activity(ByteArray(2), 3) }
    }
}
