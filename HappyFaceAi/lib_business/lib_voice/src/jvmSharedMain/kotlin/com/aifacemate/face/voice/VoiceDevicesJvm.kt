package com.aifacemate.face.voice

import com.aifacemate.face.voice.realtime.RealtimeEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

actual fun nativeSpeechAvailable(): Boolean = false
actual fun createNativeSpeechEngine(): NativeSpeechEngine = error("Desktop system speech service is unavailable")

actual fun realtimeAudioAvailable(): Boolean = runCatching {
    AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, pcmFormat(16_000))) &&
        listOf(24_000, 48_000).any { AudioSystem.isLineSupported(DataLine.Info(SourceDataLine::class.java, pcmFormat(it))) }
}.getOrDefault(false)

actual fun createRealtimeAudioDevice(): RealtimeAudioDevice = JavaSoundRealtimeDevice()

private fun pcmFormat(rate: Int) = AudioFormat(rate.toFloat(), 16, 1, true, false)

private class JavaSoundRealtimeDevice : RealtimeAudioDevice {
    private val closed = AtomicBoolean(false)
    private val microphone: TargetDataLine = AudioSystem.getTargetDataLine(pcmFormat(16_000)).apply {
        try { open(pcmFormat(16_000), 640 * 10); start() } catch (error: Exception) { close(); throw error }
    }
    private val outputRate = listOf(24_000, 48_000).firstOrNull {
        AudioSystem.isLineSupported(DataLine.Info(SourceDataLine::class.java, pcmFormat(it)))
    } ?: run { microphone.close(); error("PCM16 speaker unavailable") }
    private val speaker: SourceDataLine = try {
        AudioSystem.getSourceDataLine(pcmFormat(outputRate)).apply {
            try { open(pcmFormat(outputRate), outputRate * 2 / 10); start() } catch (error: Exception) { close(); throw error }
        }
    } catch (error: Exception) { microphone.close(); throw error }
    private val lock = Any()

    override suspend fun capture(onFrame: suspend (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(640)
        while (currentCoroutineContext().isActive && !closed.get()) {
            var offset = 0
            while (offset < buffer.size) {
                val n = try { microphone.read(buffer, offset, buffer.size - offset) }
                catch (error: Exception) { if (closed.get()) return@withContext else throw error }
                if (closed.get()) return@withContext
                check(n > 0) { "Microphone stopped" }
                offset += n
            }
            onFrame(buffer.copyOf())
        }
    }

    override suspend fun play(audio: RealtimeEvent.OutputAudio) = withContext(Dispatchers.IO) {
        val pcm = playbackPcm16(audio)
        val output = if (outputRate == 24_000) pcm else ByteArray(pcm.size * 2).also { doubled ->
            for (i in pcm.indices step 2) {
                val j = i * 2
                doubled[j] = pcm[i]; doubled[j + 1] = pcm[i + 1]
                doubled[j + 2] = pcm[i]; doubled[j + 3] = pcm[i + 1]
            }
        }
        val maxBytes = outputRate * 2 / 50
        var offset = 0
        while (offset < output.size && !closed.get()) {
            val n = synchronized(lock) { speaker.write(output, offset, minOf(maxBytes, output.size - offset)) }
            if (n <= 0) break
            offset += n
        }
    }

    override suspend fun drain() = withContext(Dispatchers.IO) { if (!closed.get()) speaker.drain() }
    override fun interrupt() { synchronized(lock) { if (!closed.get()) speaker.flush() } }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        microphone.stop(); microphone.close()
        synchronized(lock) { speaker.stop(); speaker.flush(); speaker.close() }
    }
}
