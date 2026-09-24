package com.aifacemate.face.audio

import com.aifacemate.face.voice.realtime.RealtimeAudio
import com.aifacemate.face.voice.realtime.RealtimeEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** JVM desktop capture: 16 kHz, mono, signed little-endian PCM16 in 20 ms frames. */
class DesktopMicrophone : AutoCloseable {
    private val closed = AtomicBoolean()
    private val format = AudioFormat(16_000f, 16, 1, true, false)
    private val line: TargetDataLine = AudioSystem.getTargetDataLine(format).apply {
        try {
            open(format, 640 * 10)
            start()
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    suspend fun capture(send: suspend (RealtimeAudio) -> Unit) = withContext(Dispatchers.IO) {
        val frame = ByteArray(640)
        while (currentCoroutineContext().isActive && !closed.get()) {
            var offset = 0
            while (offset < frame.size) {
                val read = try {
                    line.read(frame, offset, frame.size - offset)
                } catch (error: Exception) {
                    if (closed.get()) return@withContext else throw error
                }
                if (closed.get()) return@withContext
                check(read > 0) { "Microphone stopped" }
                offset += read
            }
            send(RealtimeAudio(frame.copyOf()))
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try { line.stop() } finally { line.close() }
        }
    }
}

/** A bounded speaker queue keeps network receive and device playback independent. */
class DesktopSpeaker : AutoCloseable {
    private val closed = AtomicBoolean()
    private val outputRate = listOf(24_000, 48_000).firstOrNull { rate ->
        AudioSystem.isLineSupported(DataLine.Info(SourceDataLine::class.java, format(rate)))
    } ?: error("No 24 kHz or 48 kHz mono PCM16 speaker available")
    private val line: SourceDataLine = AudioSystem.getSourceDataLine(format(outputRate)).apply {
        try {
            open(format(outputRate), outputRate * 2 / 10) // 100 ms device buffer
            start()
        } catch (error: Exception) {
            close()
            throw error
        }
    }
    private val generation = AtomicLong()
    // Network audio can arrive faster than real-time playback. Keep only the newest 12 packets.
    private val queue = Channel<Command>(12, BufferOverflow.DROP_OLDEST)
    private val lineLock = Any()

    fun start(scope: CoroutineScope, onDrained: () -> Unit): Job = scope.launch(Dispatchers.IO) {
        try {
            for (command in queue) {
                if (command.generation != generation.get()) continue
                when (command) {
                    is Command.Audio -> {
                        val bytes = toSpeakerPcm(command.audio, outputRate)
                        var offset = 0
                        val frameBytes = outputRate * 2 / 50 // 20 ms
                        while (offset < bytes.size && command.generation == generation.get()) {
                            val written = synchronized(lineLock) {
                                if (command.generation != generation.get()) 0
                                else line.write(bytes, offset, minOf(frameBytes, bytes.size - offset))
                            }
                            if (written <= 0) break
                            offset += written
                        }
                    }
                    is Command.Complete -> {
                        line.drain()
                        if (command.generation == generation.get()) onDrained()
                    }
                }
            }
        } catch (error: Exception) {
            if (!closed.get()) throw error
        }
    }

    suspend fun play(audio: RealtimeEvent.OutputAudio) {
        queue.send(Command.Audio(generation.get(), audio))
    }

    suspend fun complete() {
        queue.send(Command.Complete(generation.get()))
    }

    fun interrupt() {
        generation.incrementAndGet()
        while (queue.tryReceive().isSuccess) Unit
        synchronized(lineLock) { line.flush() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        generation.incrementAndGet()
        queue.close()
        synchronized(lineLock) {
            try {
                line.stop()
                line.flush()
            } finally { line.close() }
        }
    }

    private sealed interface Command {
        val generation: Long
        data class Audio(override val generation: Long, val audio: RealtimeEvent.OutputAudio) : Command
        data class Complete(override val generation: Long) : Command
    }
}

private fun format(rate: Int) = AudioFormat(rate.toFloat(), 16, 1, true, false)

/** Doubao uses float32; Qwen uses PCM16. Java Sound receives PCM16 at 24 or 48 kHz. */
internal fun toSpeakerPcm(audio: RealtimeEvent.OutputAudio, outputRate: Int): ByteArray {
    require(audio.sampleRate == 24_000 && outputRate in listOf(24_000, 48_000)) { "Unsupported audio sample rate" }
    val pcm = when (audio.format) {
        "pcm_s16le" -> {
            require(audio.bytes.size % 2 == 0) { "Incomplete PCM16 sample" }
            audio.bytes
        }
        "pcm_f32le" -> {
            require(audio.bytes.size % 4 == 0) { "Incomplete float32 sample" }
            val result = ByteArray(audio.bytes.size / 2)
            val source = ByteBuffer.wrap(audio.bytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in result.indices step 2) {
                val sample = source.float.coerceIn(-1f, 1f)
                val signed = (sample * if (sample < 0f) 32768f else 32767f).toInt()
                result[i] = signed.toByte()
                result[i + 1] = (signed shr 8).toByte()
            }
            result
        }
        else -> error("Unsupported audio format: ${audio.format}")
    }
    if (outputRate == 24_000) return pcm
    val doubled = ByteArray(pcm.size * 2)
    for (i in pcm.indices step 2) {
        val target = i * 2
        doubled[target] = pcm[i]
        doubled[target + 1] = pcm[i + 1]
        doubled[target + 2] = pcm[i]
        doubled[target + 3] = pcm[i + 1]
    }
    return doubled
}
