package com.aifacemate.face.audio

import com.aifacemate.face.voice.realtime.RealtimeEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class DesktopRealtimeAudioTest {
    @Test
    fun doubaoFloat32ConvertsToSignedLittleEndianPcm16() {
        val bytes = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(-1f).putFloat(0f).putFloat(1f).array()
        val audio = RealtimeEvent.OutputAudio(bytes, "pcm_f32le", 24_000)
        assertContentEquals(byteArrayOf(0, -128, 0, 0, -1, 127), toSpeakerPcm(audio, 24_000))
    }

    @Test
    fun qwenPcm16DoublesSamplesFor48kDevice() {
        val audio = RealtimeEvent.OutputAudio(byteArrayOf(1, 2, 3, 4), "pcm_s16le", 24_000)
        assertContentEquals(byteArrayOf(1, 2, 1, 2, 3, 4, 3, 4), toSpeakerPcm(audio, 48_000))
    }

    @Test
    fun rejectsIncompleteSamples() {
        assertFailsWith<IllegalArgumentException> {
            toSpeakerPcm(RealtimeEvent.OutputAudio(byteArrayOf(1, 2, 3), "pcm_f32le", 24_000), 24_000)
        }
    }
}
