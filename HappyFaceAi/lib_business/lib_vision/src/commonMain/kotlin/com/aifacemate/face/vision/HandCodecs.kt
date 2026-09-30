package com.aifacemate.face.vision

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** MediaPipe palm detection 2023feb: 192×192 RGB/255 NHWC input, 2016 anchors with box and seven keypoints. */
internal object PalmCodec {
    const val inputSize = 192
    const val anchorCount = 2016

    /** Frame pixel = model pixel / 192 × [scale] − bias. */
    class Input(val nhwc: FloatArray, val scale: Float, val biasX: Float, val biasY: Float)

    /** Frame pixels. Keypoint 0 is the wrist and keypoint 2 the middle-finger base. */
    class Palm(val left: Float, val top: Float, val right: Float, val bottom: Float, val score: Float, val keypoints: List<Pair<Float, Float>>)

    private val anchors: List<Pair<Float, Float>> by lazy {
        buildList {
            for (y in 0 until 24) for (x in 0 until 24) repeat(2) { add((x + .5f) / 24 to (y + .5f) / 24) }
            for (y in 0 until 12) for (x in 0 until 12) repeat(6) { add((x + .5f) / 12 to (y + .5f) / 12) }
        }
    }

    fun prepare(frame: RgbaFrame): Input {
        val fit = min(inputSize.toFloat() / frame.height, inputSize.toFloat() / frame.width)
        val width = (frame.width * fit).toInt().coerceIn(1, inputSize)
        val height = (frame.height * fit).toInt().coerceIn(1, inputSize)
        val padLeft = (inputSize - width) / 2
        val padTop = (inputSize - height) / 2
        val toSource = Affine2D.translate(-padLeft.toDouble(), -padTop.toDouble())
            .then(Affine2D.resizeToSource(frame.width, frame.height, width, height))
        val nhwc = frame.sample(inputSize, inputSize, toSource, TensorLayout.Nhwc, ChannelOrder.Rgb, Border.Clamp,
            scale = 1f / 255f, contentLeft = padLeft, contentTop = padTop,
            contentRight = padLeft + width, contentBottom = padTop + height)
        return Input(nhwc, max(frame.width, frame.height).toFloat(), padLeft / fit, padTop / fit)
    }

    fun decode(
        deltas: FloatArray,
        logits: FloatArray,
        input: Input,
        frameWidth: Int,
        frameHeight: Int,
        scoreThreshold: Float = 0.5f,
        nmsThreshold: Float = 0.3f,
        maxPalms: Int = 2,
    ): List<Palm> {
        require(deltas.size == anchorCount * 18 && logits.size == anchorCount) { "手掌检测模型输出维度不正确" }
        val candidates = ArrayList<Palm>()
        for (index in 0 until anchorCount) {
            val confidence = 1f / (1f + exp(-logits[index]))
            if (confidence < scoreThreshold) continue
            val (anchorX, anchorY) = anchors[index]
            val offset = index * 18
            fun x(value: Float) = (value / inputSize + anchorX) * input.scale - input.biasX
            fun y(value: Float) = (value / inputSize + anchorY) * input.scale - input.biasY
            val centerX = x(deltas[offset])
            val centerY = y(deltas[offset + 1])
            val width = deltas[offset + 2] / inputSize * input.scale
            val height = deltas[offset + 3] / inputSize * input.scale
            val left = (centerX - width / 2).coerceIn(0f, frameWidth.toFloat())
            val top = (centerY - height / 2).coerceIn(0f, frameHeight.toFloat())
            val right = (centerX + width / 2).coerceIn(left, frameWidth.toFloat())
            val bottom = (centerY + height / 2).coerceIn(top, frameHeight.toFloat())
            if (right - left < 16 || bottom - top < 16) continue
            val keypoints = List(7) { point -> x(deltas[offset + 4 + 2 * point]) to y(deltas[offset + 5 + 2 * point]) }
            candidates += Palm(left, top, right, bottom, confidence, keypoints)
        }
        val boxes = candidates.map { floatArrayOf(it.left, it.top, it.right, it.bottom) }
        return nonMaximumSuppression(boxes, candidates.map { it.score }, nmsThreshold, maxPalms).map(candidates::get)
    }
}

/**
 * MediaPipe hand landmarks 2023feb: a 224×224 RGB/255 NHWC crop, rotated so the wrist-to-middle-finger axis
 * points up, and 21 × (x, y, z) outputs in crop pixels. The crop is one affine map, so landmarks map back exactly.
 */
internal object HandCodec {
    const val inputSize = 224

    /** [sampling] maps crop pixel centres to frame pixels; [points] maps continuous crop coordinates. */
    class Roi(val sampling: Affine2D, val points: Affine2D)

    fun roi(palm: PalmCodec.Palm): Roi? {
        if (palm.right - palm.left < 8f || palm.bottom - palm.top < 8f) return null
        val (wristX, wristY) = palm.keypoints[0]
        val (middleX, middleY) = palm.keypoints[2]
        val degrees = 90.0 - atan2(-(middleY - wristY).toDouble(), (middleX - wristX).toDouble()) * 180 / PI
        val rotate = Affine2D.rotation((wristX + middleX) / 2.0, (wristY + middleY) / 2.0, degrees)
        val rotated = palm.keypoints.map { (x, y) -> rotate.mapX(x.toDouble(), y.toDouble()) to rotate.mapY(x.toDouble(), y.toDouble()) }
        val minX = rotated.minOf { it.first }
        val minY = rotated.minOf { it.second }
        val width = rotated.maxOf { it.first } - minX
        val height = rotated.maxOf { it.second } - minY
        // Same enlargement as OpenCV Zoo: 3× the rotated palm box, shifted 0.4 box heights towards the fingers.
        val centerX = minX + width / 2
        val centerY = minY + height / 2 - 0.4 * height
        val side = max(width, height) * 3
        if (side < 8) return null
        val step = side / inputSize
        val left = centerX - side / 2
        val top = centerY - side / 2
        val toFrame = rotate.inverse()
        return Roi(
            Affine2D(step, 0.0, left + 0.5 * step - 0.5, 0.0, step, top + 0.5 * step - 0.5).then(toFrame),
            Affine2D(step, 0.0, left, 0.0, step, top).then(toFrame),
        )
    }

    fun prepare(frame: RgbaFrame, roi: Roi): FloatArray =
        frame.sample(inputSize, inputSize, roi.sampling, TensorLayout.Nhwc, ChannelOrder.Rgb, Border.Zero, scale = 1f / 255f)

    fun landmarks(values: FloatArray, roi: Roi, frameWidth: Int, frameHeight: Int): List<NormalizedPoint> {
        require(values.size == 63) { "手部关键点模型应输出 21×3 个值" }
        return List(21) { point ->
            val x = values[point * 3].toDouble()
            val y = values[point * 3 + 1].toDouble()
            NormalizedPoint((roi.points.mapX(x, y) / frameWidth).toFloat(), (roi.points.mapY(x, y) / frameHeight).toFloat())
        }
    }
}
