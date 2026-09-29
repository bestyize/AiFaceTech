package com.aifacemate.face.voice

import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.aifacemate.face.data.AndroidAppContext
import com.aifacemate.face.voice.realtime.RealtimeEvent
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun systemAsrAvailable(): Boolean {
    val context = AndroidAppContext.context() ?: return false
    return SpeechRecognizer.isRecognitionAvailable(context)
}

actual fun systemTtsAvailable(): Boolean {
    val context = AndroidAppContext.context() ?: return false
    return context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).isNotEmpty()
}

actual fun createNativeSpeechEngine(): NativeSpeechEngine = AndroidNativeSpeech()

private class AndroidNativeSpeech : NativeSpeechEngine {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    @Volatile private var finishRequested = false
    private var finishSent = false
    private var recognitionReady = false

    override suspend fun recognize(): String {
        finishRequested = false
        finishSent = false
        recognitionReady = false
        return withContext(Dispatchers.Main) {
        val context = AndroidAppContext.context() ?: error("Android context unavailable")
        check(SpeechRecognizer.isRecognitionAvailable(context)) { "系统语音识别不可用" }
        suspendCancellableCoroutine { continuation ->
            val service = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = service
            fun release() { main.post { service.destroy(); if (recognizer === service) recognizer = null } }
            service.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (recognizer === service) {
                        recognitionReady = true
                        finishIfRequested(service)
                    }
                }
                override fun onBeginningOfSpeech() {
                    if (recognizer === service) {
                        recognitionReady = true
                        finishIfRequested(service)
                    }
                }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onError(error: Int) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("系统识别失败：$error"))
                    release()
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (continuation.isActive) continuation.resume(text)
                    release()
                }
            })
            continuation.invokeOnCancellation { main.post { service.cancel(); release() } }
            service.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            })
        }
        }
    }

    private fun finishIfRequested(service: SpeechRecognizer) {
        if (recognitionReady && finishRequested && !finishSent && recognizer === service) {
            finishSent = runCatching { service.stopListening() }.isSuccess
        }
    }

    override fun finishRecognition() {
        finishRequested = true
        main.post { recognizer?.let(::finishIfRequested) }
    }

    override suspend fun speak(text: String) = withContext(Dispatchers.Main) {
        val context = AndroidAppContext.context() ?: error("Android context unavailable")
        suspendCancellableCoroutine<Unit> { continuation ->
            val id = UUID.randomUUID().toString()
            val engine = TextToSpeech(context) { status ->
                // Some engines invoke onInit before the constructor returns. Post until tts is assigned.
                main.post {
                    val speech = tts ?: return@post
                    if (status != TextToSpeech.SUCCESS) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("系统朗读不可用"))
                        speech.shutdown(); tts = null
                    } else if (speech.setLanguage(Locale.getDefault()) < TextToSpeech.LANG_AVAILABLE) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("系统未安装当前语言的语音"))
                        speech.shutdown(); tts = null
                    } else {
                        speech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit
                            override fun onDone(utteranceId: String?) {
                                if (continuation.isActive) continuation.resume(Unit)
                                main.post { speech.shutdown(); if (tts === speech) tts = null }
                            }
                            override fun onError(utteranceId: String?) {
                                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("系统朗读失败"))
                                main.post { speech.shutdown(); if (tts === speech) tts = null }
                            }
                        })
                        if (speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
                            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("系统朗读启动失败"))
                            speech.shutdown(); tts = null
                        }
                    }
                }
            }
            tts = engine
            continuation.invokeOnCancellation { main.post { engine.stop(); engine.shutdown(); if (tts === engine) tts = null } }
        }
    }

    override fun stop() { main.post { recognizer?.cancel(); recognizer?.destroy(); recognizer = null; tts?.stop(); tts?.shutdown(); tts = null } }
}

actual fun realtimeAudioAvailable(): Boolean = AndroidAppContext.context() != null &&
    AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0 &&
    AudioTrack.getMinBufferSize(24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0

actual fun createRealtimeAudioDevice(): RealtimeAudioDevice = AndroidRealtimeDevice()

private class AndroidRealtimeDevice : RealtimeAudioDevice {
    private val closed = AtomicBoolean(false)
    private val inputMin = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private val outputMin = AudioTrack.getMinBufferSize(24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private val microphone = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16_000,
        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(inputMin * 2, 6_400))
    private val speaker = AudioTrack(AudioManager.STREAM_MUSIC, 24_000,
        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(outputMin, 4_800), AudioTrack.MODE_STREAM)
    private val echoCanceler = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(microphone.audioSessionId) else null
    private var framesWritten = 0L
    private var generation = 0L

    init {
        try {
            check(microphone.state == AudioRecord.STATE_INITIALIZED && speaker.state == AudioTrack.STATE_INITIALIZED) { "实时音频设备不可用" }
            echoCanceler?.enabled = true
            microphone.startRecording(); speaker.play()
        } catch (error: Exception) {
            echoCanceler?.release()
            runCatching { microphone.release() }
            runCatching { speaker.release() }
            throw error
        }
    }

    override suspend fun capture(onFrame: suspend (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        val frame = ByteArray(640)
        while (currentCoroutineContext().isActive && !closed.get()) {
            var count = 0
            while (count < frame.size) {
                val n = microphone.read(frame, count, frame.size - count)
                if (closed.get()) return@withContext
                check(n > 0) { "麦克风读取失败：$n" }
                count += n
            }
            onFrame(frame.copyOf())
        }
    }

    override suspend fun play(audio: RealtimeEvent.OutputAudio) = withContext(Dispatchers.IO) {
        val pcm = playbackPcm16(audio)
        var pos = 0
        while (pos < pcm.size && !closed.get()) {
            val n = speaker.write(pcm, pos, minOf(960, pcm.size - pos), AudioTrack.WRITE_BLOCKING)
            check(n > 0) { "扬声器写入失败：$n" }
            pos += n
            framesWritten += n / 2
        }
    }

    override suspend fun drain() {
        val token = generation
        val target = framesWritten
        kotlinx.coroutines.withTimeoutOrNull(15_000) {
            while (!closed.get() && token == generation &&
                (speaker.playbackHeadPosition.toLong() and 0xffffffffL) < target) delay(10)
        }
    }

    override fun interrupt() { generation++; speaker.pause(); speaker.flush(); speaker.play(); framesWritten = speaker.playbackHeadPosition.toLong() and 0xffffffffL }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        echoCanceler?.release()
        runCatching { microphone.stop() }; microphone.release()
        runCatching { speaker.pause(); speaker.flush() }; speaker.release()
    }
}
