package com.aifacemate.face.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class SpokenReplyResult(val text: String, val speechFailure: Throwable? = null)

/** Three independent stages, bounded queues, one ordered player. Speech failure preserves the text stream. */
suspend fun <Audio> streamSpokenReply(
    generate: suspend (onDelta: suspend (String) -> Unit) -> Unit,
    synthesize: suspend (String, emit: suspend (Audio) -> Unit) -> Unit,
    play: suspend (Audio) -> Unit,
    drain: suspend () -> Unit = {},
    onText: suspend (String) -> Unit = {},
    onSpeechStart: suspend () -> Unit = {},
    onSpeechFailure: suspend (Throwable) -> Unit = {},
): SpokenReplyResult = coroutineScope {
    val sentences = Channel<String>(2)
    val audio = Channel<Audio>(8)
    val failure = CompletableDeferred<Throwable>()
    val text = StringBuilder()
    suspend fun failed(error: Throwable) {
        if (failure.complete(error)) onSpeechFailure(error)
    }
    val synthesis = launch {
        try {
            for (sentence in sentences) {
                if (failure.isCompleted) continue
                try {
                    synthesize(sentence) { chunk ->
                        if (failure.isCompleted) throw failure.await()
                        audio.send(chunk)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { failed(error) }
            }
        } finally { audio.close() }
    }
    val playback = launch {
        var started = false
        for (chunk in audio) {
            if (failure.isCompleted) continue
            try {
                if (!started) { onSpeechStart(); started = true }
                play(chunk)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failed(error) }
        }
        if (started && !failure.isCompleted) {
            try { drain() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failed(error) }
        }
    }
    try {
        val buffer = SpeechSentenceBuffer()
        try {
            generate { delta ->
                if (delta.isNotEmpty()) {
                    text.append(delta)
                    onText(text.toString())
                    buffer.append(delta).forEach { sentences.send(it) }
                }
            }
            buffer.finish().forEach { sentences.send(it) }
        } finally { sentences.close() }
        synthesis.join(); playback.join()
        SpokenReplyResult(text.toString(), if (failure.isCompleted) failure.await() else null)
    } finally { sentences.cancel(); audio.cancel() }
}
