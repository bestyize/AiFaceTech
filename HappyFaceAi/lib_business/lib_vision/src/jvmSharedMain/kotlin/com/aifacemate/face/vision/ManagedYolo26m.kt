package com.aifacemate.face.vision

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class YoloModelArtifact(
    val url: String = Yolo26mModelInfo.downloadUrl,
    val size: Long = Yolo26mModelInfo.sizeBytes,
    val sha256: String = Yolo26mModelInfo.sha256,
)

/** One gate serializes downloads, inference and deletion, so native sessions never outlive their file. */
internal class ManagedYolo26m(
    private val directory: () -> File,
    private val artifact: YoloModelArtifact = YoloModelArtifact(),
    private val downloadStream: (String) -> InputStream = ::openModelDownload,
    private val createDetector: (String) -> VisionDetector = ::Yolo26mOnnxDetector,
) {
    private val gate = Mutex()
    private var detector: VisionDetector? = null
    private var detectorPath: String? = null
    private fun target() = File(directory(), "yolo26m.onnx")

    fun installed(): Boolean = runCatching { target().let { it.isFile && it.length() == artifact.size } }.getOrDefault(false)

    suspend fun install(onProgress: (Long, Long) -> Unit) = gate.withLock {
        withContext(Dispatchers.IO) {
            val target = target()
            check(target.parentFile.let { it.isDirectory || it.mkdirs() }) { "无法创建 YOLO 模型目录" }
            if (valid(target)) {
                onProgress(artifact.size, artifact.size)
                return@withContext
            }
            val partial = File(target.parentFile, "${target.name}.part")
            try {
                onProgress(0, artifact.size)
                downloadStream(artifact.url).use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var received = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            check(received <= artifact.size) { "YOLO 模型文件大小不符" }
                            output.write(buffer, 0, count)
                            onProgress(received, artifact.size)
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                check(valid(partial)) { "YOLO 模型校验失败，请重新下载" }
                if (detectorPath == target.absolutePath) closeDetector()
                check(!target.exists() || target.delete()) { "无法替换 YOLO 模型" }
                check(partial.renameTo(target)) { "无法保存 YOLO 模型" }
            } finally {
                if (partial.exists()) partial.delete()
            }
        }
    }

    suspend fun remove() = gate.withLock {
        withContext(Dispatchers.IO) {
            // Only our own cached file is removed; custom user model files are never deleted.
            if (detectorPath == target().absolutePath) closeDetector()
            listOf(target(), File(directory(), "yolo26m.onnx.part")).forEach { file ->
                check(!file.exists() || file.delete()) { "无法删除 YOLO 模型" }
            }
        }
    }

    suspend fun detect(frame: RgbaFrame, preferences: VisionPreferences): List<VisionDetection> = gate.withLock {
        withContext(Dispatchers.Default) {
            if (!preferences.yoloEnabled) {
                closeDetector()
                return@withContext emptyList()
            }
            val config = preferences.normalized()
            require(config.modelPathError(false) == null) { "YOLO 模型路径须为 .onnx 文件" }
            val managed = config.yoloModelPath.isEmpty()
            val model = if (managed) target() else File(config.yoloModelPath)
            val path = model.absolutePath
            if (detectorPath != path) closeDetector()
            val engine = detector ?: run {
                check(model.isFile && model.canRead()) {
                    if (managed) "请先在视觉设置中下载 YOLO26m 模型" else "YOLO 模型文件不可读取"
                }
                if (managed) check(valid(model)) { "YOLO 模型校验失败，请删除后重新下载" }
                currentCoroutineContext().ensureActive()
                createDetector(path).also { detector = it; detectorPath = path }
            }
            engine.detect(frame, config.yoloConfidenceThreshold)
        }
    }

    suspend fun unload() = withContext(NonCancellable + Dispatchers.Default) { gate.withLock { closeDetector() } }

    private fun closeDetector() {
        val engine = detector
        detector = null
        detectorPath = null
        engine?.close()
    }

    private suspend fun valid(file: File): Boolean {
        if (!file.isFile || file.length() != artifact.size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == artifact.sha256
    }
}

private fun openModelDownload(url: String): InputStream {
    require(URL(url).protocol == "https") { "模型下载须使用 HTTPS" }
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        check(connection.responseCode == 200) { "YOLO 下载失败（HTTP ${connection.responseCode}）" }
        val input = connection.inputStream
        return object : FilterInputStream(input) {
            override fun close() { try { super.close() } finally { connection.disconnect() } }
        }
    } catch (failure: Throwable) {
        connection.disconnect()
        throw failure
    }
}
