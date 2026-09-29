package com.aifacemate.face.vision

import java.io.File
import java.nio.ByteOrder
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bytedeco.opencv.global.opencv_core.BORDER_CONSTANT
import org.bytedeco.opencv.global.opencv_core.CV_32F
import org.bytedeco.opencv.global.opencv_core.CV_32FC1
import org.bytedeco.opencv.global.opencv_core.CV_32FC3
import org.bytedeco.opencv.global.opencv_core.CV_64F
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.global.opencv_dnn.DNN_BACKEND_OPENCV
import org.bytedeco.opencv.global.opencv_dnn.DNN_TARGET_CPU
import org.bytedeco.opencv.global.opencv_imgproc.INTER_LINEAR
import org.bytedeco.opencv.global.opencv_dnn.blobFromImage
import org.bytedeco.opencv.global.opencv_dnn.readNet
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2RGB
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_RGBA2BGR
import org.bytedeco.opencv.global.opencv_imgproc.cvtColor
import org.bytedeco.opencv.global.opencv_imgproc.getRotationMatrix2D
import org.bytedeco.opencv.global.opencv_imgproc.resize
import org.bytedeco.opencv.global.opencv_imgproc.warpAffine
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.MatVector
import org.bytedeco.opencv.opencv_core.Point2f
import org.bytedeco.opencv.opencv_core.Rect
import org.bytedeco.opencv.opencv_core.Scalar
import org.bytedeco.opencv.opencv_core.Size
import org.bytedeco.opencv.opencv_dnn.Net
import org.bytedeco.opencv.opencv_core.StringVector
import org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN

/** DNN models are created only after the matching feature is enabled and its models are installed. */
internal class OpenCvLocalVisionEngine(private val directory: () -> File) {
    private val cache = LocalVisionModelCache(directory)
    private val gate = Mutex()
    private var faceDetector: FaceDetectorYN? = null
    private var expressionNet: Net? = null
    private var palmNet: Net? = null
    private var handNet: Net? = null
    private var faceDetectorPath: String? = null
    private var expressionPath: String? = null

    fun modelsInstalled(task: LocalVisionTask) = cache.installed(task)

    suspend fun downloadModels(task: LocalVisionTask, progress: (Long, Long) -> Unit) = gate.withLock { cache.download(task, progress) }

    suspend fun removeModels(task: LocalVisionTask) = gate.withLock {
        withContext(Dispatchers.IO) {
            when (task) {
                LocalVisionTask.FacialExpressions -> closeFace()
                LocalVisionTask.HandGestures -> closeHands()
            }
            cache.remove(task)
        }
    }

    suspend fun unload() = withContext(NonCancellable + Dispatchers.Default) {
        gate.withLock { closeFace(); closeHands() }
    }

    suspend fun analyze(frame: RgbaFrame, preferences: VisionPreferences): List<LocalVisualFinding> = gate.withLock {
        withContext(Dispatchers.Default) {
            val config = preferences.normalized()
            val results = ArrayList<LocalVisualFinding>(4)
            if (config.expressionEnabled) results += analyzeExpressions(frame, config)
            if (config.gestureEnabled) results += analyzeHands(frame, config)
            results
        }
    }

