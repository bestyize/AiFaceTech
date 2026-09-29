package com.aifacemate.face.vision

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreGraphics.CGPoint
import platform.CoreVideo.*
import platform.ImageIO.kCGImagePropertyOrientationUp
import platform.Vision.*

/** On iOS, Apple's built-in Vision models run face/hand landmarks locally without extra model files. */
actual object LocalVisionAnalyzer {
    actual fun supported() = true
    actual fun modelsInstalled(task: LocalVisionTask) = true
    actual fun modelsAreDownloadable() = false
    actual suspend fun downloadModels(task: LocalVisionTask, onProgress: (Long, Long) -> Unit): Unit =
        error("iOS uses the built-in Vision framework; no model download is required")
    actual suspend fun removeModels(task: LocalVisionTask): Unit = Unit
    actual suspend fun unload() = Unit

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun analyze(frame: RgbaFrame, preferences: VisionPreferences): List<LocalVisualFinding> =
        withContext(Dispatchers.Default) {
            val config = preferences.normalized()
            if (!config.expressionEnabled && !config.gestureEnabled) return@withContext emptyList()
            memScoped {
                val output = alloc<CVPixelBufferRefVar>()
                val status = CVPixelBufferCreate(kCFAllocatorDefault, frame.width.toULong(), frame.height.toULong(), kCVPixelFormatType_32RGBA, null, output.ptr)
                check(status == kCVReturnSuccess) { "无法创建 Vision 输入帧" }
                val pixelBuffer = checkNotNull(output.value) { "Vision 输入帧为空" }
                try {
                    check(CVPixelBufferLockBaseAddress(pixelBuffer, 0u) == kCVReturnSuccess) { "无法读取 Vision 输入帧" }
                    try {
                        val address = checkNotNull(CVPixelBufferGetBaseAddress(pixelBuffer))?.reinterpret<ByteVar>()
                            ?: error("Vision 帧像素地址为空")
                        val rowBytes = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
                        for (row in 0 until frame.height) for (column in 0 until frame.width) {
                            val src = (row * frame.width + column) * 4
                            val dst = row * rowBytes + column * 4
                            for (channel in 0 until 4) address[dst + channel] = frame.pixels[src + channel]
                        }
                    } finally { CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u) }

                    val results = ArrayList<LocalVisualFinding>()
                    if (config.expressionEnabled) {
                        val request = VNDetectFaceLandmarksRequest()
                        val handler = VNImageRequestHandler(pixelBuffer, kCGImagePropertyOrientationUp, emptyMap<Any?, Any>())
                        check(handler.performRequests(listOf(request), null)) { "Vision 人脸关键点识别失败" }
                        @Suppress("UNCHECKED_CAST")
                        (request.results as? List<VNFaceObservation>).orEmpty().forEach { face ->
                            val landmarks = face.landmarks ?: return@forEach
                            val outer = landmarks.outerLips ?: return@forEach
                            val lips = outer.normalizedPoints() ?: return@forEach
                            val count = outer.pointCount.toInt()
                            if (count < 4) return@forEach
                            val faceRect = face.boundingBox
                            val box = faceRect.useContents { floatArrayOf(origin.x.toFloat(), origin.y.toFloat(), size.width.toFloat(), size.height.toFloat()) }
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
                            val label = when {
                                opening > 0.45f -> "张嘴线索"
                                cornerY - middleY > 0.025f -> "微笑线索"
                                else -> "平静线索"
                            }
                            val top = ((1f - (box[1] + box[3])) * frame.height).coerceIn(0f, frame.height.toFloat())
                            val bottom = ((1f - box[1]) * frame.height).coerceIn(top, frame.height.toFloat())
                            val left = (box[0] * frame.width).coerceIn(0f, frame.width.toFloat())
                            val right = ((box[0] + box[2]) * frame.width).coerceIn(left, frame.width.toFloat())
                            val margin = when (label) {
                                "张嘴线索" -> opening - .45f
                                "微笑线索" -> cornerY - middleY - .025f
                                else -> .1f
                            }.coerceAtLeast(0f)
                            val confidence = (face.confidence * (0.65f + margin)).coerceIn(.55f, .95f)
                            if (confidence >= config.expressionConfidenceThreshold) {
                                results += LocalVisualFinding(LocalVisionTask.FacialExpressions, label, confidence, PixelBox(left, top, right, bottom))
                            }
                        }
                    }
                    if (config.gestureEnabled) {
                        val request = VNDetectHumanHandPoseRequest()
                        request.maximumHandCount = 2u
                        val handler = VNImageRequestHandler(pixelBuffer, kCGImagePropertyOrientationUp, emptyMap<Any?, Any>())
                        check(handler.performRequests(listOf(request), null)) { "Vision 手部关键点识别失败" }
                        @Suppress("UNCHECKED_CAST")
                        (request.results as? List<VNHumanHandPoseObservation>).orEmpty().forEach { hand ->
                            val names = handJointNames()
                            val pointResults = names.map { name ->
                                hand.recognizedPointForJointName(name, null)?.let { point ->
                                    val location = point.location.useContents { x.toFloat() to y.toFloat() }
                                    VisionHandPoint(PixelBox(location.first * frame.width, (1f - location.second) * frame.height,
                                        location.first * frame.width, (1f - location.second) * frame.height), point.confidence)
                                }
                            }
                            if (pointResults.any { it == null }) return@forEach
                            val aligned = pointResults.mapNotNull { it?.location }
                            val confidence = pointResults.minOf { it!!.confidence }
                            if (confidence < config.gestureConfidenceThreshold) return@forEach
                            val label = LocalHandGestureClassifier.classify(aligned, confidence) ?: return@forEach
                            results += LocalVisualFinding(LocalVisionTask.HandGestures, label, confidence, PixelBox(
                                aligned.minOf { it.left }, aligned.minOf { it.top }, aligned.maxOf { it.left }, aligned.maxOf { it.top },
                            ))
                        }
                    }
                    results
                } finally { CVBufferRelease(pixelBuffer) }
            }
        }
}

@OptIn(ExperimentalForeignApi::class)
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

private data class VisionHandPoint(val location: PixelBox, val confidence: Float)
