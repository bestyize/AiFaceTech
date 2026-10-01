package com.aifacemate.face

import android.os.Bundle
import com.aifacemate.face.voice.SpeechCapture
import com.aifacemate.face.voice.asr.client.SenseVoiceAsr
import com.aifacemate.face.voice.asr.vad.createSpeechDetector
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.runBlocking

/**
 * On-device noise gate check with the bundled Silero model, without the microphone. With [speechPath], a 16 kHz
 * WAV readable by the app (for example under /data/local/tmp) is also padded with noise, gated and recognized.
 */
internal fun runSpeechGateCheck(speechPath: String?): Bundle {
    val result = Bundle()
    try {
        val random = Random(3)
        fun white(rms: Float) = (random.nextFloat() * 2 - 1) * rms * 1.732f
        var brown = 0f
        val noises = mapOf(
            "white" to FloatArray(80_000) { white(.03f) },
            "fan" to FloatArray(80_000) { i -> brown = (brown + (random.nextFloat() * 2 - 1) * .02f) * .995f; (brown + .3f * sin(2 * PI * 100 * i / 16_000).toFloat()) * .06f },
            "keyboard" to FloatArray(80_000) { i -> val t = i % 1600; if (t < 160) white(.25f) * exp(-t / 60f) else white(.002f) },
        )
        noises.forEach { (name, samples) ->
            val (kept, millis) = gate(pcm(samples))
            check(kept == null) { "$name noise was kept as speech" }
            result.putString("noise.$name", "PASS rejected 5 s in $millis ms")
        }
        speechPath?.let { path ->
            val speech = wavPcm(File(path).readBytes())
            val padded = pcm(FloatArray(32_000) { white(.02f) }) + speech + pcm(FloatArray(32_000) { white(.02f) })
            val (kept, millis) = gate(padded)
            checkNotNull(kept) { "speech was rejected" }
            val text = runBlocking { SenseVoiceAsr.transcribe(wav(kept)) }
            result.putString("speech", "PASS kept ${kept.size / 32} of ${padded.size / 32} ms in $millis ms → $text")
        }
        result.putString("result", "PASS bundled Silero VAD on device")
    } catch (failure: Throwable) {
        result.putString("result", "FAIL ${failure::class.simpleName}: ${failure.message}")
    }
    return result
}

private fun gate(pcm: ByteArray): Pair<ByteArray?, Long> = SpeechCapture(checkNotNull(createSpeechDetector()) {
        "the bundled Silero model did not load"
    }).use { capture ->
        val started = System.nanoTime()
        pcm.toList().chunked(640).forEach { capture.accept(it.toByteArray(), it.size) }
        capture.finish(pcm) to (System.nanoTime() - started) / 1_000_000
    }

private fun pcm(samples: FloatArray) = ByteArray(samples.size * 2).also { out ->
        samples.forEachIndexed { i, v -> val s = (v.coerceIn(-1f, 1f) * 32767).toInt(); out[2 * i] = s.toByte(); out[2 * i + 1] = (s shr 8).toByte() }
    }

private fun wavPcm(bytes: ByteArray): ByteArray {
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val size = (bytes[offset + 4].toInt() and 255) or ((bytes[offset + 5].toInt() and 255) shl 8) or
                ((bytes[offset + 6].toInt() and 255) shl 16) or ((bytes[offset + 7].toInt() and 255) shl 24)
            if (String(bytes, offset, 4) == "data") return bytes.copyOfRange(offset + 8, offset + 8 + size)
            offset += 8 + size + (size and 1)
        }
        error("WAV has no data chunk")
    }

private fun wav(pcm: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        fun le(value: Int) { repeat(4) { write(value ushr (it * 8) and 255) } }
        write("RIFF".encodeToByteArray()); le(36 + pcm.size)
        write("WAVEfmt ".encodeToByteArray()); le(16)
        write(byteArrayOf(1, 0, 1, 0)); le(16_000); le(32_000)
        write(byteArrayOf(2, 0, 16, 0)); write("data".encodeToByteArray()); le(pcm.size); write(pcm)
    }.toByteArray()
