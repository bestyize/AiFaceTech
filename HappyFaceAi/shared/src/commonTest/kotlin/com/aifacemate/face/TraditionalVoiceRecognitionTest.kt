package com.aifacemate.face

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TraditionalVoiceRecognitionTest {
    @Test fun localRecognitionWorksWithoutOnlineCredentials(): Unit = runBlocking {
        val text = recognizeTraditionalSpeech(TraditionalAsr.SenseVoice, false,
            local = { "你好，小星" }, online = { error("Must not call online service") })
        assertEquals("你好，小星", text)
    }

    @Test fun localFailureWithoutCredentialsKeepsItsOriginalCause(): Unit = runBlocking {
        val cause = IllegalStateException("模型缺失")
        val failure = assertFailsWith<IllegalStateException> {
            recognizeTraditionalSpeech(TraditionalAsr.SenseVoice, false,
                local = { throw cause }, online = { error("Must not call online service") })
        }
        assertSame(cause, failure.cause)
    }

    @Test fun onlineFallbackRequiresCredentialsAndRunsOnce(): Unit = runBlocking {
        var onlineCalls = 0
        var fallbacks = 0
        val text = recognizeTraditionalSpeech(TraditionalAsr.SenseVoice, true,
            local = { "" }, online = { onlineCalls++; "在线识别结果" }, onFallback = { fallbacks++ })
        assertEquals("在线识别结果", text)
        assertEquals(1, onlineCalls)
        assertEquals(1, fallbacks)
    }

    @Test fun punctuationFromNoiseIsNoSpeechAndUsesTheOnlineFallbackOnlyWithAKey(): Unit = runBlocking {
        assertEquals("", recognizeTraditionalSpeech(TraditionalAsr.SenseVoice, false,
            local = { "。" }, online = { error("Must not call online service") }))
        var fallbacks = 0
        assertEquals("在线识别结果", recognizeTraditionalSpeech(TraditionalAsr.SenseVoice, true,
            local = { "." }, online = { "在线识别结果" }, onFallback = { fallbacks++ }))
        assertEquals(1, fallbacks)
    }

    @Test fun onlineRecognitionRequiresItsOwnCredentials(): Unit = runBlocking {
        assertFailsWith<IllegalStateException> {
            recognizeTraditionalSpeech(TraditionalAsr.Mimo, false,
                local = { error("Wrong recognizer") }, online = { error("Missing credentials") })
        }
    }

    @Test fun cancellingLocalRecognitionDoesNotStartOnlineFallback(): Unit = runBlocking {
        val cancelled = CancellationException("User stopped recording")
        val thrown = assertFailsWith<CancellationException> {
            recognizeTraditionalSpeech(TraditionalAsr.SenseVoice, true,
                local = { throw cancelled }, online = { error("Must not fall back after cancellation") })
        }
        assertSame(cancelled, thrown)
    }
}
