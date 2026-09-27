package com.aifacemate.face.voice

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

actual fun createVoiceService(): VoiceService = AndroidVoiceService()

private class AndroidVoiceService : VoiceService {
    @Volatile private var stopped = false
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null

    override suspend fun record(maxDurationMs: Long): VoiceCaptureResult = withContext(Dispatchers.IO) {
        stopped = false
        val min = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return@withContext VoiceCaptureResult.Failure("麦克风不可用")
        val line = AudioRecord(MediaRecorder.AudioSource.MIC, 16_000, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 6_400))
        recorder = line
        try {
            line.startRecording()
            val output = ByteArrayOutputStream()
            val frame = ByteArray(640)
            val endAt = System.currentTimeMillis() + maxDurationMs
            while (!stopped && System.currentTimeMillis() < endAt) {
                val n = line.read(frame, 0, frame.size)
                if (n > 0) output.write(frame, 0, n)
                else if (n < 0) error("录音失败：$n")
            }
            VoiceCaptureResult.Success(VoiceClip(wav16k(output.toByteArray()), "audio/wav"))
        } catch (error: Exception) { VoiceCaptureResult.Failure(error.message ?: "录音失败") }
        finally { runCatching { line.stop() }; line.release(); recorder = null }
    }

    override suspend fun play(clip: VoiceClip): VoicePlaybackResult = withContext(Dispatchers.IO) {
        try {
            require(clip.mimeType == "audio/wav") { "需要 WAV 音频" }
            val (rate, pcm) = decodeWav(clip.bytes)
            val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "扬声器不支持此采样率" }
            val line = AudioTrack(AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 4_800), AudioTrack.MODE_STREAM)
            player = line; stopped = false
            try {
                line.play()
                var offset = 0
                while (offset < pcm.size && !stopped) {
                    val n = line.write(pcm, offset, minOf(4_800, pcm.size - offset), AudioTrack.WRITE_BLOCKING)
                    check(n > 0) { "音频写入失败：$n" }
                    offset += n
                }
                val target = offset / 2L
                kotlinx.coroutines.withTimeoutOrNull(30_000) {
                    while (!stopped && (line.playbackHeadPosition.toLong() and 0xffffffffL) < target) delay(10)
                }
                VoicePlaybackResult.Complete
            } finally { runCatching { line.stop() }; line.release(); player = null }
        } catch (error: Exception) { VoicePlaybackResult.Failure(error.message ?: "播放失败") }
    }

    override fun stop() { stopped = true; runCatching { recorder?.stop() }; player?.pause(); player?.flush() }

    private fun wav16k(pcm: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        fun le(value: Int) { repeat(4) { write(value ushr (it * 8) and 255) } }
        write("RIFF".encodeToByteArray()); le(36 + pcm.size)
        write("WAVEfmt ".encodeToByteArray()); le(16)
        write(byteArrayOf(1, 0, 1, 0)); le(16_000); le(32_000)
        write(byteArrayOf(2, 0, 16, 0)); write("data".encodeToByteArray()); le(pcm.size); write(pcm)
    }.toByteArray()

    private fun readLeInt(bytes: ByteArray, offset: Int) = (bytes[offset].toInt() and 255) or
        ((bytes[offset + 1].toInt() and 255) shl 8) or ((bytes[offset + 2].toInt() and 255) shl 16) or
        ((bytes[offset + 3].toInt() and 255) shl 24)

    private fun readLeShort(bytes: ByteArray, offset: Int) = (bytes[offset].toInt() and 255) or
        ((bytes[offset + 1].toInt() and 255) shl 8)

    private fun decodeWav(bytes: ByteArray): Pair<Int, ByteArray> {
        require(bytes.size >= 44 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            bytes.copyOfRange(8, 12).decodeToString() == "WAVE") { "不是 WAV 音频" }
        var offset = 12
        var rate = 0
        var audio: ByteArray? = null
        while (offset + 8 <= bytes.size) {
            val size = readLeInt(bytes, offset + 4)
            require(size >= 0 && size <= bytes.size - offset - 8) { "WAV 数据损坏" }
            when (bytes.copyOfRange(offset, offset + 4).decodeToString()) {
                "fmt " -> {
                    require(size >= 16 && readLeShort(bytes, offset + 8) == 1 &&
                        readLeShort(bytes, offset + 10) == 1 && readLeShort(bytes, offset + 22) == 16) {
                        "仅支持单声道 PCM16 WAV"
                    }
                    rate = readLeInt(bytes, offset + 12)
                }
                "data" -> audio = bytes.copyOfRange(offset + 8, offset + 8 + size)
            }
            offset += 8 + size + (size and 1)
        }
        val pcm = requireNotNull(audio) { "WAV 缺少音频数据" }
        check(rate > 0 && pcm.isNotEmpty()) { "WAV 缺少音频数据" }
        return rate to pcm
    }
}
