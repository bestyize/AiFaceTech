package com.aifacemate.face.camera

import kotlin.test.*

class CameraPixelsTest {
    @Test
    fun fullScreenCropKeepsOverlayCoordinatesAlignedForBothLenses() {
        val normal = CameraPreviewTransform.crop(640, 480, 300f, 600f, false)
        val mirrored = CameraPreviewTransform.crop(640, 480, 300f, 600f, true)
        assertEquals(1.25f, normal.scale)
        assertEquals(-250f, normal.left)
        assertEquals(0f, normal.top)
        assertEquals(150f, normal.x(320f))
        assertEquals(300f, normal.y(240f))
        assertEquals(normal.x(400f), mirrored.x(240f))
    }

    @Test
    fun photoMatchesCentreCropAndSelfieWithoutChangingTheAnalysisFrame() {
        val pixels = (1..8).flatMap { listOf(it.toByte(), 0.toByte(), 0.toByte(), (-1).toByte()) }.toByteArray()
        val frame = CameraFrame(4, 2, pixels, 12, mirrored = true)
        val photo = frame.photoFrame(1f)
        assertEquals(2, photo.width)
        assertEquals(2, photo.height)
        assertEquals(listOf(3, 2, 7, 6), photo.rgba.asList().chunked(4).map { it.first().toInt() })
        assertFalse(photo.mirrored)
        assertEquals(12, photo.sequence)
        assertContentEquals(pixels, frame.rgba)
    }

    @Test
    fun desktopZoomUsesTheCentreOfTheFrameForPreviewAndInference() {
        val pixels = (1..16).flatMap { listOf(it.toByte(), 0.toByte(), 0.toByte(), (-1).toByte()) }.toByteArray()
        val frame = CameraFrame(4, 4, pixels, 2)
        assertSame(frame, frame.digitalZoom(1f))
        assertEquals(listOf(6, 6, 7, 7, 6, 6, 7, 7, 10, 10, 11, 11, 10, 10, 11, 11),
            frame.digitalZoom(2f).rgba.asList().chunked(4).map { it.first().toInt() })
    }

    @Test
    fun yuvUsesPlaneRowAndPixelStrides() {
        val y = CameraPixels.Plane(byteArrayOf(81, 81, 81, 81, 0, 81, 81, 81, 81), 5)
        val u = CameraPixels.Plane(byteArrayOf(-128, 0, -128), 4, 2)
        val v = CameraPixels.Plane(byteArrayOf(-128, -1, -128), 4, 2)
        val pixels = CameraPixels.yuv420(4, 2, y, u, v)
        pixels.asList().chunked(4).forEach { pixel -> assertEquals(listOf<Byte>(76, 76, 76, -1), pixel) }
    }

    @Test
    fun bgraDropsPaddingAndNormalizesColorAndAlpha() {
        val source = byteArrayOf(10, 20, 30, 0, 40, 50, 60, 0, 99, 99, 99, 99,
            70, 80, 90, 0, 100, 110, 120, 0, 99, 99, 99, 99)
        assertContentEquals(byteArrayOf(30, 20, 10, -1, 60, 50, 40, -1, 90, 80, 70, -1, 120, 110, 100, -1),
            CameraPixels.bgra(2, 2, source, 12))
    }

    @Test
    fun rotationPreservesPixelsAndMirroringIsOnlyPreviewMetadata() {
        val pixels = (1..6).flatMap { listOf(it.toByte(), 0.toByte(), 0.toByte(), (-1).toByte()) }.toByteArray()
        val expected = mapOf(90 to listOf(5, 3, 1, 6, 4, 2), 180 to listOf(6, 5, 4, 3, 2, 1), 270 to listOf(2, 4, 6, 1, 3, 5))
        expected.forEach { (rotation, order) ->
            val frame = CameraPixels.upright(2, 3, pixels, rotation, 7, true)
            assertEquals(order, frame.rgba.asList().chunked(4).map { it.first().toInt() })
            assertEquals(if (rotation == 180) 2 else 3, frame.width)
            assertEquals(7, frame.sequence)
            assertTrue(frame.mirrored)
        }
    }

    @Test
    fun detectionCoordinatesFollowFitLetterboxAndMirroring() {
        val normal = CameraPreviewTransform.fit(640, 480, 800f, 450f, false)
        assertEquals(100f, normal.left)
        assertEquals(0f, normal.top)
        assertEquals(193.75f, normal.x(100f))
        assertEquals(93.75f, normal.y(100f))
        val mirrored = CameraPreviewTransform.fit(640, 480, 800f, 450f, true)
        assertEquals(606.25f, mirrored.x(100f))
        assertEquals(193.75f, mirrored.x(540f))
    }

    @Test
    fun invalidFramesAndUnsupportedRotationsAreRejected() {
        assertFailsWith<IllegalArgumentException> { CameraFrame(1, 1, ByteArray(3), 0) }
        assertFailsWith<IllegalArgumentException> { CameraPixels.upright(1, 1, ByteArray(4), 45, 0, false) }
        assertFailsWith<IllegalArgumentException> { CameraPixels.bgra(2, 1, ByteArray(4), 4) }
    }
}