    private suspend fun analyzeExpressions(frame: RgbaFrame, config: VisionPreferences): List<LocalVisualFinding> {
        require(config.modelPathError(expression = true) == null) { "表情模型路径须使用 ONNX 文件" }
        val facePath = cache.verifiedPath(LocalVisionModelCatalog.faceDetection)
        val classPath = config.expressionModelPath.ifBlank { cache.verifiedPath(LocalVisionModelCatalog.expression) }
        if (faceDetectorPath != facePath) {
            faceDetector?.close()
            faceDetector = FaceDetectorYN.create(facePath, "", Size(frame.width, frame.height), 0.6f, 0.3f, 10,
                DNN_BACKEND_OPENCV, DNN_TARGET_CPU)
            faceDetectorPath = facePath
        }
        if (expressionPath != classPath) {
            expressionNet?.close()
            expressionNet = readNet(classPath).also {
                it.setPreferableBackend(DNN_BACKEND_OPENCV)
                it.setPreferableTarget(DNN_TARGET_CPU)
            }
            expressionPath = classPath
        }
        return withBgr(frame) { image ->
            faceDetector!!.setInputSize(Size(frame.width, frame.height))
            Mat().use { detected ->
                faceDetector!!.detect(image, detected)
                val count = detected.rows()
                if (count == 0) return@withBgr emptyList()
                val rows = detected.floatValues(count * 15)
                buildList {
                    for (index in 0 until count) {
                        val offset = index * 15
                        val landmarks = List(5) { point -> rows[offset + 4 + point * 2] to rows[offset + 5 + point * 2] }
                        val aligned = alignFace(image, landmarks)
                        try {
                            val input = blobFromImage(aligned, 1.0 / 127.5, Size(112, 112),
                                Scalar(127.5, 127.5, 127.5, 0.0), true, false, CV_32F)
                            try {
                                val network = expressionNet!!
                                network.setInput(input, "data", 1.0, Scalar())
                                val logits = network.forward("label").use { it.floatValues(FacialExpression.entries.size) }
                                val probabilities = softmax(logits)
                                val classIndex = probabilities.indices.maxByOrNull(probabilities::get) ?: continue
                                val score = probabilities[classIndex]
                                if (score >= config.expressionConfidenceThreshold) {
                                    val left = rows[offset].coerceIn(0f, frame.width.toFloat())
                                    val top = rows[offset + 1].coerceIn(0f, frame.height.toFloat())
                                    val right = (left + rows[offset + 2]).coerceIn(left, frame.width.toFloat())
                                    val bottom = (top + rows[offset + 3]).coerceIn(top, frame.height.toFloat())
                                    add(LocalVisualFinding(LocalVisionTask.FacialExpressions, FacialExpression.entries[classIndex].label, score,
                                        PixelBox(left, top, right, bottom)))
                                }
                            } finally { input.close() }
                        } finally { aligned.close() }
                    }
                }
            }
        }
    }

    private suspend fun analyzeHands(frame: RgbaFrame, config: VisionPreferences): List<LocalVisualFinding> {
        require(config.modelPathError(expression = false, gesture = true) == null) { "手势关键点模型须使用 ONNX 文件" }
        val palmPath = cache.verifiedPath(LocalVisionModelCatalog.palmDetection)
        val landmarkPath = config.gestureModelPath.ifEmpty { cache.verifiedPath(LocalVisionModelCatalog.handLandmarks) }
        if (palmNet == null) palmNet = readNet(palmPath).also {
            it.setPreferableBackend(DNN_BACKEND_OPENCV); it.setPreferableTarget(DNN_TARGET_CPU)
        }
        if (handNet == null) handNet = readNet(landmarkPath).also {
            it.setPreferableBackend(DNN_BACKEND_OPENCV); it.setPreferableTarget(DNN_TARGET_CPU)
        }
        return withBgr(frame) { image ->
            val palmInput = makePalmInput(image)
            val palms = try { findPalms(palmInput, frame.width, frame.height) }
                finally { palmInput.mat.close() }
            buildList {
                palms.forEach { palm ->
                    val input = makeCanonicalHandInput(image, palm) ?: return@forEach
                    val (score, points) = try { classifyHand(input) } finally { input.close() }
                    if (score < config.gestureConfidenceThreshold) return@forEach
                    val label = LocalHandGestureClassifier.classify(points, score) ?: return@forEach
                    add(LocalVisualFinding(LocalVisionTask.HandGestures, label, score, PixelBox(palm.left, palm.top, palm.right, palm.bottom)))
                }
            }
        }
    }

