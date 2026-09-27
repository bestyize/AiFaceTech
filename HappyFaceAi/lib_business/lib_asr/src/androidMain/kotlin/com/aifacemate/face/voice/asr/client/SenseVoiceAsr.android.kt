package com.aifacemate.face.voice.asr.client

import com.aifacemate.face.data.AndroidAppContext
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The pinned export is kept in noBackupFilesDir, never in APK assets. */
actual object SenseVoiceAsr {
    private const val REVISION = "2365baeacb507f821a0c8120fcee3d484dba7a07"
    private const val BASE = "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/$REVISION"
    private const val MODEL = "model.int8.onnx"
    private const val TOKENS = "tokens.txt"
    private const val MODEL_SIZE = 239_233_841L
    private const val TOKENS_SIZE = 315_894L
    private const val MODEL_SHA = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51"
    private const val TOKENS_SHA = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc"
    private val gate = Mutex()
    private var verified = false
    private var recognizer: OfflineRecognizer? = null

    private fun directory(): File = File(
        checkNotNull(AndroidAppContext.context()) { "Android Context 未初始化" }.noBackupFilesDir,
        "asr/sensevoice-small-int8-2024-07-17",
    )

    actual fun supported() = AndroidAppContext.context() != null

    actual fun installed(): Boolean = runCatching {
        File(directory(), MODEL).length() == MODEL_SIZE && File(directory(), TOKENS).length() == TOKENS_SIZE
    }.getOrDefault(false)

    actual suspend fun install(onProgress: (Long, Long) -> Unit) = gate.withLock {
        withContext(Dispatchers.IO) {
            val dir = directory().apply { check(exists() || mkdirs()) { "无法创建模型目录" } }
            val total = MODEL_SIZE + TOKENS_SIZE
            recognizer?.release(); recognizer = null
            verified = false
            download(File(dir, MODEL), MODEL_SIZE, MODEL_SHA, onProgress, 0L, total)
            download(File(dir, TOKENS), TOKENS_SIZE, TOKENS_SHA, onProgress, MODEL_SIZE, total)
            verified = true
        }
    }

    actual suspend fun remove() = gate.withLock {
        withContext(Dispatchers.IO) {
            verified = false
            recognizer?.release(); recognizer = null
            val dir = directory()
            listOf(MODEL, TOKENS).forEach { name ->
                val file = File(dir, name)
                check(!file.exists() || file.delete()) { "无法删除 $name" }
            }
        }
    }

    actual suspend fun transcribe(wav: ByteArray): String = gate.withLock {
        withContext(Dispatchers.Default) {
            check(installed()) { "请先在声音设置中下载 SenseVoice 模型" }
            val dir = directory()
            if (!verified) {
                check(valid(File(dir, MODEL), MODEL_SIZE, MODEL_SHA) && valid(File(dir, TOKENS), TOKENS_SIZE, TOKENS_SHA)) {
                    "SenseVoice 模型校验失败，请删除后重新下载"
                }
                verified = true
            }
            val samples = pcm16WavToFloat(wav)
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(model = File(dir, MODEL).absolutePath, language = "auto"),
                    tokens = File(dir, TOKENS).absolutePath, numThreads = 2, provider = "cpu",
                ),
            )
            val engine = recognizer ?: try {
                OfflineRecognizer(config = config).also { recognizer = it }
            } catch (failure: LinkageError) {
                throw IllegalStateException("当前设备缺少 SenseVoice 原生运行库", failure)
            }
            val stream = engine.createStream()
            try {
                stream.acceptWaveform(samples, 16_000)
                engine.decode(stream)
                engine.getResult(stream).text.trim()
            } finally { stream.release() }
        }
    }

    private suspend fun download(
        target: File, size: Long, sha: String, progress: (Long, Long) -> Unit, offset: Long, total: Long,
    ) {
        if (valid(target, size, sha)) { progress(offset + size, total); return }
        val temp = File(target.parentFile, "${target.name}.part")
        val connection = URL("$BASE/${target.name}?download=true").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            check(connection.responseCode == 200) { "模型下载失败（HTTP ${connection.responseCode}）" }
            var received = 0L
            connection.inputStream.use { input ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        check(received <= size) { "模型文件大小不符" }
                        output.write(buffer, 0, count)
                        progress(offset + received, total)
                    }
                }
            }
            check(valid(temp, size, sha)) { "模型 SHA-256 校验失败" }
            if (target.exists()) check(target.delete()) { "无法替换旧模型" }
            check(temp.renameTo(target)) { "无法保存模型" }
        } finally {
            connection.disconnect()
            if (temp.exists()) temp.delete()
        }
    }

    private fun valid(file: File, size: Long, sha: String): Boolean {
        if (!file.isFile || file.length() != size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == sha
    }

    private fun pcm16WavToFloat(wav: ByteArray): FloatArray {
        fun u16(at: Int) = (wav[at].toInt() and 255) or ((wav[at + 1].toInt() and 255) shl 8)
        fun u32(at: Int) = u16(at).toLong() or (u16(at + 2).toLong() shl 16)
        check(wav.size >= 44 && String(wav, 0, 4) == "RIFF" && String(wav, 8, 4) == "WAVE") { "需要 WAV 录音" }
        var offset = 12
        var formatOk = false
        var data = -1
        var length = 0
        while (offset + 8 <= wav.size) {
            val size = u32(offset + 4)
            check(size <= wav.size.toLong() - offset - 8) { "WAV 数据不完整" }
            when (String(wav, offset, 4)) {
                "fmt " -> {
                    check(size >= 16) { "WAV 格式信息不完整" }
                    formatOk = u16(offset + 8) == 1 && u16(offset + 10) == 1 &&
                        u32(offset + 12) == 16_000L && u16(offset + 22) == 16
                }
                "data" -> { data = offset + 8; length = size.toInt() }
            }
            offset += 8 + size.toInt() + (size.toInt() and 1)
        }
        check(formatOk && data >= 0 && length >= 2 && length % 2 == 0) { "仅支持 16 kHz 单声道 PCM16 WAV" }
        return FloatArray(length / 2) { index -> u16(data + index * 2).toShort() / 32768f }
    }
}
