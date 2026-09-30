package com.aifacemate.face.vision

import com.aifacemate.face.capability.ModelSpec

/** Pinned downloads. Directories keep the paths used by earlier releases, so installed models stay valid. */
object VisionModels {
    private const val yoloRevision = "c526dae7a5a5b4f1d79d704903b4406011da2716"
    private const val localAnalysis = "vision/local-analysis-opencv-zoo-2023"

    val yolo26m = ModelSpec(
        "yolo26m", "vision/yolo26m-$yoloRevision", "yolo26m.onnx", 81_963_205L,
        "fff318f5bf564af5510997bdbfd248c83dac63313fa55ff35ad4812d5340908e",
        "https://huggingface.co/prithivMLmods/YOLO26-ONNX/resolve/$yoloRevision/yolo26m/yolo26m.onnx",
    )
    val yunet = ModelSpec(
        "yunet-2023mar", localAnalysis, "face_detection_yunet_2023mar.onnx", 232_589L,
        "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4",
        "https://huggingface.co/opencv/face_detection_yunet/resolve/3cc26e7f1014a5ee5d74a42acee58bafc9d0a310/face_detection_yunet_2023mar.onnx",
    )
    val mobileFaceNetExpression = ModelSpec(
        "mobilefacenet-expression-2022july", localAnalysis, "facial_expression_recognition_mobilefacenet_2022july.onnx", 4_791_892L,
        "4f61307602fc089ce20488a31d4e4614e3c9753a7d6c41578c854858b183e1a9",
        "https://huggingface.co/opencv/facial_expression_recognition/resolve/b473270576eabddaacffd08adb4bed27b3fc54e6/facial_expression_recognition_mobilefacenet_2022july.onnx",
    )
    val palmDetection = ModelSpec(
        "mediapipe-palm-2023feb", localAnalysis, "palm_detection_mediapipe_2023feb.onnx", 3_905_734L,
        "78ff51c38496b7fc8b8ebdb6cc8c1abb02fa6c38427c6848254cdaba57fcce7c",
        "https://huggingface.co/opencv/palm_detection_mediapipe/resolve/233e619dcea1759bf6de707b9b904fe30881ea55/palm_detection_mediapipe_2023feb.onnx",
    )
    val handLandmarks = ModelSpec(
        "mediapipe-handpose-2023feb", localAnalysis, "handpose_estimation_mediapipe_2023feb.onnx", 4_099_621L,
        "db0898ae717b76b075d9bf563af315b29562e11f8df5027a1ef07b02bef6d81c",
        "https://huggingface.co/opencv/handpose_estimation_mediapipe/resolve/4b2a0b446e5cf2f11fb6b2c7251091c035d2c1f7/handpose_estimation_mediapipe_2023feb.onnx",
    )
}