    private fun withBgr(frame: RgbaFrame, process: (Mat) -> List<LocalVisualFinding>): List<LocalVisualFinding> {
        val rgba32 = Mat(frame.height, frame.width, org.bytedeco.opencv.global.opencv_core.CV_8UC4)
        try {
            val input = rgba32.data().asBuffer()
            for (index in 0 until frame.width * frame.height) {
                val at = index * 4
                input.put(frame.pixels[at]); input.put(frame.pixels[at + 1]); input.put(frame.pixels[at + 2]); input.put(frame.pixels[at + 3])
            }
            val bgr = Mat(); cvtColor(rgba32, bgr, COLOR_RGBA2BGR)
            try { return process(bgr) } finally { bgr.close() }
        } finally { rgba32.close() }
    }

    private fun alignFace(image: Mat, landmarks: List<Pair<Float, Float>>): Mat {
        val reference = arrayOf(38.2946 to 51.6963, 73.5318 to 51.5014, 56.0252 to 71.7366, 41.5493 to 92.3655, 70.7299 to 92.2041)
        val sourceX = landmarks.sumOf { it.first.toDouble() } / 5
        val sourceY = landmarks.sumOf { it.second.toDouble() } / 5
        val targetX = reference.sumOf { it.first } / 5
        val targetY = reference.sumOf { it.second } / 5
        var norm = 0.0; var a = 0.0; var b = 0.0
        landmarks.indices.forEach { index ->
            val x = landmarks[index].first - sourceX; val y = landmarks[index].second - sourceY
            val u = reference[index].first - targetX; val v = reference[index].second - targetY
            norm += x * x + y * y; a += x * u + y * v; b += x * v - y * u
        }
        require(norm > 1.0) { "无法对齐人脸" }
        a /= norm; b /= norm
        val matrix = Mat(2, 3, CV_64F)
        val values = doubleArrayOf(a, -b, targetX - a * sourceX + b * sourceY, b, a, targetY - b * sourceX - a * sourceY)
        val native = matrix.data().asBuffer().order(ByteOrder.nativeOrder()).asDoubleBuffer()
        native.put(values); native.rewind()
        return Mat().also { aligned -> warpAffine(image, aligned, matrix, Size(112, 112), INTER_LINEAR, BORDER_CONSTANT, Scalar()) }
            .also { matrix.close() }
    }

    private fun makePalmInput(image: Mat): PalmTensor {
        val width = image.cols(); val height = image.rows()
        val scale = min(192f / height, 192f / width)
        val resizedWidth = (width * scale).toInt().coerceIn(1, 192)
        val resizedHeight = (height * scale).toInt().coerceIn(1, 192)
        val padLeft = (192 - resizedWidth) / 2; val padTop = (192 - resizedHeight) / 2
        val resized = Mat()
        resize(image, resized, Size(resizedWidth, resizedHeight))
        val pad = Mat(192, 192, CV_8UC3, Scalar())
        val region = Mat(pad, Rect(padLeft, padTop, resizedWidth, resizedHeight))
        try { resized.copyTo(region) } finally { region.close(); resized.close() }
        val rgb = Mat()
        cvtColor(pad, rgb, COLOR_BGR2RGB)
        val normalized = Mat()
        rgb.convertTo(normalized, CV_32FC3, 1.0 / 255.0, 0.0)
        pad.close(); rgb.close()
        val input = Mat(4, intArrayOf(1, 192, 192, 3), CV_32F)
        val source = normalized.data().asBuffer().order(ByteOrder.nativeOrder()).asFloatBuffer()
        val destination = input.data().asBuffer().order(ByteOrder.nativeOrder()).asFloatBuffer()
        destination.put(source); normalized.close()
        return PalmTensor(input, max(width, height).toFloat(), padLeft / scale, padTop / scale)
    }

