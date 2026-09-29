package com.aifacemate.face

import com.aifacemate.face.chat.*
import com.aifacemate.face.network.NetworkClient
import com.aifacemate.face.voice.streamSpokenReply
import com.aifacemate.face.voice.tts.client.TtsClient
import com.aifacemate.face.voice.tts.model.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlin.test.*

class StreamingReplyHttpTest {
    @Test fun firstAudioArrivesBeforeTheBrainAndTtsFinishTheirResponses() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val workers = Executors.newFixedThreadPool(2)
        server.executor = workers
        val heard = CountDownLatch(1)
        val brainFinished = AtomicBoolean(false)
        val firstTtsFinished = AtomicBoolean(false)
        val ttsCalls = AtomicInteger()
        val validRequests = AtomicBoolean(true)
        server.createContext("/brain/v1/chat/completions") { exchange ->
            val request = exchange.requestBody.use { it.readBytes().decodeToString() }
            validRequests.set(validRequests.get() && request.contains("\"stream\":true"))
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { output ->
                output.write("data: {\"choices\":[{\"delta\":{\"content\":\"您好。\"}}]}\n\n".toByteArray()); output.flush()
                check(heard.await(5, TimeUnit.SECONDS)) { "No early audio reached playback" }
                brainFinished.set(true)
                output.write("data: {\"choices\":[{\"delta\":{\"content\":\"第二句。\"},\"finish_reason\":\"stop\"}]}\n\n".toByteArray()); output.flush()
            }
        }
        server.createContext("/tts/chat/completions") { exchange ->
            val request = exchange.requestBody.use { it.readBytes().decodeToString() }
            validRequests.set(validRequests.get() && request.contains("\"stream\":true") && request.contains("\"format\":\"pcm16\""))
            val first = ttsCalls.incrementAndGet() == 1
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { output ->
                output.write("data: {\"choices\":[{\"delta\":{\"audio\":{\"data\":\"AQACAA==\"}}}]}\n\n".toByteArray()); output.flush()
                if (first) {
                    check(heard.await(5, TimeUnit.SECONDS)) { "Audio was buffered until response completion" }
                    firstTtsFinished.set(true)
                }
                output.write("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n".toByteArray()); output.flush()
            }
        }
        server.start()
        val endpoint = "http://127.0.0.1:${server.address.port}"
        val chunks = mutableListOf<SpeechAudioChunk>()
        try {
            val result = withTimeout(10_000) {
                streamSpokenReply<SpeechAudioChunk>(
                    generate = { delta ->
                        val result = ChatClient().stream(ChatConfig("$endpoint/brain", "test-only", "test"), listOf(ChatMessage("user", "你好")), delta)
                        check(result is ChatResult.Success)
                    },
                    synthesize = { text, emit -> TtsClient(NetworkClient()).stream(TtsRequest(apiKey = "test-only", baseUrl = "$endpoint/tts", text = text), emit) },
                    play = {
                        if (chunks.isEmpty()) { assertFalse(brainFinished.get()); assertFalse(firstTtsFinished.get()); heard.countDown() }
                        chunks.add(it)
                    },
                )
            }
            assertEquals("您好。第二句。", result.text)
            assertNull(result.speechFailure); assertEquals(2, ttsCalls.get()); assertEquals(2, chunks.size)
            assertTrue(validRequests.get()); assertTrue(chunks.all { it.sampleRate == 24_000 })
        } finally { heard.countDown(); server.stop(0); workers.shutdownNow() }
    }
}
