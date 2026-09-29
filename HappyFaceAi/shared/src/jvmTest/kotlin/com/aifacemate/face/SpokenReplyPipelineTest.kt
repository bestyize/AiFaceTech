package com.aifacemate.face

import com.aifacemate.face.voice.*
import kotlinx.coroutines.*
import kotlin.test.*

class SpokenReplyPipelineTest {
    @Test fun playsBeforeBothTextAndAudioFinishInOrder() = runBlocking {
        withTimeout(5000) {
            val heard = CompletableDeferred<Unit>()
            val played = mutableListOf<String>()
            var textFinished = false
            var firstSynthesisFinished = false
            var starts = 0
            var drains = 0
            val result = streamSpokenReply<String>(
                generate = { delta -> delta("第一句。"); heard.await(); delta("第二句。"); textFinished = true },
                synthesize = { sentence, emit ->
                    if (sentence == "第一句。") {
                        emit("first-a"); heard.await(); emit("first-b"); firstSynthesisFinished = true
                    } else emit("second")
                },
                play = {
                    if (played.isEmpty()) { assertFalse(textFinished); assertFalse(firstSynthesisFinished); heard.complete(Unit) }
                    played.add(it)
                },
                onSpeechStart = { starts++ }, drain = { drains++ },
            )
            assertEquals(listOf("first-a", "first-b", "second"), played)
            assertEquals("第一句。第二句。", result.text)
            assertNull(result.speechFailure); assertEquals(1, starts); assertEquals(1, drains)
        }
    }

    @Test fun audioFailureStillReceivesCompleteText() = runBlocking {
        val seen = mutableListOf<String>()
        var errors = 0
        val result = streamSpokenReply<String>(
            generate = { delta -> delta("第一句。"); delta("后面的文字仍然保留。") },
            synthesize = { _, _ -> error("TTS unavailable") },
            play = { error("must not play") },
            onText = { seen.add(it) }, onSpeechFailure = { errors++ },
        )
        assertEquals("第一句。后面的文字仍然保留。", result.text)
        assertEquals(result.text, seen.last()); assertEquals(1, errors)
        assertEquals("TTS unavailable", result.speechFailure?.message)
    }

    @Test fun cancellationStopsAllThreeStagesWithoutDrainingQueuedSpeech() = runBlocking {
        withTimeout(5000) {
            val heard = CompletableDeferred<Unit>()
            var generationStopped = false
            var synthesisStopped = false
            var drained = false
            val reply = launch {
                streamSpokenReply<String>(
                    generate = { delta -> try { delta("第一句。"); awaitCancellation() } finally { generationStopped = true } },
                    synthesize = { _, emit -> try { emit("audio"); awaitCancellation() } finally { synthesisStopped = true } },
                    play = { heard.complete(Unit); awaitCancellation() }, drain = { drained = true },
                )
            }
            heard.await(); reply.cancelAndJoin()
            assertTrue(generationStopped); assertTrue(synthesisStopped); assertFalse(drained)
        }
    }

    @Test fun sentenceBoundariesHandleDecimalsAndLongUnpunctuatedText() {
        val buffer = SpeechSentenceBuffer()
        assertEquals(emptyList(), buffer.append("价格是 3."))
        assertEquals(listOf("价格是 3.14 元。"), buffer.append("14 元。下一句"))
        assertEquals(listOf("下一句"), buffer.finish())
        val long = "这是很长没有标点的一段中文".repeat(20)
        val chunks = SpeechSentenceBuffer().let { it.append(long) + it.finish() }
        assertEquals(long, chunks.joinToString("")); assertTrue(chunks.first().length <= 32)
        assertTrue(chunks.all { it.length <= 80 })
    }
}