    private fun findPalms(tensor: PalmTensor, width: Int, height: Int): List<DetectedPalm> {
        palmNet!!.setInput(tensor.mat)
        return MatVector().use { outputs ->
            palmNet!!.forward(outputs)
            check(outputs.size() == 2L) { "手掌检测模型输出维度不正确" }
            val deltas = outputs.get(0).use { it.floatValues(2016 * 18) }
            val scores = outputs.get(1).use { it.floatValues(2016) }
            val anchors = PalmAnchors
            val candidates = ArrayList<DetectedPalm>()
            scores.forEachIndexed { index, logit ->
                val confidence = 1f / (1f + exp(-logit))
                if (confidence < .5f) return@forEachIndexed
                val anchor = anchors[index]
                val offset = index * 18
                val cx = (deltas[offset] / 192f + anchor.x) * tensor.scale - tensor.biasX
                val cy = (deltas[offset + 1] / 192f + anchor.y) * tensor.scale - tensor.biasY
                val bw = deltas[offset + 2] / 192f * tensor.scale
                val bh = deltas[offset + 3] / 192f * tensor.scale
                val left = (cx - bw / 2).coerceIn(0f, width.toFloat())
                val top = (cy - bh / 2).coerceIn(0f, height.toFloat())
                val right = (cx + bw / 2).coerceIn(left, width.toFloat())
                val bottom = (cy + bh / 2).coerceIn(top, height.toFloat())
                if (right - left < 16 || bottom - top < 16) return@forEachIndexed
                val keypoints = List(7) { point ->
                    (deltas[offset + 4 + 2 * point] / 192f + anchor.x) * tensor.scale - tensor.biasX to
                        (deltas[offset + 5 + 2 * point] / 192f + anchor.y) * tensor.scale - tensor.biasY
                }
                candidates += DetectedPalm(left, top, right, bottom, confidence, keypoints)
            }
            candidates.sortedByDescending { it.score }.fold(mutableListOf<DetectedPalm>()) { selected, palm ->
                if (selected.none { intersectionOverUnion(it, palm) > .3f }) selected += palm
                selected
            }.take(2)
        }
    }

    private fun makeHandInput(image: Mat, palm: DetectedPalm): Mat? {
        val palmWidth = palm.right - palm.left; val palmHeight = palm.bottom - palm.top
        if (palmWidth < 8 || palmHeight < 8) return null
        val expand = max(palmWidth, palmHeight) * 4.0f
        val cropLeft = (palm.left + palmWidth / 2 - expand / 2).toInt().coerceIn(0, image.cols() - 1)
        val cropTop = (palm.top + palmHeight / 2 - expand / 2).toInt().coerceIn(0, image.rows() - 1)
        val cropRight = (palm.left + palmWidth / 2 + expand / 2).toInt().coerceIn(cropLeft + 1, image.cols())
        val cropBottom = (palm.top + palmHeight / 2 + expand / 2).toInt().coerceIn(cropTop + 1, image.rows())
        val side = max(cropRight - cropLeft, cropBottom - cropTop)
        val padded = Mat(side, side, CV_8UC3, Scalar())
        val src = Mat(image, Rect(cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop))
        try {
            val dstX = (side - (cropRight - cropLeft)) / 2
            val dstY = (side - (cropBottom - cropTop)) / 2
            val dst = Mat(padded, Rect(dstX, dstY, cropRight - cropLeft, cropBottom - cropTop))
            try { src.copyTo(dst) } finally { dst.close() }
            val p1 = palm.landmarks[0]
            val p2 = palm.landmarks[2]
            val angle = 90.0 - Math.toDegrees(atan2((p2.second - p1.second).toDouble(), (p2.first - p1.first).toDouble()))
            val rotation = getRotationMatrix2D(Point2f(side / 2f, side / 2f), angle, 1.0)
            val rotated = Mat()
            try {
                warpAffine(padded, rotated, rotation, Size(side, side), INTER_LINEAR, BORDER_CONSTANT, Scalar())
                val rgb = Mat(); cvtColor(rotated, rgb, COLOR_BGR2RGB)
                val resized = Mat(); resize(rgb, resized, Size(224, 224)); rgb.close()
                val modelInput = Mat(4, intArrayOf(1, 224, 224, 3), CV_32F)
                val source = resized.data().asBuffer()
                val destination = modelInput.data().asBuffer().order(ByteOrder.nativeOrder()).asFloatBuffer()
                repeat(224 * 224 * 3) { destination.put((source.get().toInt() and 255) / 255f) }
                resized.close()
                return modelInput
            } finally { rotation.close(); rotated.close() }
        } finally { src.close(); padded.close() }
    }

