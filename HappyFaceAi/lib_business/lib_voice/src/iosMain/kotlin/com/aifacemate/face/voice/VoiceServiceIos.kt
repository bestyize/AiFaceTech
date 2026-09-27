package com.aifacemate.face.voice

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAudioPlayer
import platform.AVFoundation.AVAudioRecorder
import platform.AVFoundation.AVAudioSession
import platform.AVFoundation.AVAudioSessionCategoryPlayAndRecord
import platform.AVFoundation.AVFormatIDKey
import platform.AVFoundation.AVLinearPCMBitDepthKey
import platform.AVFoundation.AVLinearPCMIsFloatKey
import platform.AVFoundation.AVNumberOfChannelsKey
import platform.AVFoundation.AVSampleRateKey
import platform.AudioToolbox.kAudioFormatLinearPCM
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import kotlin.coroutines.resume

actual fun createVoiceService(): VoiceService = IosVoiceService()

@OptIn(ExperimentalForeignApi::class)
private class IosVoiceService : VoiceService {
    private var recorder: AVAudioRecorder? = null
    private var player: AVAudioPlayer? = null
    private var stopped = false

    override suspend fun record(maxDurationMs: Long): VoiceCaptureResult {
        val allowed = suspendCancellableCoroutine<Boolean> { continuation ->
            AVAudioSession.sharedInstance().requestRecordPermission { granted ->
                if (continuation.isActive) continuation.resume(granted)
            }
        }
        if (!allowed) return VoiceCaptureResult.Failure("请在系统设置中允许麦克风访问")
        val path = "${NSTemporaryDirectory()}voice-${NSUUID().UUIDString}.wav"
        try {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
            AVAudioSession.sharedInstance().setActive(true, error = null)
            val settings = mapOf<Any?, Any>(
                AVFormatIDKey to kAudioFormatLinearPCM,
                AVSampleRateKey to 16_000,
                AVNumberOfChannelsKey to 1,
                AVLinearPCMBitDepthKey to 16,
                AVLinearPCMIsFloatKey to false,
            )
            val recording = AVAudioRecorder(NSURL.fileURLWithPath(path), settings, error = null)
            recorder = recording
            stopped = false
            check(recording.record()) { "麦克风启动失败" }
            delay(maxDurationMs.coerceAtLeast(1))
            recording.stop()
            if (stopped) return VoiceCaptureResult.Failure("录音已停止")
            val data = NSData.dataWithContentsOfFile(path) ?: error("无法读取录音")
            val bytes = ByteArray(data.length.toInt())
            if (bytes.isNotEmpty()) data.getBytes(bytes.refTo(0), data.length)
            return VoiceCaptureResult.Success(VoiceClip(bytes, "audio/wav"))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return VoiceCaptureResult.Failure(error.message ?: "录音失败")
        } finally {
            runCatching { recorder?.stop() }
            recorder = null
            runCatching { NSFileManager.defaultManager.removeItemAtPath(path, error = null) }
        }
    }

    override suspend fun play(clip: VoiceClip): VoicePlaybackResult {
        if (clip.bytes.isEmpty()) return VoicePlaybackResult.Failure("语音数据为空")
        return try {
            val data = clip.bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), clip.bytes.size.toULong()) }
            val audio = AVAudioPlayer(data, error = null)
            player = audio
            stopped = false
            check(audio.prepareToPlay() && audio.play()) { "扬声器播放失败" }
            while (!stopped && audio.playing) delay(40)
            VoicePlaybackResult.Complete
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            VoicePlaybackResult.Failure(error.message ?: "播放失败")
        } finally {
            player?.stop()
            player = null
        }
    }

    override fun stop() {
        stopped = true
        recorder?.stop()
        player?.stop()
    }
}
