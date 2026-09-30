package com.aifacemate.face.voice

import platform.AVFoundation.AVAudioEngine
import platform.AVFoundation.AVAudioSession
import platform.AVFoundation.AVAudioSessionCategoryPlayAndRecord
import platform.AVFoundation.AVSpeechBoundaryImmediate
import platform.AVFoundation.AVSpeechSynthesizer
import platform.AVFoundation.AVSpeechSynthesizerDelegateProtocol
import platform.AVFoundation.AVSpeechUtterance
import platform.AVFoundation.AVSpeechSynthesisVoice
import platform.Foundation.NSLocale
import platform.Foundation.NSObject
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatusAuthorized
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun systemAsrAvailable(): Boolean =
    SFSpeechRecognizer(locale = NSLocale(localeIdentifier = "zh_CN")).isAvailable

actual fun systemTtsAvailable(): Boolean = true

actual fun createNativeSpeechEngine(): NativeSpeechEngine = IosNativeSpeech()

/** The iOS realtime PCM device still needs AVAudioConverter output and is disabled until ready. */
actual fun realtimeAudioAvailable(): Boolean = false
actual fun createRealtimeAudioDevice(): RealtimeAudioDevice = error("iOS 实时音频设备尚未接入")

private class IosNativeSpeech : NativeSpeechEngine {
    private val events = kotlinx.coroutines.flow.MutableSharedFlow<VoiceObservation>(extraBufferCapacity = 8,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    override val observations: kotlinx.coroutines.flow.Flow<VoiceObservation> get() = events
    private var engine: AVAudioEngine? = null
    private var task: SFSpeechRecognitionTask? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var synthesizer: AVSpeechSynthesizer? = null
    private var delegate: SpeechDelegate? = null
    private var finishRequested = false
    private var recognitionGeneration = 0

    override suspend fun recognize(): String {
        finishRequested = false
        val generation = ++recognitionGeneration
        val authorized = suspendCancellableCoroutine<Boolean> { continuation ->
            SFSpeechRecognizer.requestAuthorization { status ->
                if (continuation.isActive) continuation.resume(status == SFSpeechRecognizerAuthorizationStatusAuthorized)
            }
        }
        check(authorized) { "请在系统设置中允许语音识别" }
        val microphoneAllowed = suspendCancellableCoroutine<Boolean> { continuation ->
            AVAudioSession.sharedInstance().requestRecordPermission { allowed ->
                if (continuation.isActive) continuation.resume(allowed)
            }
        }
        check(microphoneAllowed) { "请在系统设置中允许麦克风访问" }
        val recognizer = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = "zh_CN"))
        check(recognizer.isAvailable) { "系统语音识别不可用" }
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
        AVAudioSession.sharedInstance().setActive(true, error = null)
        return suspendCancellableCoroutine { continuation ->
            val audioEngine = AVAudioEngine()
            val audioRequest = SFSpeechAudioBufferRecognitionRequest()
            audioRequest.shouldReportPartialResults = true
            engine = audioEngine; request = audioRequest
            val input = audioEngine.inputNode
            input.installTapOnBus(0u, bufferSize = 1024u, format = input.outputFormatForBus(0u)) { buffer, _ ->
                if (buffer != null) audioRequest.appendAudioPCMBuffer(buffer)
            }
            task = recognizer.recognitionTaskWithRequest(audioRequest) { result, error ->
                if (generation == recognitionGeneration) {
                    if (result != null && !result.isFinal && continuation.isActive)
                        events.tryEmit(VoiceObservation.PartialText(result.bestTranscription.formattedString))
                    if (error != null && continuation.isActive) continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                    else if (result?.isFinal == true && continuation.isActive) continuation.resume(result.bestTranscription.formattedString)
                    if (error != null || result?.isFinal == true) stopRecognition()
                }
            }
            audioEngine.prepare()
            if (!audioEngine.startAndReturnError(null) && continuation.isActive) {
                continuation.resumeWithException(IllegalStateException("麦克风启动失败"))
                stopRecognition()
            }
            if (finishRequested) finishRecognition()
            continuation.invokeOnCancellation { stopRecognition() }
        }
    }

    override fun finishRecognition() {
        finishRequested = true
        if (request != null) {
            endInput()
            task?.finish()
        }
    }

    override suspend fun speak(text: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val speech = AVSpeechSynthesizer()
            val listener = SpeechDelegate(
                complete = { if (continuation.isActive) continuation.resume(Unit) },
                cancelled = { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("系统朗读中断")) },
            )
            synthesizer = speech; delegate = listener; speech.delegate = listener
            val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
            utterance.voice = AVSpeechSynthesisVoice.speechVoiceWithLanguage("zh-CN")
            speech.speakUtterance(utterance)
            continuation.invokeOnCancellation { speech.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate) }
        }
        synthesizer = null; delegate = null
    }

    private fun endInput() {
        val activeEngine = engine
        val activeRequest = request
        engine = null; request = null
        activeEngine?.stop()
        activeEngine?.inputNode?.removeTapOnBus(0u)
        activeRequest?.endAudio()
    }

    private fun stopRecognition() {
        recognitionGeneration++
        val activeTask = task
        task = null
        endInput()
        activeTask?.cancel()
    }

    override fun stop() {
        stopRecognition()
        synthesizer?.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate)
        synthesizer = null; delegate = null
    }
}

private class SpeechDelegate(val complete: () -> Unit, val cancelled: () -> Unit) : NSObject(), AVSpeechSynthesizerDelegateProtocol {
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) = complete()
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didCancelSpeechUtterance: AVSpeechUtterance) = cancelled()
}
