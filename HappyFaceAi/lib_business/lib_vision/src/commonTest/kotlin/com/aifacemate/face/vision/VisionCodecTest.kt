package com.aifacemate.face.vision

import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.*

class VisionCodecTest {
    private fun frame(width: Int, height: Int, pixel: (x: Int, y: Int) -> Triple<Int, Int, Int>) =
        RgbaFrame(width, height, ByteArray(width * height * 4).also { bytes ->
            for (y in 0 until height) for (x in 0 until width) {
                val (r, g, b) = pixel(x, y)
                val at = (y * width + x) * 4
                bytes[at] = r.toByte(); bytes[at + 1] = g.toByte(); bytes[at + 2] = b.toByte(); bytes[at + 3] = -1
            }
        })

    private fun near(expected: Float, actual: Float, tolerance: Float = 1e-3f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, was $actual")

    @Test
    fun affineInverseAndCompositionRoundTrip() {
        val transform = Affine2D.rotation(30.0, 40.0, 25.0).then(Affine2D.translate(5.0, -7.0))
        val back = transform.then(transform.inverse())
        near(12f, back.mapX(12.0, 34.0).toFloat())
        near(34f, back.mapY(12.0, 34.0).toFloat())
        // OpenCV convention: +90° about the origin sends (1, 0) to (0, -1).
        val quarter = Affine2D.rotation(0.0, 0.0, 90.0)
        near(0f, quarter.mapX(1.0, 0.0).toFloat())
        near(-1f, quarter.mapY(1.0, 0.0).toFloat())
    }

    @Test
    fun samplingCopiesChannelsInRequestedOrderAndLayout() {
        val source = frame(2, 1) { x, _ -> if (x == 0) Triple(10, 20, 30) else Triple(40, 50, 60) }
        val bgr = source.sample(3, 1, Affine2D.identity, TensorLayout.Nchw, ChannelOrder.Bgr, Border.Clamp, contentRight = 2)
        assertContentEquals(floatArrayOf(30f, 60f, 0f, 20f, 50f, 0f, 10f, 40f, 0f), bgr)
        val rgb = source.sample(2, 1, Affine2D.identity, TensorLayout.Nhwc, ChannelOrder.Rgb, Border.Clamp, mean = 10f, scale = .5f)
        assertContentEquals(floatArrayOf(0f, 5f, 10f, 15f, 20f, 25f), rgb)
    }

    @Test
    fun resizeInterpolatesAndZeroBorderBlendsWithBlack() {
        val gradient = frame(2, 1) { x, _ -> Triple(x * 100, 0, 0) }
        val resized = gradient.sample(4, 1, Affine2D.resizeToSource(2, 1, 4, 1), TensorLayout.Nhwc, ChannelOrder.Rgb, Border.Clamp)
        assertEquals(listOf(0f, 25f, 75f, 100f), (0 until 4).map { resized[it * 3] })
        val white = frame(1, 1) { _, _ -> Triple(200, 200, 200) }
        val shifted = white.sample(1, 1, Affine2D.translate(-0.5, 0.0), TensorLayout.Nhwc, ChannelOrder.Rgb, Border.Zero)
        near(100f, shifted[0])
    }

    @Test
    fun nonMaximumSuppressionKeepsTheBestOfOverlappingBoxes() {
        val boxes = listOf(floatArrayOf(0f, 0f, 10f, 10f), floatArrayOf(1f, 1f, 11f, 11f), floatArrayOf(20f, 20f, 30f, 30f))
        assertEquals(listOf(1, 2), nonMaximumSuppression(boxes, listOf(.7f, .9f, .8f), .3f, 10))
        assertEquals(listOf(1), nonMaximumSuppression(boxes, listOf(.7f, .9f, .8f), .3f, 1))
    }

    @Test
    fun yunetDecodesAnchorsIntoNormalizedFacesAndKeypoints() {
        val outputs = YunetCodec.outputNames.associateWith { name ->
            val stride = name.substringAfter('_').toInt()
            val anchors = (640 / stride) * (640 / stride)
            FloatArray(anchors * when { name.startsWith("bbox") -> 4; name.startsWith("kps") -> 10; else -> 1 })
        }.toMutableMap()
        // One face at stride 8, row 10, column 20: centre (164, 84), 32×32, keypoints offset (0.5, 0.25) cells.
        val index = 10 * 80 + 20
        outputs.getValue("cls_8")[index] = 0.9f
        outputs.getValue("obj_8")[index] = 0.9f
        outputs.getValue("bbox_8").apply { this[index * 4] = .5f; this[index * 4 + 1] = .5f; this[index * 4 + 2] = ln(4f); this[index * 4 + 3] = ln(4f) }
        outputs.getValue("kps_8").apply { for (k in 0 until 5) { this[index * 10 + 2 * k] = .5f; this[index * 10 + 2 * k + 1] = .25f } }
        val input = YunetCodec.prepare(frame(640, 480) { _, _ -> Triple(0, 0, 0) })
        val face = YunetCodec.decode(outputs, input, 640, 480).single()
        near(0.9f, face.score)
        near(148f / 640, face.box.left); near(68f / 480, face.box.top)
        near(180f / 640, face.box.right); near(100f / 480, face.box.bottom)
        near(164f / 640, face.keypoints!!.noseTip.x); near(82f / 480, face.keypoints!!.noseTip.y)
    }

    @Test
    fun largeFramesAreScaledIntoTheFixedYunetInput() {
        val input = YunetCodec.prepare(frame(1280, 960) { _, _ -> Triple(255, 0, 0) })
        near(.5f, input.scaleX); near(.5f, input.scaleY)
        // Red is written to the last BGR plane; the padding below 480 rows stays black.
        assertEquals(255f, input.chw[2 * 640 * 640 + 100 * 640 + 100])
        assertEquals(0f, input.chw[2 * 640 * 640 + 600 * 640 + 100])
    }

    @Test
    fun expressionDecodingIsSoftmaxOverSevenClasses() {
        val result = ExpressionCodec.decode(floatArrayOf(0f, 0f, 0f, 5f, 0f, 0f, 0f))
        assertEquals(Expression.Happy, result.expression)
        assertTrue(result.confidence > .95f)
        assertFailsWith<IllegalArgumentException> { ExpressionCodec.decode(FloatArray(8)) }
    }

    @Test
    fun alignedFaceMapsTemplateKeypointsOntoTheFrame() {
        val face = DetectedFace(NormalizedBox(0f, 0f, 1f, 1f), 1f, FaceKeypoints(
            NormalizedPoint(38.2946f / 224, 51.6963f / 224), NormalizedPoint(73.5318f / 224, 51.5014f / 224),
            NormalizedPoint(56.0252f / 224, 71.7366f / 224), NormalizedPoint(41.5493f / 224, 92.3655f / 224),
            NormalizedPoint(70.7299f / 224, 92.2041f / 224),
        ))
        // The keypoints already sit on the template, so the alignment is the identity and output pixels copy the frame.
        val bright = frame(224, 224) { x, _ -> if (x < 56) Triple(255, 255, 255) else Triple(0, 0, 0) }
        val input = ExpressionCodec.prepare(bright, face)!!
        near(1f, input[10 * 112 + 10])
        near(-1f, input[10 * 112 + 111], 0.02f)
    }

    @Test
    fun uprightPalmBuildsAnAxisAlignedHandCropAndMapsLandmarksBack() {
        // Wrist below the middle-finger base: already upright, so the crop is not rotated.
        val keypoints = listOf(100f to 140f, 90f to 110f, 100f to 100f, 110f to 105f, 120f to 115f, 80f to 125f, 85f to 120f)
        val palm = PalmCodec.Palm(80f, 100f, 120f, 140f, .9f, keypoints)
        val roi = HandCodec.roi(palm)!!
        near(roi.points.mapX(112.0, 0.0).toFloat(), roi.points.mapX(112.0, 224.0).toFloat())
        // A landmark at the crop centre maps to the rotated box centre shifted 0.4 box heights upwards.
        val centre = HandCodec.landmarks(FloatArray(63) { if (it % 3 == 2) 0f else 112f }, roi, 200, 200).first()
        near(100f / 200, centre.x)
        near((120f - 0.4f * 40f) / 200, centre.y)
    }

    @Test
    fun palmDecodingAppliesAnchorsSigmoidAndMinimumSize() {
        val deltas = FloatArray(PalmCodec.anchorCount * 18)
        val logits = FloatArray(PalmCodec.anchorCount) { -10f }
        // Anchor 0 is centred at (1/48, 1/48) of the 192 input; give it a 48×48 box.
        logits[0] = 4f
        deltas[2] = 48f; deltas[3] = 48f
        val input = PalmCodec.prepare(frame(192, 192) { _, _ -> Triple(0, 0, 0) })
        val palm = PalmCodec.decode(deltas, logits, input, 192, 192).single()
        near(1f / (1f + kotlin.math.exp(-4f)), palm.score)
        // Centre (4, 4) with a 48 px box, clamped to the frame.
        assertEquals(listOf(0f, 0f, 28f, 28f), listOf(palm.left, palm.top, palm.right, palm.bottom))
        logits[0] = -10f; logits[1] = 4f; deltas[18 + 2] = 4f; deltas[18 + 3] = 4f
        assertTrue(PalmCodec.decode(deltas, logits, input, 192, 192).isEmpty())
    }

    @Test
    fun gesturesUseIsotropicDistancesAndTellAFistFromAPinch() {
        fun hand(points: List<Pair<Float, Float>>) = DetectedHand(NormalizedBox(0f, 0f, 1f, 1f), .95f,
            points.map { (x, y) -> NormalizedPoint(x / 400, y / 200) })
        val wrist = 200f to 190f
        fun finger(base: Float, extended: Boolean) = if (extended) listOf(base to 150f, base to 120f, base to 100f, base to 80f)
            else listOf(base to 150f, base to 130f, base to 150f, base to 165f)
        val open = listOf(wrist, 170f to 170f, 150f to 150f, 140f to 130f, 130f to 110f) +
            finger(185f, true) + finger(200f, true) + finger(215f, true) + finger(230f, true)
        assertEquals(HandGesture.OpenPalm, RuleBasedGestureClassifier.classify(hand(open), 400, 200))
        val fist = listOf(wrist, 190f to 175f, 185f to 160f, 188f to 158f, 190f to 157f) +
            finger(185f, false) + finger(200f, false) + finger(215f, false) + finger(230f, false)
        assertEquals(HandGesture.Fist, RuleBasedGestureClassifier.classify(hand(fist), 400, 200))
        assertNull(RuleBasedGestureClassifier.classify(hand(open).copy(score = .3f), 400, 200))
    }
}
