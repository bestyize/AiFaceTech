package com.aifacemate.face.vision

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A versioned private cache for an OpenCV Zoo ONNX file. */
internal class ManagedVisualModelFile(
    private val directory: () -> File,
    private val spec: LocalVisionModelAsset,
) {
    private val gate = Mutex()
    private fun target() = File(directory(), spec.filename)

    fun installed() = runCatching { target().let { it.isFile && it.length() == spec.sizeBytes } }.getOrDefault(false)

    suspend fun install(onProgress: (Long, Long) -> Unit) = gate.withLock {
        withContext(Dispatchers.IO) {
            val target = target()
            val parent = target.parentFile ?: error("模型缓存路径无效")
            check(parent.isDirectory || parent.mkdirs()) { "无法创建视觉模型缓存目录" }
            if (valid(target)) { onProgress(spec.sizeBytes, spec.sizeBytes); return@withContext }
            val partial = File(parent, "${target.name}.part")
            try {
                onProgress(0, spec.sizeBytes)
                openModelDownload(spec.url).use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var received = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            check(received <= spec.sizeBytes) { "${spec.filename} 文件大小不符" }
                            output.write(buffer, 0, count)
                            onProgress(received, spec.sizeBytes)
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                check(valid(partial)) { "${spec.filename} SHA-256 校验失败，请重试" }
                check(!target.exists() || target.delete()) { "无法替换 ${spec.filename}" }
                check(partial.renameTo(target)) { "无法保存 ${spec.filename}" }
            } finally {
                if (partial.exists()) partial.delete()
            }
        }
    }

    suspend fun remove() = gate.withLock {
        withContext(Dispatchers.IO) {
            listOf(target(), File(directory(), "${spec.filename}.part")).forEach { file ->
                check(!file.exists() || file.delete()) { "无法删除 ${spec.filename}" }
            }
        }
    }

    suspend fun verifiedPath(): String = withContext(Dispatchers.IO) {
        check(valid(target())) { "视觉模型 ${spec.filename} 未下载或校验失败，请返回视觉设置下载模型" }
        target().absolutePath
    }

    private suspend fun valid(file: File): Boolean {
        if (!file.isFile || file.length() != spec.sizeBytes) return false
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
        return digest.digest().joinToString("") { "%02x".format(it) } == spec.sha256
    }
}
