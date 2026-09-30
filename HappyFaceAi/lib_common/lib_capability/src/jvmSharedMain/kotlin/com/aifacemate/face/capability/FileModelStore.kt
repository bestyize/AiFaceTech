package com.aifacemate.face.capability

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Stores each model at `root/directory/fileName`, downloading through a `.part` file that is verified before it is kept. */
class FileModelStore(
    private val root: () -> File,
    private val download: (String) -> InputStream = ::openHttpsDownload,
) : ModelStore {
    private val gate = Mutex()
    // Verified files are remembered by length and modification time, so opening a session does not rehash 80 MB.
    private val verified = HashMap<String, Pair<Long, Long>>()

    override val supportsDownloads = true

    fun file(model: ModelSpec) = File(File(root(), model.directory), model.fileName)

    override fun installed(model: ModelSpec): Boolean =
        runCatching { file(model).let { it.isFile && it.length() == model.sizeBytes } }.getOrDefault(false)

    override suspend fun install(models: List<ModelSpec>, onProgress: (Long, Long) -> Unit) = gate.withLock {
        withContext(Dispatchers.IO) {
            val total = models.sumOf { it.sizeBytes }
            var completed = 0L
            onProgress(0, total)
            for (model in models) {
                currentCoroutineContext().ensureActive()
                val target = file(model)
                val parent = target.parentFile ?: error("模型路径无效")
                check(parent.isDirectory || parent.mkdirs()) { "无法创建模型目录" }
                if (!valid(target, model)) {
                    val partial = File(parent, "${target.name}.part")
                    try {
                        download(model.url).use { input ->
                            partial.outputStream().buffered().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var received = 0L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    received += count
                                    check(received <= model.sizeBytes) { "${model.fileName} 文件大小不符" }
                                    output.write(buffer, 0, count)
                                    onProgress(completed + received, total)
                                }
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        check(valid(partial, model)) { "${model.fileName} 校验失败，请重新下载" }
                        check(!target.exists() || target.delete()) { "无法替换 ${model.fileName}" }
                        check(partial.renameTo(target)) { "无法保存 ${model.fileName}" }
                    } finally {
                        if (partial.exists()) partial.delete()
                    }
                }
                completed += model.sizeBytes
                onProgress(completed, total)
            }
        }
    }

    override suspend fun remove(models: List<ModelSpec>) = gate.withLock {
        withContext(Dispatchers.IO) {
            for (model in models) {
                val target = file(model)
                verified.remove(target.absolutePath)
                listOf(target, File(target.parentFile, "${target.name}.part")).forEach { file ->
                    check(!file.exists() || file.delete()) { "无法删除 ${model.fileName}" }
                }
            }
        }
    }

    override suspend fun verifiedPath(model: ModelSpec): String = gate.withLock {
        withContext(Dispatchers.IO) {
            val target = file(model)
            check(valid(target, model)) { "${model.fileName} 未下载或校验失败，请在设置中重新下载" }
            target.absolutePath
        }
    }

    private suspend fun valid(file: File, model: ModelSpec): Boolean {
        if (!file.isFile || file.length() != model.sizeBytes) return false
        val stamp = file.length() to file.lastModified()
        if (verified[file.absolutePath] == stamp) return true
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
        val matches = digest.digest().joinToString("") { "%02x".format(it) } == model.sha256
        // Only remember the final file; a verified `.part` is renamed and gets a fresh check once.
        if (matches && !file.name.endsWith(".part")) verified[file.absolutePath] = stamp
        return matches
    }
}

fun openHttpsDownload(url: String): InputStream {
    require(URL(url).protocol == "https") { "模型下载须使用 HTTPS" }
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        check(connection.responseCode == 200) { "模型下载失败（HTTP ${connection.responseCode}）" }
        val input = connection.inputStream
        return object : FilterInputStream(input) {
            override fun close() { try { super.close() } finally { connection.disconnect() } }
        }
    } catch (failure: Throwable) {
        connection.disconnect()
        throw failure
    }
}