    /** Rotate and crop around the seven palm points before invoking the 21-point hand model. */
    private fun makeCanonicalHandInput(image: Mat, palm: DetectedPalm): Mat? {
        if (palm.right - palm.left < 8f || palm.bottom - palm.top < 8f) return null
        val firstCrop = makePaddedHandCrop(image, palm.left, palm.top, palm.right, palm.bottom, 0f, 0f, 4f, true)
        try {
            val palmPoints = palm.landmarks.map { (x, y) -> (x - firstCrop.biasX) to (y - firstCrop.biasY) }
            val wrist = palmPoints[0]
            val middleBase = palmPoints[2]
            val angle = 90.0 - Math.toDegrees(atan2(-(middleBase.second - wrist.second).toDouble(),
                (middleBase.first - wrist.first).toDouble()))
            val center = Point2f((palmPoints[0].first + palmPoints[2].first) / 2f,
                (palmPoints[0].second + palmPoints[2].second) / 2f)
            val rotation = getRotationMatrix2D(center, angle, 1.0)
            val rotated = Mat()
            try {
                warpAffine(firstCrop.image, rotated, rotation, firstCrop.image.size(), INTER_LINEAR, BORDER_CONSTANT, Scalar())
                val affine = DoubleArray(6)
                rotation.data().asBuffer().order(ByteOrder.nativeOrder()).asDoubleBuffer().get(affine)
                val aligned = palmPoints.map { (x, y) ->
                    (affine[0] * x + affine[1] * y + affine[2]).toFloat() to
                        (affine[3] * x + affine[4] * y + affine[5]).toFloat()
                }
                val bounds = HandCropBounds(aligned.minOf { it.first }, aligned.minOf { it.second },
                    aligned.maxOf { it.first }, aligned.maxOf { it.second })
                val secondCrop = makePaddedHandCrop(rotated, bounds.left, bounds.top, bounds.right, bounds.bottom, 0f, -0.4f, 3f, false)
                try {
                    val rgb = Mat(); cvtColor(secondCrop.image, rgb, COLOR_BGR2RGB)
                    val resized = Mat(); resize(rgb, resized, Size(224, 224)); rgb.close()
                    try {
                        val input = Mat(4, intArrayOf(1, 224, 224, 3), CV_32F)
                        val pixels = resized.data().asBuffer()
                        val output = input.data().asBuffer().order(ByteOrder.nativeOrder()).asFloatBuffer()
                        repeat(224 * 224 * 3) { output.put((pixels.get().toInt() and 0xff) / 255f) }
                        return input
                    } finally { resized.close() }
                } finally { secondCrop.image.close() }
            } finally { rotation.close(); rotated.close() }
        } finally { firstCrop.image.close() }
    }

