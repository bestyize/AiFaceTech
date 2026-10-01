package com.aifacemate.face.vision

import com.aifacemate.face.capability.ModelSpec

/** Pinned downloads. Directories keep the paths used by earlier releases, so installed models stay valid. */
object VisionModels {
    private const val yoloRevision = "c526dae7a5a5b4f1d79d704903b4406011da2716"
    private const val localAnalysis = "vision/local-analysis-opencv-zoo-2023"

    /** Hugging Face file, with hf-mirror.com as a fallback where huggingface.co is unreachable. */
    private fun huggingFace(id: String, directory: String, fileName: String, size: Long, sha256: String, path: String) =
        ModelSpec(id, directory, fileName, size, sha256, "https://huggingface.co/$path", listOf("https://hf-mirror.com/$path"))

    private fun yolo26(variant: String, size: Long, sha256: String) = huggingFace(
        "yolo26$variant", "vision/yolo26$variant-$yoloRevision", "yolo26$variant.onnx", size, sha256,
        "prithivMLmods/YOLO26-ONNX/resolve/$yoloRevision/yolo26$variant/yolo26$variant.onnx",
    )

    val yolo26n = yolo26("n", 9_942_005L, "00e2d1062178fca312fee35cf5fc9b9c783b3e5c9675d48b84929f69ee391807")
    val yolo26s = yolo26("s", 38_291_178L, "6a48834ea650c0fdc525a7b0be9d991d7d03596a56592b17ca270b216c9f84bc")
    val yolo26m = yolo26("m", 81_963_205L, "fff318f5bf564af5510997bdbfd248c83dac63313fa55ff35ad4812d5340908e")

    val yunet = huggingFace(
        "yunet-2023mar", localAnalysis, "face_detection_yunet_2023mar.onnx", 232_589L,
        "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4",
        "opencv/face_detection_yunet/resolve/3cc26e7f1014a5ee5d74a42acee58bafc9d0a310/face_detection_yunet_2023mar.onnx",
    )
    val mobileFaceNetExpression = huggingFace(
        "mobilefacenet-expression-2022july", localAnalysis, "facial_expression_recognition_mobilefacenet_2022july.onnx", 4_791_892L,
        "4f61307602fc089ce20488a31d4e4614e3c9753a7d6c41578c854858b183e1a9",
        "opencv/facial_expression_recognition/resolve/b473270576eabddaacffd08adb4bed27b3fc54e6/facial_expression_recognition_mobilefacenet_2022july.onnx",
    )
    val palmDetection = huggingFace(
        "mediapipe-palm-2023feb", localAnalysis, "palm_detection_mediapipe_2023feb.onnx", 3_905_734L,
        "78ff51c38496b7fc8b8ebdb6cc8c1abb02fa6c38427c6848254cdaba57fcce7c",
        "opencv/palm_detection_mediapipe/resolve/233e619dcea1759bf6de707b9b904fe30881ea55/palm_detection_mediapipe_2023feb.onnx",
    )
    val handLandmarks = huggingFace(
        "mediapipe-handpose-2023feb", localAnalysis, "handpose_estimation_mediapipe_2023feb.onnx", 4_099_621L,
        "db0898ae717b76b075d9bf563af315b29562e11f8df5027a1ef07b02bef6d81c",
        "opencv/handpose_estimation_mediapipe/resolve/4b2a0b446e5cf2f11fb6b2c7251091c035d2c1f7/handpose_estimation_mediapipe_2023feb.onnx",
    )
}
