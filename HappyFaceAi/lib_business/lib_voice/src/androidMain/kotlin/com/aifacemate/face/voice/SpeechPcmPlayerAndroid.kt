package com.aifacemate.face.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import com.aifacemate.face.voice.tts.model.SpeechAudioChunk
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

actual fun createSpeechPcmPlayer(): SpeechPcmPlayer = AndroidSpeechPcmPlayer()

private class AndroidSpeechPcmPlayer : SpeechPcmPlayer {
    private val closed = AtomicBoolean(false)
    private val lock = Any()
    @Volatile private var speaker: AudioTrack? = null
    private var sampleRate = 0
    private var writtenFrames = 0L

    override suspend fun write(audio: SpeechAudioChunk) = withContext(Dispatchers.IO) {
        if (audio.pcm16.isEmpty()) return@withContext
        val line = synchronized(lock) {
            check(!closed.get()) { "朗读已停止" }
            speaker?.also { check(sampleRate == audio.sampleRate) { "朗读采样率发生变化" } } ?: run {
                val minimum = AudioTrack.getMinBufferSize(audio.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0) { "扬声器不支持此采样率" }
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(if (Build.VERSION.SDK_INT >= 26) AudioAttributes.USAGE_ASSISTANT else AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(audio.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum, audio.sampleRate * 2 / 10)).build().also {
                        try { check(it.state == AudioTrack.STATE_INITIALIZED) { "扬声器初始化失败" }; it.play() }
                        catch (error: Exception) { it.release(); throw error }
                        speaker = it; sampleRate = audio.sampleRate
                    }
            }
        }
        var offset = 0
        while (offset < audio.pcm16.size) {
            currentCoroutineContext().ensureActive()
            check(!closed.get()) { "朗读已停止" }
            val count = try {
                line.write(audio.pcm16, offset, minOf(audio.sampleRate * 2 / 50, audio.pcm16.size - offset), AudioTrack.WRITE_BLOCKING)
            } catch (failure: Exception) { currentCoroutineContext().ensureActive(); throw failure }
            currentCoroutineContext().ensureActive()
            check(count > 0 && count % 2 == 0) { "音频写入失败：$count" }
            writtenFrames += count / 2; offset += count
        }
    }

    override suspend fun drain() = withContext(Dispatchers.IO) {
        while (!closed.get()) {
            currentCoroutineContext().ensureActive()
            val played = speaker?.playbackHeadPosition?.toLong()?.and(0xffffffffL) ?: break
            if (played >= writtenFrames) break
            delay(10)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) {
            speaker?.let { line ->
                runCatching { line.pause() }; runCatching { line.flush() }; runCatching { line.release() }
            }
            speaker = null
        }
    }
}
