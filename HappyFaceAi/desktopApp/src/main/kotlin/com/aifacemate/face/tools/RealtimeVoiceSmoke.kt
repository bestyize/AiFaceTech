package com.aifacemate.face.tools

import com.aifacemate.face.voice.realtime.RealtimeAudio
import com.aifacemate.face.voice.realtime.RealtimeConfig
import com.aifacemate.face.voice.realtime.RealtimeConversationEngine
import com.aifacemate.face.voice.realtime.RealtimeEvent
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Manual protocol check: send an existing 16 kHz mono PCM16 file in 20 ms frames. */
fun main(args: Array<String>) = runBlocking {
    require(args.size == 3) { "Usage: provider input.pcm output.pcm" }
    val input = File(args[1])
    require(input.isFile && input.length() > 0 && input.length() % 2L == 0L) { "Expected raw mono PCM16 input" }
    val config = when (args[0].lowercase()) {
        "doubao" -> RealtimeConfig.Doubao(
            appId = credential("DOUBAO_APP_ID"),
            accessKey = credential("DOUBAO_ACCESS_KEY"),
            appKey = credential("DOUBAO_APP_KEY"),
            sessionId = UUID.randomUUID().toString(),
        )
        "qwen" -> RealtimeConfig.Qwen(
            apiKey = credential("DASHSCOPE_API_KEY"),
            workspaceEndpoint = credential("QWEN_WORKSPACE_ENDPOINT"),
        )
        else -> error("Provider must be doubao or qwen")
    }

    FileOutputStream(args[2]).use { output ->
        val session = RealtimeConversationEngine().connect(config)
        val done = CompletableDeferred<Unit>()
        val receiver = launch {
            session.events.collect { event ->
                when (event) {
                    is RealtimeEvent.OutputAudio -> {
                        output.write(event.bytes)
                        println("Received ${event.bytes.size} bytes (${event.format}, ${event.sampleRate} Hz)")
                    }
                    is RealtimeEvent.InputText -> if (event.final) println("You: ${event.text}")
                    is RealtimeEvent.OutputText -> if (event.final) println("Assistant: ${event.text}")
                    is RealtimeEvent.Failure -> done.completeExceptionally(IllegalStateException(event.message))
                    RealtimeEvent.OutputComplete -> done.complete(Unit)
                    RealtimeEvent.Closed -> if (!done.isCompleted) done.completeExceptionally(IllegalStateException("Connection closed before a response"))
                    else -> Unit
                }
            }
        }
        val sender = launch {
            FileInputStream(input).use { source ->
                while (isActive && !done.isCompleted) {
                    val chunk = source.readNBytes(640)
                    if (chunk.isEmpty()) break
                    require(chunk.size % 2 == 0) { "Truncated PCM16 input" }
                    session.sendAudio(RealtimeAudio(chunk))
                    delay(20)
                }
            }
            // Both providers use server VAD. Keep sending silence until it ends the turn.
            val silence = RealtimeAudio(ByteArray(640))
            while (isActive && !done.isCompleted) {
                session.sendAudio(silence)
                delay(20)
            }
        }
        try {
            withTimeout(90_000) { done.await() }
            output.flush()
            println("Saved raw response audio to ${args[2]}")
        } finally {
            sender.cancelAndJoin()
            session.close()
            receiver.cancelAndJoin()
        }
    }
}

private fun credential(name: String): String = System.getenv(name)?.takeIf { it.isNotBlank() }
    ?: error("Missing environment variable $name")
