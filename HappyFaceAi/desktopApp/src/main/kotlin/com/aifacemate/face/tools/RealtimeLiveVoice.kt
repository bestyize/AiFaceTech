package com.aifacemate.face.tools

import com.aifacemate.face.audio.DesktopMicrophone
import com.aifacemate.face.audio.DesktopSpeaker
import com.aifacemate.face.voice.realtime.RealtimeConversationEngine
import com.aifacemate.face.voice.realtime.RealtimeEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Manual desktop full-duplex microphone and speaker check. Press Enter or wait for the timer. */
fun main(args: Array<String>) = runBlocking {
    require(args.size in 1..2) { "Usage: provider [seconds]" }
    val seconds = args.getOrNull(1)?.toLong() ?: 120L
    require(seconds in 1..3600) { "Duration must be 1..3600 seconds" }
    val config = realtimeProviderConfig(args[0])

    DesktopMicrophone().use { microphone ->
        DesktopSpeaker().use { speaker ->
            val session = RealtimeConversationEngine().connect(config)
            val finished = CompletableDeferred<Unit>()
            val playback = speaker.start(this) { println("Playback finished; listening") }
            val receiver = launch {
                session.events.collect { event ->
                    when (event) {
                        is RealtimeEvent.OutputAudio -> speaker.play(event)
                        RealtimeEvent.OutputComplete -> speaker.complete()
                        RealtimeEvent.InterruptPlayback -> speaker.interrupt()
                        is RealtimeEvent.InputText -> if (event.final) println("You: ${event.text}")
                        is RealtimeEvent.OutputText -> if (event.final) println("Assistant: ${event.text}")
                        is RealtimeEvent.TranscriptionFailed -> System.err.println("Transcription: ${event.message}")
                        is RealtimeEvent.Failure -> finished.completeExceptionally(IllegalStateException(event.message))
                        RealtimeEvent.Closed -> finished.completeExceptionally(IllegalStateException("Realtime connection closed"))
                        else -> Unit
                    }
                }
            }
            val capture = launch { microphone.capture(session::sendAudio) }
            // Gradle's JavaExec forwards stdin; a missing stdin simply leaves the timer active.
            Thread({ if (readLine() != null) finished.complete(Unit) }, "realtime-stop").apply {
                isDaemon = true
                start()
            }
            try {
                println("Connected. Speak into the microphone; press Enter to stop (maximum ${seconds}s).")
                withTimeoutOrNull(seconds * 1_000) { finished.await() }
            } finally {
                // Device shutdown must not prevent the network session from closing.
                runCatching { microphone.close() } // Unblock TargetDataLine.read.
                runCatching { speaker.close() } // Discard queued audio.
                capture.cancel()
                receiver.cancel()
                playback.cancel()
                withContext(NonCancellable) {
                    withTimeoutOrNull(2_000) { capture.cancelAndJoin() }
                    withTimeoutOrNull(2_000) { playback.cancelAndJoin() }
                    session.close()
                    receiver.cancelAndJoin()
                }
            }
        }
    }
}
