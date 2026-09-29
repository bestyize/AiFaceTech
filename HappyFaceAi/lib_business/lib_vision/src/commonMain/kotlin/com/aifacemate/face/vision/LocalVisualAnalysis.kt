package com.aifacemate.face.vision

enum class LocalVisionTask { FacialExpressions, HandGestures }

enum class FacialExpression(val label: String) {
    Angry("愤怒"), Disgust("厌恶"), Fear("恐惧"), Happy("开心"), Neutral("平静"), Sad("难过"), Surprise("惊讶"),
}

enum class HandGesture(val label: String) {
    ThumbsUp("点赞"), OpenPalm("张开手掌"), Fist("握拳"), Victory("胜利手势"), Pointing("指向"), Pinch("捏合"),
}

/** An on-device observation in original, upright camera-frame pixels. */
data class LocalVisualFinding(val task: LocalVisionTask, val label: String, val confidence: Float, val box: PixelBox)

expect object LocalVisionAnalyzer {
    fun supported(): Boolean
    fun modelsInstalled(task: LocalVisionTask): Boolean
    fun modelsAreDownloadable(): Boolean
    suspend fun downloadModels(task: LocalVisionTask, onProgress: (Long, Long) -> Unit = { _, _ -> })
    suspend fun removeModels(task: LocalVisionTask)
    /** Runs only the recognizers enabled in [preferences]; all processing stays on the device. */
    suspend fun analyze(frame: RgbaFrame, preferences: VisionPreferences = VisionPreferencesStore.load()): List<LocalVisualFinding>
    suspend fun unload()
}

/** These pinned, local models are downloaded only after the user taps Download. */
data class LocalVisionModelAsset(val filename: String, val sizeBytes: Long, val sha256: String, val url: String)

object LocalVisionModelCatalog {
    const val modelVersion = "opencv-zoo-2023"
    val faceDetection = LocalVisionModelAsset(
        "face_detection_yunet_2023mar.onnx", 232_589L,
        "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4",
        "https://huggingface.co/opencv/face_detection_yunet/resolve/3cc26e7f1014a5ee5d74a42acee58bafc9d0a310/face_detection_yunet_2023mar.onnx",
    )
    val expression = LocalVisionModelAsset(
        "facial_expression_recognition_mobilefacenet_2022july.onnx", 4_791_892L,
        "4f61307602fc089ce20488a31d4e4614e3c9753a7d6c41578c854858b183e1a9",
        "https://huggingface.co/opencv/facial_expression_recognition/resolve/b473270576eabddaacffd08adb4bed27b3fc54e6/facial_expression_recognition_mobilefacenet_2022july.onnx",
    )
    val palmDetection = LocalVisionModelAsset(
        "palm_detection_mediapipe_2023feb.onnx", 3_905_734L,
        "78ff51c38496b7fc8b8ebdb6cc8c1abb02fa6c38427c6848254cdaba57fcce7c",
        "https://huggingface.co/opencv/palm_detection_mediapipe/resolve/233e619dcea1759bf6de707b9b904fe30881ea55/palm_detection_mediapipe_2023feb.onnx",
    )
    val handLandmarks = LocalVisionModelAsset(
        "handpose_estimation_mediapipe_2023feb.onnx", 4_099_621L,
        "db0898ae717b76b075d9bf563af315b29562e11f8df5027a1ef07b02bef6d81c",
        "https://huggingface.co/opencv/handpose_estimation_mediapipe/resolve/4b2a0b446e5cf2f11fb6b2c7251091c035d2c1f7/handpose_estimation_mediapipe_2023feb.onnx",
    )

    fun assets(task: LocalVisionTask) = when (task) {
        LocalVisionTask.FacialExpressions -> listOf(faceDetection, expression)
        LocalVisionTask.HandGestures -> listOf(palmDetection, handLandmarks)
    }
}

/** Classifies the MediaPipe-order 21 hand landmarks into common stationary gestures. */
object LocalHandGestureClassifier {
    fun classify(points: List<PixelBox>, confidence: Float): String? {
        if (points.size != 21 || confidence < 0.55f) return null
        fun distance(first: Int, second: Int): Float {
            val dx = points[first].left - points[second].left
            val dy = points[first].top - points[second].top
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }
        val straight = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18).map { (tip, joint) ->
            distance(0, tip) > distance(0, joint) * 1.14f
        }
        val thumbStraight = distance(0, 4) > distance(0, 3) * 1.12f
        val palmWidth = distance(5, 17).coerceAtLeast(0.01f)
        return when {
            distance(4, 8) / palmWidth < 0.24f -> HandGesture.Pinch.label
            straight[0] && straight[1] && !straight[2] && !straight[3] -> HandGesture.Victory.label
            straight[0] && !straight[1] && !straight[2] && !straight[3] -> HandGesture.Pointing.label
            straight.all { it } -> HandGesture.OpenPalm.label
            thumbStraight && straight.none { it } -> HandGesture.ThumbsUp.label
            !thumbStraight && straight.none { it } -> HandGesture.Fist.label
            else -> null
        }
    }
}
