@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aifacemate.face.vision

import com.aifacemate.face.capability.Availability
import com.aifacemate.face.capability.CapabilityKey
import com.aifacemate.face.capability.DeviceEnvironment
import com.aifacemate.face.capability.ModelSpec
import com.aifacemate.face.capability.ModelStore
import com.aifacemate.face.capability.Provider
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreGraphics.CGRect
import platform.CoreVideo.*
import platform.ImageIO.kCGImagePropertyOrientationUp
import platform.Vision.*

/** Apple Vision runs on the device with system models, so nothing is downloaded. There is no object detector yet. */
internal actual fun platformVisionProviders(store: ModelStore): List<Provider<*>> = listOf(
    AppleVisionProvider("vision.face.apple-vision", FaceDetector, "系统 Vision", "人脸检测") { AppleFaceDetector() },
    AppleVisionProvider("vision.expression.apple-vision-cues", ExpressionClassifier, "系统 Vision", "微笑、张嘴等动作线索") { AppleExpressionCues() },
    AppleVisionProvider("vision.hands.apple-vision", HandLandmarker, "系统 Vision", "手部 21 点关键点") { AppleHandLandmarker() },
)

private class AppleVisionProvider<C : AutoCloseable>(
    override val id: String,
    override val capability: CapabilityKey<C>,
    override val displayName: String,
    override val description: String,
    private val create: () -> C,
) : Provider<C> {
    override val models = emptyList<ModelSpec>()
    override fun availability(environment: DeviceEnvironment) = Availability.Ready
    override suspend fun open(): C = create()
}

private class AppleFaceDetector : FaceDetector {
    override suspend fun detect(frame: RgbaFrame): List<DetectedFace> = withContext(Dispatchers.Default) {
        frame.withPixelBuffer { buffer ->
            val request = VNDetectFaceRectanglesRequest()
            perform(buffer, request, "Vision 人脸检测失败")
            @Suppress("UNCHECKED_CAST")
            (request.results as? List<VNFaceObservation>).orEmpty().map { face ->
                DetectedFace(face.boundingBox.toNormalizedBox(), face.confidence)
            }
        }
    }

    override fun close() = Unit
}

/** Geometric cues from the lip landmarks; Apple Vision has no expression classifier. */
private class AppleExpressionCues : ExpressionClassifier {
    override suspend fun classify(frame: RgbaFrame, face: DetectedFace): ExpressionResult? = withContext(Dispatchers.Default) {
        frame.withPixelBuffer { buffer ->
            val request = VNDetectFaceLandmarksRequest()
            perform(buffer, request, "Vision 人脸关键点识别失败")
            @Suppress("UNCHECKED_CAST")
            val observation = (request.results as? List<VNFaceObservation>).orEmpty()
                .map { it to intersectionOverUnion(it.boundingBox.toNormalizedBox(), face.box) }
                .filter { it.second > 0.3f }
                .maxByOrNull { it.second }?.first ?: return@withPixelBuffer null
            cue(observation)
        }
    }

    private fun cue(face: VNFaceObservation): ExpressionResult? {
        val landmarks = face.landmarks ?: return null
        val outer = landmarks.outerLips ?: return null
        val lips = outer.normalizedPoints() ?: return null
        val count = outer.pointCount.toInt()
        if (count < 4) return null
        val mouth = (0 until count).map { index -> lips[index].let { it.x.toFloat() to it.y.toFloat() } }
        val middleY = mouth.map { it.second }.average().toFloat()
        val cornerY = (mouth.first().second + mouth.last().second) / 2f
        val inner = landmarks.innerLips
        val innerPoints = if (inner != null && inner.pointCount > 1uL) {
            val pointer = inner.normalizedPoints()
            if (pointer == null) emptyList() else (0 until inner.pointCount.toInt()).map { index ->
                pointer[index].let { it.x.toFloat() to it.y.toFloat() }
            }
        } else emptyList()
        val mouthWidth = mouth.maxOf { it.first } - mouth.minOf { it.first }
        val opening = if (innerPoints.isNotEmpty() && mouthWidth > 0f)
            (innerPoints.maxOf { it.second } - innerPoints.minOf { it.second }) / mouthWidth else 0f
        val (expression, margin) = when {
            opening > 0.45f -> Expression.MouthOpen to opening - .45f
            cornerY - middleY > 0.025f -> Expression.Smiling to cornerY - middleY - .025f
            else -> Expression.Neutral to .1f
        }
        return ExpressionResult(expression, (face.confidence * (0.65f + margin.coerceAtLeast(0f))).coerceIn(.55f, .95f))
    }

