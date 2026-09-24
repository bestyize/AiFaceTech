package com.aifacemate.face.voice.realtime.provider.doubao

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DoubaoProtocolTest {
    @Test fun startConnectionMatchesOfficialFrame() {
        assertContentEquals(byteArrayOf(17, 20, 16, 0, 0, 0, 0, 1, 0, 0, 0, 2, 123, 125),
            DoubaoProtocol.encode(1, "{}".encodeToByteArray()))
    }

    @Test fun audioFrameCarriesSessionAndUnmodifiedPcm() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val frame = DoubaoProtocol.encode(200, pcm, "12345678-1234-1234-1234-123456789abc", audio = true)
        assertEquals(0x24, frame[1].toInt())
        assertContentEquals(pcm, frame.copyOfRange(frame.size - pcm.size, frame.size))
    }

    @Test fun rejectsTruncatedResponse() {
        assertFailsWith<IllegalArgumentException> { DoubaoProtocol.decode(byteArrayOf(17, 0)) }
    }

    @Test fun decodesSessionAudioResponse() {
        val id = "12345678-1234-1234-1234-123456789abc".encodeToByteArray()
        val payload = byteArrayOf(10, 20)
        val serverFrame = byteArrayOf(17, 0xB4.toByte(), 0, 0, 0, 0, 1, 96, 0, 0, 0, 36) +
            id + byteArrayOf(0, 0, 0, 2) + payload
        val response = DoubaoProtocol.decode(serverFrame)
        assertEquals(352, response.event)
        assertContentEquals(payload, response.payload)
    }
}
