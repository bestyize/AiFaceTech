package com.aifacemate.face.vision

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ManagedYolo26mTest {
    private lateinit var directory: File
    private val bytes = "fixture for model integrity".toByteArray()
    private val frame = RgbaFrame(1, 1, byteArrayOf(0, 0, 0, -1))
    private val enabled = VisionPreferences(yoloEnabled = true)

    @BeforeTest fun setUp() { directory = Files.createTempDirectory("yolo-model-test-").toFile() }
    @AfterTest fun tearDown() { directory.deleteRecursively() }
    private fun artifact() = YoloModelArtifact("https://example.test/model.onnx", bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })

    @Test
    fun downloadDoesNotLoadAndInferenceReusesUntilUnload() = runBlocking {
        var downloads = 0
        var opened = 0
        var closed = 0
        val thresholds = mutableListOf<Float>()
        val manager = ManagedYolo26m({ directory }, artifact(),
            downloadStream = { downloads++; ByteArrayInputStream(bytes) },
            createDetector = {
                opened++
                object : VisionDetector {
                    override fun detect(frame: RgbaFrame, confidenceThreshold: Float): List<VisionDetection> {
                        thresholds += confidenceThreshold
                        return emptyList()
                    }
                    override fun close() { closed++ }
                }
            })
        assertFalse(manager.installed())
        assertEquals(emptyList(), manager.detect(frame, VisionPreferences()))
        assertFailsWith<IllegalStateException> { manager.detect(frame, enabled) }
        assertEquals(0, opened)
        manager.install { _, _ -> }
        assertTrue(manager.installed())
        assertEquals(0, opened)
        manager.install { _, _ -> }
        assertEquals(1, downloads)
        manager.detect(frame, enabled.copy(yoloConfidenceThreshold = 0.4f))
        manager.detect(frame, enabled.copy(yoloConfidenceThreshold = 0.8f))
        assertEquals(listOf(0.4f, 0.8f), thresholds)
        assertEquals(1, opened)
        // Session-finally cleanup must also release native resources from a cancelled caller.
        launch { cancel(); manager.unload() }.join()
        assertEquals(1, closed)
        assertTrue(manager.installed())
        manager.detect(frame, enabled)
        assertEquals(2, opened)
        manager.remove()
        assertEquals(2, closed)
        assertFalse(manager.installed())
    }

    @Test
    fun corruptCacheIsRejectedBeforeNativeLoadingAndCanBeRepaired() = runBlocking {
        val target = File(directory, "yolo26m.onnx").apply { writeBytes(ByteArray(bytes.size)) }
        val manager = ManagedYolo26m({ directory }, artifact(), { ByteArrayInputStream(bytes) },
            createDetector = { error("Corrupt model must never reach native runtime") })
        assertFailsWith<IllegalStateException> { manager.detect(frame, enabled) }
        manager.install { _, _ -> }
        assertContentEquals(bytes, target.readBytes())
        assertFalse(File(directory, "yolo26m.onnx.part").exists())
    }

    @Test
    fun downloadFailureAndCancellationLeaveNoPartialModel() = runBlocking {
        val badChecksum = ManagedYolo26m({ directory }, artifact(), { ByteArrayInputStream(ByteArray(bytes.size)) })
        assertFailsWith<IllegalStateException> { badChecksum.install { _, _ -> } }
        assertFalse(badChecksum.installed())
        assertTrue(directory.listFiles()!!.isEmpty())
        val cancelled = ManagedYolo26m({ directory }, artifact(), { ByteArrayInputStream(bytes) })
        assertFailsWith<CancellationException> {
            cancelled.install { received, _ -> if (received > 0) throw CancellationException("cancel fixture") }
        }
        assertFalse(cancelled.installed())
        assertTrue(directory.listFiles()!!.isEmpty())
        val networkFailure = ManagedYolo26m({ directory }, artifact(), { throw IOException("network fixture") })
        assertFailsWith<IOException> { networkFailure.install { _, _ -> } }
        assertTrue(directory.listFiles()!!.isEmpty())
        cancelled.install { _, _ -> }
        assertTrue(cancelled.installed())
    }

    @Test
    fun customFilesAreRetainedAndDisablingClosesTheRuntime() = runBlocking {
        val custom = File(directory, "custom.onnx").apply { writeBytes(bytes) }
        var closed = 0
        var selectedPath: String? = null
        val manager = ManagedYolo26m({ directory }, createDetector = { path ->
            selectedPath = path
            object : VisionDetector {
                override fun detect(frame: RgbaFrame, confidenceThreshold: Float) = emptyList<VisionDetection>()
                override fun close() { closed++ }
            }
        })
        manager.detect(frame, enabled.copy(yoloModelPath = custom.absolutePath))
        assertEquals(custom.absolutePath, selectedPath)
        manager.remove()
        assertTrue(custom.isFile)
        assertEquals(0, closed)
        manager.detect(frame, VisionPreferences())
        assertEquals(1, closed)
        assertTrue(custom.isFile)
    }
}
