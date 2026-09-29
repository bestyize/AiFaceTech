package com.aifacemate.face.chat

import kotlin.test.*

class ChatStreamTest {
    @Test fun decodesEscapesAndExcludesReasoningAndTools() {
        assertEquals("", decodeChatStreamEvent("""{"choices":[{"delta":{"reasoning_content":"secret reasoning","tool_calls":[{"arguments":"not speech"}]}}]}""").text)
        assertEquals("你好\n\"小星\"", decodeChatStreamEvent("""{"choices":[{"delta":{"content":"\u4f60\u597d\n\"小星\""},"finish_reason":null}]}""").text)
        assertTrue(decodeChatStreamEvent("[DONE]").finished)
    }
    @Test fun acceptsOrdinaryJsonGatewaysAndRejectsErrors() {
        assertEquals(ChatStreamChunk("完整回答", true), decodeChatStreamEvent("""{"choices":[{"message":{"content":"完整回答"}}]}"""))
        assertFailsWith<IllegalStateException> { decodeChatStreamEvent("""{"error":{"message":"service error"}}""") }
        assertFailsWith<IllegalStateException> { decodeChatStreamEvent("""{"choices":[{"delta":{},"finish_reason":"length"}]}""") }
    }
}
