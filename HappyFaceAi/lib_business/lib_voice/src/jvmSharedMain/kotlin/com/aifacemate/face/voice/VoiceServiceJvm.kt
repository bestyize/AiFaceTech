package com.aifacemate.face.voice

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

actual fun createVoiceService(): VoiceService = DesktopVoiceService()

private class DesktopVoiceService : VoiceService {
    @Volatile private var recording: TargetDataLine? = null
    @Volatile private var playback: SourceDataLine? = null
    @Volatile private var stopped = false
    @Volatile private var finishRequested = false

    override suspend fun record(maxDurationMs: Long): VoiceCaptureResult {
        stopped = false
        finishRequested = false
        return withContext(Dispatchers.IO) {
            val format = AudioFormat(16_000f, 16, 1, true, false)
            val line = runCatching { AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java, format)) as TargetDataLine }
                .getOrElse { return@withContext VoiceCaptureResult.Failure("麦克风不可用") }
            recording = line
            val output = ByteArrayOutputStream()
            try {
                line.open(format)
                if (!stopped && !finishRequested) line.start()
                val frame = ByteArray(640)
                val endAt = System.currentTimeMillis() + maxDurationMs
                while (!stopped && !finishRequested && System.currentTimeMillis() < endAt) {
                    val ready = minOf(frame.size, line.available()) and -2
                    if (ready == 0) {
                        delay(10)
                    } else {
                        val count = line.read(frame, 0, ready)
                        if (count > 0) output.write(frame, 0, count)
                        else delay(10)
                    }
                }
                when {
                    stopped -> VoiceCaptureResult.Failure("录音已取消")
                    output.size() == 0 -> VoiceCaptureResult.Failure("录音时间过短")
                    else -> VoiceCaptureResult.Success(VoiceClip(wav(output.toByteArray())))
                }
            } catch (error: Exception) {
                VoiceCaptureResult.Failure("录音失败：${error.message}")
            } finally {
                runCatching { line.stop() }
                line.close()
                recording = null
            }
        }
    }

    override fun finishRecording() {
        finishRequested = true
    }

    override suspend fun play(clip: VoiceClip): VoicePlaybackResult = withContext(Dispatchers.IO) {
        try {
            AudioSystem.getAudioInputStream(ByteArrayInputStream(clip.bytes)).use { stream ->
                val line = AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, stream.format)) as SourceDataLine
                playback = line
                stopped = false
                try {
                    line.open(stream.format)
                    line.start()
                    val frame = ByteArray(4096)
                    while (!stopped) {
                        val count = stream.read(frame)
                        if (count < 0) break
                        line.write(frame, 0, count)
                    }
                    if (!stopped) line.drain()
                    VoicePlaybackResult.Complete
                } finally {
                    runCatching { line.stop() }
                    line.close()
                    playback = null
                }
            }
        } catch (error: Exception) {
            VoicePlaybackResult.Failure("播放失败：${error.message}")
        }
    }

    override fun stop() {
        stopped = true
        finishRequested = true
        runCatching { recording?.stop() }
        runCatching { playback?.stop() }
    }

    private fun wav(pcm: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        fun le(value: Int) { repeat(4) { write(value ushr (it * 8) and 255) } }
        write("RIFF".encodeToByteArray()); le(36 + pcm.size)
        write("WAVEfmt ".encodeToByteArray()); le(16)
        write(byteArrayOf(1, 0, 1, 0)); le(16_000); le(32_000)
        write(byteArrayOf(2, 0, 16, 0)); write("data".encodeToByteArray()); le(pcm.size); write(pcm)
    }.toByteArray()
}
