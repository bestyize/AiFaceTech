package com.aifacemate.face.network

import kotlin.test.*

class SseEventDecoderTest {
    @Test fun ignoresHeartbeatsAndJoinsMultilineData() {
        val decoder = SseEventDecoder()
        assertNull(decoder.acceptLine(": keep-alive"))
        assertNull(decoder.acceptLine("event: message"))
        assertNull(decoder.acceptLine("\uFEFFdata: 第一行"))
        assertNull(decoder.acceptLine("data: 第二行"))
        assertEquals("第一行\n第二行", decoder.acceptLine(""))
        assertNull(decoder.acceptLine(""))
        decoder.acceptLine("data: [DONE]")
        assertEquals("[DONE]", decoder.finish())
    }
    @Test fun rejectsOversizedEvents() {
        val decoder = SseEventDecoder(4)
        assertFailsWith<IllegalStateException> { decoder.acceptLine("data: too long") }
    }
}
