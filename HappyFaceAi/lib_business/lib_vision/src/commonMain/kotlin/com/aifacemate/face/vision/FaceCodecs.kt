package com.aifacemate.face.vision

import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * YuNet 2023mar: fixed 640×640 BGR input with raw 0–255 values, and per-stride score, box and keypoint
 * outputs. Decoding follows OpenCV's `FaceDetectorYN`, so any runtime that executes the model can reuse it.
 */
internal object YunetCodec {
    const val inputSize = 640
    private val strides = intArrayOf(8, 16, 32)
    val outputNames = strides.flatMap { stride -> listOf("cls_$stride", "obj_$stride", "bbox_$stride", "kps_$stride") }

    /** Model pixels = frame pixels × scale. Frames up to 640 px are copied unscaled into the top-left corner. */
    class Input(val chw: FloatArray, val scaleX: Float, val scaleY: Float)

    fun prepare(frame: RgbaFrame): Input {
        val fit = minOf(1f, inputSize.toFloat() / frame.width, inputSize.toFloat() / frame.height)
        val width = (frame.width * fit).roundToInt().coerceIn(1, inputSize)
        val height = (frame.height * fit).roundToInt().coerceIn(1, inputSize)
        val toSource = if (width == frame.width && height == frame.height) Affine2D.identity
            else Affine2D.resizeToSource(frame.width, frame.height, width, height)
        val chw = frame.sample(inputSize, inputSize, toSource, TensorLayout.Nchw, ChannelOrder.Bgr, Border.Clamp,
            contentRight = width, contentBottom = height)
        return Input(chw, width.toFloat() / frame.width, height.toFloat() / frame.height)
    }

    fun decode(
        outputs: Map<String, FloatArray>,
        input: Input,
        frameWidth: Int,
        frameHeight: Int,
        scoreThreshold: Float = 0.6f,
        nmsThreshold: Float = 0.3f,
        maxFaces: Int = 10,
    ): List<DetectedFace> {
        val boxes = ArrayList<FloatArray>()
        val keypoints = ArrayList<FloatArray>()
        val scores = ArrayList<Float>()
        for (stride in strides) {
            val columns = inputSize / stride
            val cls = outputs.getValue("cls_$stride")
            val obj = outputs.getValue("obj_$stride")
            val bbox = outputs.getValue("bbox_$stride")
            val kps = outputs.getValue("kps_$stride")
            for (index in cls.indices) {
                val score = sqrt(cls[index].coerceIn(0f, 1f) * obj[index].coerceIn(0f, 1f))
                if (score < scoreThreshold) continue
                val row = index / columns
                val column = index % columns
                val centerX = (column + bbox[index * 4]) * stride
                val centerY = (row + bbox[index * 4 + 1]) * stride
                val width = exp(bbox[index * 4 + 2]) * stride
                val height = exp(bbox[index * 4 + 3]) * stride
                boxes += floatArrayOf(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2)
                keypoints += FloatArray(10) { at -> (kps[index * 10 + at] + if (at % 2 == 0) column else row) * stride }
                scores += score
            }
        }
        fun point(x: Float, y: Float) = NormalizedPoint(x / input.scaleX / frameWidth, y / input.scaleY / frameHeight)
        return nonMaximumSuppression(boxes, scores, nmsThreshold, maxFaces).map { index ->
            val box = boxes[index]
            val kps = keypoints[index]
            DetectedFace(
                NormalizedBox(box[0] / input.scaleX / frameWidth, box[1] / input.scaleY / frameHeight,
                    box[2] / input.scaleX / frameWidth, box[3] / input.scaleY / frameHeight).clamped(),
                scores[index],
                FaceKeypoints(point(kps[0], kps[1]), point(kps[2], kps[3]), point(kps[4], kps[5]),
                    point(kps[6], kps[7]), point(kps[8], kps[9])),
            )
        }
    }
}

/** OpenCV Zoo MobileFaceNet: a 112×112 ArcFace-aligned RGB face, `(pixel − 127.5) / 127.5`, seven logits. */
internal object ExpressionCodec {
    const val inputSize = 112
    val classes = listOf(
        Expression.Angry, Expression.Disgust, Expression.Fear, Expression.Happy,
        Expression.Neutral, Expression.Sad, Expression.Surprise,
    )
    private val template = listOf(
        38.2946 to 51.6963, 73.5318 to 51.5014, 56.0252 to 71.7366, 41.5493 to 92.3655, 70.7299 to 92.2041,
    )

    fun prepare(frame: RgbaFrame, face: DetectedFace): FloatArray? {
        val toSource = face.keypoints?.let { alignment(it, frame) } ?: boxCrop(face.box, frame) ?: return null
        return frame.sample(inputSize, inputSize, toSource, TensorLayout.Nchw, ChannelOrder.Rgb, Border.Zero,
            mean = 127.5f, scale = 1f / 127.5f)
    }

    fun decode(logits: FloatArray): ExpressionResult {
        require(logits.size == classes.size) { "表情模型应输出 ${classes.size} 类，实际为 ${logits.size}" }
        val max = logits.max()
        val exps = logits.map { exp(it - max) }
        val total = exps.sum()
        val best = exps.indices.maxBy { exps[it] }
        return ExpressionResult(classes[best], exps[best] / total)
    }

    /** Least-squares similarity from the five keypoints to the template; returns the template→frame mapping. */
    private fun alignment(keypoints: FaceKeypoints, frame: RgbaFrame): Affine2D? {
        val points = keypoints.toList().map { it.x.toDouble() * frame.width to it.y.toDouble() * frame.height }
        val sourceX = points.sumOf { it.first } / 5
        val sourceY = points.sumOf { it.second } / 5
        val targetX = template.sumOf { it.first } / 5
        val targetY = template.sumOf { it.second } / 5
        var norm = 0.0
        var a = 0.0
        var b = 0.0
        points.indices.forEach { index ->
            val x = points[index].first - sourceX
            val y = points[index].second - sourceY
            val u = template[index].first - targetX
            val v = template[index].second - targetY
            norm += x * x + y * y
            a += x * u + y * v
            b += x * v - y * u
        }
        if (norm <= 1.0) return null
        a /= norm
        b /= norm
        val toTemplate = Affine2D(a, -b, targetX - a * sourceX + b * sourceY, b, a, targetY - b * sourceX - a * sourceY)
        return toTemplate.inverse()
    }

    /** Fallback for detectors without keypoints: the face box, squared around its centre. */
    private fun boxCrop(box: NormalizedBox, frame: RgbaFrame): Affine2D? {
        val width = (box.right - box.left) * frame.width
        val height = (box.bottom - box.top) * frame.height
        val side = maxOf(width, height).toDouble()
        if (side < 8) return null
        val left = (box.left + box.right) / 2.0 * frame.width - side / 2
        val top = (box.top + box.bottom) / 2.0 * frame.height - side / 2
        val step = side / inputSize
        return Affine2D(step, 0.0, left + 0.5 * step - 0.5, 0.0, step, top + 0.5 * step - 0.5)
    }
}
