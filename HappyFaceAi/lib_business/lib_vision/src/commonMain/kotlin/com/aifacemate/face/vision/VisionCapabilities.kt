package com.aifacemate.face.vision

import com.aifacemate.face.capability.CapabilityKey

/** Normalized to the upright frame: (0, 0) is the top-left corner and (1, 1) the bottom-right. */
data class NormalizedPoint(val x: Float, val y: Float)

data class NormalizedBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** The five YuNet/ArcFace points. "Right" and "left" are the subject's, so the right eye is on the image's left. */
data class FaceKeypoints(
    val rightEye: NormalizedPoint,
    val leftEye: NormalizedPoint,
    val noseTip: NormalizedPoint,
    val rightMouth: NormalizedPoint,
    val leftMouth: NormalizedPoint,
) {
    fun toList() = listOf(rightEye, leftEye, noseTip, rightMouth, leftMouth)
}

data class DetectedFace(val box: NormalizedBox, val score: Float, val keypoints: FaceKeypoints? = null)

/**
 * Stable ids shared by every device and the device protocol; UI text lives with the UI. The first seven are
 * appearance classes, the last two are geometric cues from platforms without a classifier.
 */
enum class Expression(val id: String) {
    Angry("angry"), Disgust("disgust"), Fear("fear"), Happy("happy"), Neutral("neutral"), Sad("sad"), Surprise("surprise"),
    Smiling("smiling"), MouthOpen("mouth_open"),
}

data class ExpressionResult(val expression: Expression, val confidence: Float)

enum class HandGesture(val id: String) {
    ThumbsUp("thumbs_up"), OpenPalm("open_palm"), Fist("fist"), Victory("victory"), Pointing("pointing"), Pinch("pinch"),
}

/** 21 landmarks in MediaPipe order: wrist, then thumb, index, middle, ring and little finger joints from base to tip. */
data class DetectedHand(val box: NormalizedBox, val score: Float, val landmarks: List<NormalizedPoint>)

/** [label] is the model's stable class name, e.g. COCO "person". */
data class DetectedObject(val label: String, val classIndex: Int, val box: NormalizedBox, val score: Float)

interface FaceDetector : AutoCloseable {
    suspend fun detect(frame: RgbaFrame): List<DetectedFace>
    companion object : CapabilityKey<FaceDetector>("vision.face-detector")
}

interface ExpressionClassifier : AutoCloseable {
    /** Null when this face cannot be classified, for example a face too close to the frame edge. */
    suspend fun classify(frame: RgbaFrame, face: DetectedFace): ExpressionResult?
    companion object : CapabilityKey<ExpressionClassifier>("vision.expression-classifier")
}

interface HandLandmarker : AutoCloseable {
    suspend fun detect(frame: RgbaFrame): List<DetectedHand>
    companion object : CapabilityKey<HandLandmarker>("vision.hand-landmarker")
}

interface ObjectDetector : AutoCloseable {
    suspend fun detect(frame: RgbaFrame, minConfidence: Float): List<DetectedObject>
    companion object : CapabilityKey<ObjectDetector>("vision.object-detector")
}

/** Pure logic over landmarks, so it is a strategy rather than a provider with models and a lifecycle. */
fun interface GestureClassifier {
    fun classify(hand: DetectedHand, frameWidth: Int, frameHeight: Int): HandGesture?
}

/** Static gestures from finger extension ratios. Works on any device that reports MediaPipe-order landmarks. */
object RuleBasedGestureClassifier : GestureClassifier {
    override fun classify(hand: DetectedHand, frameWidth: Int, frameHeight: Int): HandGesture? {
        val points = hand.landmarks
        if (points.size != 21 || hand.score < 0.55f) return null
        // Ratios need isotropic coordinates; normalized x and y have different scales on non-square frames.
        fun distance(first: Int, second: Int): Float {
            val dx = (points[first].x - points[second].x) * frameWidth
            val dy = (points[first].y - points[second].y) * frameHeight
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }
        val straight = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18).map { (tip, joint) ->
            distance(0, tip) > distance(0, joint) * 1.14f
        }
        val thumbStraight = distance(0, 4) > distance(0, 3) * 1.12f
        val palmWidth = distance(5, 17).coerceAtLeast(0.01f)
        // A fist tucks the thumb next to the curled index finger, which otherwise reads as a pinch.
        val fingersCurled = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18).all { (tip, joint) -> distance(0, tip) < distance(0, joint) }
        return when {
            !thumbStraight && fingersCurled -> HandGesture.Fist
            distance(4, 8) / palmWidth < 0.24f -> HandGesture.Pinch
            straight[0] && straight[1] && !straight[2] && !straight[3] -> HandGesture.Victory
            straight[0] && !straight[1] && !straight[2] && !straight[3] -> HandGesture.Pointing
            straight.all { it } -> HandGesture.OpenPalm
            thumbStraight && straight.none { it } -> HandGesture.ThumbsUp
            !thumbStraight && straight.none { it } -> HandGesture.Fist
            else -> null
        }
    }
}

internal fun NormalizedBox.clamped() = NormalizedBox(
    left.coerceIn(0f, 1f), top.coerceIn(0f, 1f), right.coerceIn(0f, 1f), bottom.coerceIn(0f, 1f),
)

internal fun List<NormalizedPoint>.bounds() = NormalizedBox(minOf { it.x }, minOf { it.y }, maxOf { it.x }, maxOf { it.y })
