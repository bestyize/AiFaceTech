package com.aifacemate.face.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Yolo26mCodecTest {
    @Test
    fun letterboxRestoresNormalizedFrameCoordinates() {
        val mapping = Yolo26mCodec.letterbox(320, 240)
        assertEquals(640, mapping.resizedWidth)
        assertEquals(480, mapping.resizedHeight)
        assertEquals(80, mapping.top)
        val detection = Yolo26mCodec.decode(floatArrayOf(100f, 100f, 300f, 300f, .9f, 15f), mapping).single()
        assertEquals("cat", detection.label)
        assertEquals(15, detection.classIndex)
        assertEquals(NormalizedBox(50f / 320, 10f / 240, 150f / 320, 110f / 240), detection.box)
    }

    @Test
    fun filtersBackgroundInvalidNumbersAndBoxesOutsideImage() {
        val mapping = Yolo26mCodec.letterbox(320, 240)
        val detections = Yolo26mCodec.decode(floatArrayOf(
            0f, 0f, 640f, 640f, .8f, 0f,
            0f, 0f, 10f, 10f, .1f, 0f,
            0f, 0f, 10f, 10f, Float.NaN, 0f,
            0f, 0f, 10f, 10f, .9f, 80f,
            0f, 0f, 10f, 10f, .9f, 0f,
        ), mapping)
        assertEquals(1, detections.size)
        assertEquals(NormalizedBox(0f, 0f, 1f, 1f), detections.single().box)
        assertFailsWith<IllegalArgumentException> { Yolo26mCodec.decode(FloatArray(7), mapping) }
    }

    @Test
    fun rgbaPreprocessingIsRgbChwAndPadsWith114() {
        val pixels = ByteArray(4 * 4) { index ->
            when (index % 4) { 0 -> 255.toByte(); 1 -> 128.toByte(); 2 -> 0.toByte(); else -> 255.toByte() }
        }
        val prepared = Yolo26mCodec.prepare(RgbaFrame(4, 1, pixels))
        val center = 320 * 640 + 320
        assertEquals(1f, prepared.chw[center])
        assertTrue(kotlin.math.abs(prepared.chw[640 * 640 + center] - 128f / 255f) < 0.0001f)
        assertEquals(0f, prepared.chw[2 * 640 * 640 + center])
        assertEquals(114f / 255f, prepared.chw[0])
    }
}
