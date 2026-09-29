package com.aifacemate.face.vision

actual object Yolo26mModel {
    actual fun supported() = false
    actual fun installed() = false
    actual suspend fun install(onProgress: (Long, Long) -> Unit): Unit = error("iOS YOLO 推理后端待接入")
    actual suspend fun remove(): Unit = error("iOS YOLO 推理后端待接入")
    actual suspend fun detect(frame: RgbaFrame, preferences: VisionPreferences): List<VisionDetection> = error("iOS YOLO 推理后端待接入")
    actual suspend fun unload() = Unit
}
