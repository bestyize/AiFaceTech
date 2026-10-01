package com.aifacemate.face.capability

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FileModelStoreTest {
    private lateinit var root: File
    private val first = "first model fixture".toByteArray()
    private val second = "second model fixture, a little longer".toByteArray()

    @BeforeTest fun setUp() { root = Files.createTempDirectory("model-store-test-").toFile() }
    @AfterTest fun tearDown() { root.deleteRecursively() }

    private fun spec(id: String, bytes: ByteArray) = ModelSpec(id, "vision/bundle", "$id.onnx", bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, "https://example.test/$id")

    private fun store(bytes: Map<String, ByteArray>, downloads: MutableList<String> = mutableListOf()) =
        FileModelStore({ root }, download = { url -> downloads += url; ByteArrayInputStream(bytes.getValue(url.substringAfterLast('/'))) })

    @Test
    fun installsVerifiesAndSkipsFilesAlreadyPresent() = runBlocking {
        val models = listOf(spec("a", first), spec("b", second))
        val downloads = mutableListOf<String>()
        val store = store(mapOf("a" to first, "b" to second), downloads)
        assertFalse(models.any(store::installed))
        val progress = mutableListOf<Pair<Long, Long>>()
        store.install(models) { done, total -> progress += done to total }
        assertTrue(models.all(store::installed))
        assertEquals((first.size + second.size).toLong(), progress.last().first)
        assertEquals(progress.last().first, progress.last().second)
        assertContentEquals(first, File(root, "vision/bundle/a.onnx").readBytes())
        store.install(models)
        assertEquals(2, downloads.size)
        assertEquals(File(root, "vision/bundle/b.onnx").absolutePath, store.verifiedPath(models[1]))
    }

    @Test
    fun corruptFilesAreRejectedAndRepairedByReinstalling() = runBlocking {
        val model = spec("a", first)
        val target = File(root, "vision/bundle/a.onnx").apply { parentFile.mkdirs(); writeBytes(ByteArray(first.size)) }
        val store = store(mapOf("a" to first))
        // The size check alone passes; the SHA-256 check does not.
        assertTrue(store.installed(model))
        assertFailsWith<IllegalStateException> { store.verifiedPath(model) }
        store.install(listOf(model))
        assertContentEquals(first, target.readBytes())
        assertEquals(target.absolutePath, store.verifiedPath(model))
    }

    @Test
    fun failedOrCancelledDownloadsLeaveNoPartialFile() = runBlocking {
        val model = spec("a", first)
        val bundle = File(root, "vision/bundle")
        val badChecksum = FileModelStore({ root }, download = { ByteArrayInputStream(ByteArray(first.size)) })
        assertFailsWith<IllegalStateException> { badChecksum.install(listOf(model)) }
        assertTrue(bundle.listFiles()!!.isEmpty())
        assertFailsWith<CancellationException> {
            store(mapOf("a" to first)).install(listOf(model)) { done, _ -> if (done > 0) throw CancellationException("fixture") }
        }
        assertTrue(bundle.listFiles()!!.isEmpty())
        val network = FileModelStore({ root }, download = { throw IOException("network fixture") })
        assertFailsWith<IOException> { network.install(listOf(model)) }
        assertTrue(bundle.listFiles()!!.isEmpty())
    }

    @Test
    fun mirrorsAreTriedInOrderWhenTheMainSourceFails() = runBlocking {
        val model = spec("a", first).copy(mirrors = listOf("https://mirror-1.test/a", "https://mirror-2.test/a"))
        val requested = mutableListOf<String>()
        val store = FileModelStore({ root }, download = { url ->
            requested += url
            when {
                url.startsWith("https://example.test") -> throw IOException("blocked fixture")
                // A mirror serving different bytes is rejected by the checksum, then the next one is tried.
                url.startsWith("https://mirror-1") -> ByteArrayInputStream(ByteArray(first.size))
                else -> ByteArrayInputStream(first)
            }
        })
        store.install(listOf(model))
        assertEquals(listOf(model.url) + model.mirrors, requested)
        assertContentEquals(first, File(root, "vision/bundle/a.onnx").readBytes())
        assertTrue(File(root, "vision/bundle").listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun allSourcesFailingReportsEveryFailureAndCancellationIsNotRetried() = runBlocking {
        val model = spec("a", first).copy(mirrors = listOf("https://mirror.test/a"))
        val failing = FileModelStore({ root }, download = { url -> throw IOException("down: $url") })
        val failure = assertFailsWith<IOException> { failing.install(listOf(model)) }
        assertEquals("down: https://mirror.test/a", failure.message)
        // Coroutine stack-trace recovery may wrap the thrown exception, keeping the original as its cause.
        val original = generateSequence(failure as Throwable) { it.cause }.last()
        assertEquals("down: ${model.url}", original.suppressed.single().message)
        var attempts = 0
        val cancelled = FileModelStore({ root }, download = { attempts++; throw CancellationException("fixture") })
        assertFailsWith<CancellationException> { cancelled.install(listOf(model)) }
        assertEquals(1, attempts)
    }

    @Test
    fun removeDeletesOnlyManagedFiles() = runBlocking {
        val model = spec("a", first)
        val store = store(mapOf("a" to first))
        store.install(listOf(model))
        val custom = File(root, "vision/bundle/custom.onnx").apply { writeBytes(second) }
        store.remove(listOf(model))
        assertFalse(store.installed(model))
        assertFailsWith<IllegalStateException> { store.verifiedPath(model) }
        assertTrue(custom.isFile)
    }
}