    private fun makePaddedHandCrop(image: Mat, left: Float, top: Float, right: Float, bottom: Float,
        shiftX: Float, shiftY: Float, enlargement: Float, diagonal: Boolean): PaddedHandCrop {
        val width = right - left
        val height = bottom - top
        val centerX = left + width / 2 + shiftX * width
        val centerY = top + height / 2 + shiftY * height
        val halfWidth = width * enlargement / 2
        val halfHeight = height * enlargement / 2
        val x0 = (centerX - halfWidth).toInt().coerceIn(0, image.cols() - 1)
        val y0 = (centerY - halfHeight).toInt().coerceIn(0, image.rows() - 1)
        val x1 = (centerX + halfWidth).toInt().coerceIn(x0 + 1, image.cols())
        val y1 = (centerY + halfHeight).toInt().coerceIn(y0 + 1, image.rows())
        val source = Mat(image, Rect(x0, y0, x1 - x0, y1 - y0))
        try {
            val boxWidth = x1 - x0
            val boxHeight = y1 - y0
            val side = if (diagonal) sqrt((boxWidth * boxWidth + boxHeight * boxHeight).toDouble()).toInt()
                else max(boxWidth, boxHeight)
            val padLeft = (side - boxWidth) / 2
            val padTop = (side - boxHeight) / 2
            val result = Mat(side, side, CV_8UC3, Scalar())
            val destination = Mat(result, Rect(padLeft, padTop, boxWidth, boxHeight))
            try { source.copyTo(destination) } finally { destination.close() }
            return PaddedHandCrop(result, x0 - padLeft.toFloat(), y0 - padTop.toFloat())
        } finally { source.close() }
    }

    private fun classifyHand(input: Mat): Pair<Float, List<PixelBox>> {
        handNet!!.setInput(input)
        return MatVector().use { outputs ->
            handNet!!.forward(outputs)
            check(outputs.size() == 4L) { "手部关键点模型输出维度不正确" }
            val landmarks = outputs.get(0).use { it.floatValues(63) }
            val confidence = outputs.get(1).use { it.floatValues(1).first() }
            confidence to (0 until 21).map { point ->
                PixelBox(landmarks[point * 3], landmarks[point * 3 + 1], landmarks[point * 3], landmarks[point * 3 + 1])
            }
        }
    }

    private fun closeFace() { faceDetector?.close(); faceDetector = null; expressionNet?.close(); expressionNet = null; faceDetectorPath = null; expressionPath = null }
    private fun closeHands() { palmNet?.close(); palmNet = null; handNet?.close(); handNet = null }

    private data class PalmTensor(val mat: Mat, val scale: Float, val biasX: Float, val biasY: Float)
    private data class PalmAnchor(val x: Float, val y: Float)
    private data class HandCropBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)
    private data class PaddedHandCrop(val image: Mat, val biasX: Float, val biasY: Float)
    private data class DetectedPalm(val left: Float, val top: Float, val right: Float, val bottom: Float, val score: Float,
        val landmarks: List<Pair<Float, Float>>)

    companion object {
        private val PalmAnchors = lazy {
            buildList {
                for (y in 0 until 24) for (x in 0 until 24) repeat(2) { add(PalmAnchor((x + .5f) / 24, (y + .5f) / 24)) }
                for (y in 0 until 12) for (x in 0 until 12) repeat(6) { add(PalmAnchor((x + .5f) / 12, (y + .5f) / 12)) }
            }
        }.value

        private fun intersectionOverUnion(a: DetectedPalm, b: DetectedPalm): Float {
            val width = (min(a.right, b.right) - max(a.left, b.left)).coerceAtLeast(0f)
            val height = (min(a.bottom, b.bottom) - max(a.top, b.top)).coerceAtLeast(0f)
            val intersection = width * height
            val union = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - intersection
            return if (union > 0) intersection / union else 0f
        }
    }
}

private fun Mat.floatValues(count: Int): FloatArray {
    require(total() * channels() >= count) { "视觉模型输出维度错误: ${total()}×${channels()}，预期 $count" }
    val result = FloatArray(count)
    data().asBuffer().order(ByteOrder.nativeOrder()).asFloatBuffer().get(result)
    return result
}

private fun softmax(values: FloatArray): FloatArray {
    val max = values.maxOrNull() ?: return floatArrayOf()
    val exp = values.map { kotlin.math.exp(it - max) }
    val total = exp.sum().coerceAtLeast(1e-12f)
    return exp.map { it / total }.toFloatArray()
}
