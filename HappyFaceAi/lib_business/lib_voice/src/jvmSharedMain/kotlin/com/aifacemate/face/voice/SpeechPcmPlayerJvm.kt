package com.aifacemate.face.voice

import com.aifacemate.face.voice.tts.model.SpeechAudioChunk
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

actual fun createSpeechPcmPlayer(): SpeechPcmPlayer = JavaSpeechPcmPlayer()

private class JavaSpeechPcmPlayer : SpeechPcmPlayer {
    private val closed = AtomicBoolean(false)
    private val lock = Any()
    @Volatile private var speaker: SourceDataLine? = null
    private var sampleRate = 0

    override suspend fun write(audio: SpeechAudioChunk) = withContext(Dispatchers.IO) {
        if (audio.pcm16.isEmpty()) return@withContext
        val line = synchronized(lock) {
            check(!closed.get()) { "朗读已停止" }
            speaker?.also { check(sampleRate == audio.sampleRate) { "朗读采样率发生变化" } } ?: run {
                val format = AudioFormat(audio.sampleRate.toFloat(), 16, 1, true, false)
                AudioSystem.getSourceDataLine(format).also {
                    try { it.open(format, audio.sampleRate * 2 / 10); it.start() }
                    catch (error: Exception) { it.close(); throw error }
                    speaker = it; sampleRate = audio.sampleRate
                }
            }
        }
        var offset = 0
        while (offset < audio.pcm16.size) {
            currentCoroutineContext().ensureActive()
            check(!closed.get()) { "朗读已停止" }
            val count = try {
                line.write(audio.pcm16, offset, minOf(audio.sampleRate * 2 / 50, audio.pcm16.size - offset))
            } catch (failure: Exception) { currentCoroutineContext().ensureActive(); throw failure }
            currentCoroutineContext().ensureActive()
            check(count > 0) { "扬声器播放失败" }
            offset += count
        }
    }

    override suspend fun drain() = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (!closed.get()) speaker?.drain()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) {
            speaker?.let { line ->
                runCatching { line.stop() }; runCatching { line.flush() }; runCatching { line.close() }
            }
            speaker = null
        }
    }
}