    override fun close() = Unit
}

private class AppleHandLandmarker : HandLandmarker {
    override suspend fun detect(frame: RgbaFrame): List<DetectedHand> = withContext(Dispatchers.Default) {
        frame.withPixelBuffer { buffer ->
            val request = VNDetectHumanHandPoseRequest()
            request.maximumHandCount = 2u
            perform(buffer, request, "Vision 手部关键点识别失败")
            @Suppress("UNCHECKED_CAST")
            (request.results as? List<VNHumanHandPoseObservation>).orEmpty().mapNotNull { hand ->
                val joints = handJointNames().map { name ->
                    hand.recognizedPointForJointName(name, null)?.let { point ->
                        // Vision's origin is the bottom-left corner.
                        val location = point.location.useContents { NormalizedPoint(x.toFloat(), 1f - y.toFloat()) }
                        location to point.confidence
                    } ?: return@mapNotNull null
                }
                val points = joints.map { it.first }
                DetectedHand(points.bounds().clamped(), joints.minOf { it.second }, points)
            }
        }
    }

    override fun close() = Unit
}

private fun perform(buffer: CVPixelBufferRef, request: VNRequest, failure: String) {
    val handler = VNImageRequestHandler(buffer, kCGImagePropertyOrientationUp, emptyMap<Any?, Any>())
    check(handler.performRequests(listOf(request), null)) { failure }
}

private fun CValue<CGRect>.toNormalizedBox(): NormalizedBox = useContents {
    NormalizedBox(origin.x.toFloat(), (1 - origin.y - size.height).toFloat(),
        (origin.x + size.width).toFloat(), (1 - origin.y).toFloat()).clamped()
}

private fun intersectionOverUnion(a: NormalizedBox, b: NormalizedBox) =
    intersectionOverUnion(floatArrayOf(a.left, a.top, a.right, a.bottom), floatArrayOf(b.left, b.top, b.right, b.bottom))

private inline fun <T> RgbaFrame.withPixelBuffer(block: (CVPixelBufferRef) -> T): T = memScoped {
    val output = alloc<CVPixelBufferRefVar>()
    val status = CVPixelBufferCreate(kCFAllocatorDefault, width.toULong(), height.toULong(), kCVPixelFormatType_32RGBA, null, output.ptr)
    check(status == kCVReturnSuccess) { "无法创建 Vision 输入帧" }
    val pixelBuffer = checkNotNull(output.value) { "Vision 输入帧为空" }
    try {
        check(CVPixelBufferLockBaseAddress(pixelBuffer, 0u) == kCVReturnSuccess) { "无法读取 Vision 输入帧" }
        try {
            val address = CVPixelBufferGetBaseAddress(pixelBuffer)?.reinterpret<ByteVar>() ?: error("Vision 帧像素地址为空")
            val rowBytes = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
            for (row in 0 until height) for (column in 0 until width) {
                val src = (row * width + column) * 4
                val dst = row * rowBytes + column * 4
                for (channel in 0 until 4) address[dst + channel] = pixels[src + channel]
            }
        } finally { CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u) }
        block(pixelBuffer)
    } finally { CVBufferRelease(pixelBuffer) }
}

private fun handJointNames(): List<String> = listOf(
    VNHumanHandPoseObservationJointNameWrist!!,
    VNHumanHandPoseObservationJointNameThumbCMC!!, VNHumanHandPoseObservationJointNameThumbMP!!,
    VNHumanHandPoseObservationJointNameThumbIP!!, VNHumanHandPoseObservationJointNameThumbTip!!,
    VNHumanHandPoseObservationJointNameIndexMCP!!, VNHumanHandPoseObservationJointNameIndexPIP!!,
    VNHumanHandPoseObservationJointNameIndexDIP!!, VNHumanHandPoseObservationJointNameIndexTip!!,
    VNHumanHandPoseObservationJointNameMiddleMCP!!, VNHumanHandPoseObservationJointNameMiddlePIP!!,
    VNHumanHandPoseObservationJointNameMiddleDIP!!, VNHumanHandPoseObservationJointNameMiddleTip!!,
    VNHumanHandPoseObservationJointNameRingMCP!!, VNHumanHandPoseObservationJointNameRingPIP!!,
    VNHumanHandPoseObservationJointNameRingDIP!!, VNHumanHandPoseObservationJointNameRingTip!!,
    VNHumanHandPoseObservationJointNameLittleMCP!!, VNHumanHandPoseObservationJointNameLittlePIP!!,
    VNHumanHandPoseObservationJointNameLittleDIP!!, VNHumanHandPoseObservationJointNameLittleTip!!,
)
