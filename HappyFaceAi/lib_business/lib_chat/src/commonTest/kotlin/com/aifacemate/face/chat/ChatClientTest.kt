package com.aifacemate.face.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatClientTest {
    @Test fun normalizesEndpoint() {
        assertEquals("https://example.com/v1/chat/completions", chatEndpoint("https://example.com/v1"))
        assertEquals("https://example.com/v1/chat/completions", chatEndpoint("https://example.com"))
    }
}
