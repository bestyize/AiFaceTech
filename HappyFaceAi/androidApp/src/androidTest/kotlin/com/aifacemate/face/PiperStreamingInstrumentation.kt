package com.aifacemate.face

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.aifacemate.face.data.AndroidAppContext
import com.aifacemate.face.voice.createSpeechPcmPlayer
import com.aifacemate.face.voice.tts.client.PiperAudioCallback
import com.aifacemate.face.voice.tts.client.PiperTts
import kotlinx.coroutines.*

/** On-device JNI/PCM smoke test using the app's installed model. No microphone, keys or downloads. */
class PiperStreamingInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        try {
            AndroidAppContext.install(targetContext)
            val callback = PiperAudioCallback { 1 }
            val method = callback.javaClass.getMethod("invoke", FloatArray::class.java)
            check(method.returnType == java.lang.Integer::class.java) { "JNI callback must return boxed Integer" }
            check(PiperTts.installed()) { "Piper model must already be installed in the target app" }
            runBlocking {
                withTimeout(60_000) {
                    val player = createSpeechPcmPlayer()
                    val started = System.nanoTime()
                    var firstMs = -1L
                    var chunks = 0
                    try {
                        PiperTts.stream("你好，这是修复后的本地语音测试。现在可以按句合成和播放了。") {
                            if (firstMs < 0) firstMs = (System.nanoTime() - started) / 1_000_000
                            check(it.pcm16.isNotEmpty())
                            player.write(it); chunks++
                        }
                        player.drain()
                    } finally { player.close() }
                    check(chunks > 1) { "Expected multiple streamed PCM blocks" }
                    result.putString("playback", "PASS chunks=$chunks firstPCM=${firstMs}ms")
                    repeat(2) {
                        val first = CompletableDeferred<Unit>()
                        val synthesis = launch(Dispatchers.Default) {
                            PiperTts.stream("这是一段用于验证停止回复的本地测试。".repeat(8)) {
                                first.complete(Unit); awaitCancellation()
                            }
                        }
                        first.await()
                        withTimeout(5000) { synthesis.cancelAndJoin() }
                    }
                    var restarted = 0
                    PiperTts.stream("停止之后，也能再次合成。") { restarted++ }
                    check(restarted > 0)
                    result.putString("cancellation", "PASS cancel twice and synthesize again")
                }
            }
            result.putString("result", "PASS Android JNI callback, real speaker playback, cancel and restart")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("result", "FAIL ${failure::class.simpleName}: ${failure.message}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
