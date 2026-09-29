package com.aifacemate.face.vision

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shared by face and hand bundles; every file is installed atomically after a pinned-hash check. */
internal class LocalVisionModelCache(private val directory: () -> File) {
    private val gate = Mutex()

    fun installed(task: LocalVisionTask): Boolean = runCatching {
        LocalVisionModelCatalog.assets(task).all { asset ->
            File(directory(), asset.filename).let { it.isFile && it.length() == asset.sizeBytes }
        }
    }.getOrDefault(false)

    suspend fun download(task: LocalVisionTask, onProgress: (Long, Long) -> Unit) = gate.withLock {
        withContext(Dispatchers.IO) {
            val assets = LocalVisionModelCatalog.assets(task)
            val total = assets.sumOf { it.sizeBytes }
            var completed = 0L
            for (asset in assets) {
                currentCoroutineContext().ensureActive()
                val target = File(directory(), asset.filename)
                val parent = target.parentFile ?: error("视觉模型缓存路径无效")
                check(parent.isDirectory || parent.mkdirs()) { "无法创建视觉模型缓存目录" }
                if (valid(target, asset)) {
                    completed += asset.sizeBytes
                    onProgress(completed, total)
                    continue
                }
                val partial = File(parent, "${target.name}.part")
                try {
                    openModelDownload(asset.url).use { input ->
                        partial.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var received = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                received += count
                                check(received <= asset.sizeBytes) { "${asset.filename} 文件大小不符" }
                                output.write(buffer, 0, count)
                                onProgress(completed + received, total)
                            }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    check(valid(partial, asset)) { "${asset.filename} SHA-256 校验失败，请重新下载" }
                    check(!target.exists() || target.delete()) { "无法替换 ${asset.filename}" }
                    check(partial.renameTo(target)) { "无法保存 ${asset.filename}" }
                } finally {
                    if (partial.exists()) partial.delete()
                }
                completed += asset.sizeBytes
                onProgress(completed, total)
            }
        }
    }

    suspend fun remove(task: LocalVisionTask) = gate.withLock {
        withContext(Dispatchers.IO) {
            LocalVisionModelCatalog.assets(task).forEach { asset ->
                listOf(File(directory(), asset.filename), File(directory(), "${asset.filename}.part")).forEach { file ->
                    check(!file.exists() || file.delete()) { "无法删除视觉模型 ${asset.filename}" }
                }
            }
        }
    }

    suspend fun verifiedPath(asset: LocalVisionModelAsset): String = withContext(Dispatchers.IO) {
        val file = File(directory(), asset.filename)
        check(valid(file, asset)) { "请先下载视觉模型 ${asset.filename}" }
        file.absolutePath
    }

    private suspend fun valid(file: File, spec: LocalVisionModelAsset): Boolean {
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
