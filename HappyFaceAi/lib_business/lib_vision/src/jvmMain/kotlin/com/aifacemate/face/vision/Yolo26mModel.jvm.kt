package com.aifacemate.face.vision

import java.io.File

actual object Yolo26mModel {
    private val models = ManagedYolo26m(directory = {
        File(System.getProperty("user.home"), ".aifacemate/models/vision/yolo26m-${Yolo26mModelInfo.revision}")
    })
    actual fun supported(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        return (os.contains("windows") && arch in setOf("amd64", "x86_64")) ||
            ((os.contains("linux") || os.contains("mac")) && arch in setOf("amd64", "x86_64", "aarch64", "arm64"))
    }
    actual fun installed() = supported() && models.installed()
    actual suspend fun install(onProgress: (Long, Long) -> Unit) {
        check(supported()) { "当前桌面平台暂不支持 YOLO 本地推理" }
        models.install(onProgress)
    }
    actual suspend fun remove() = models.remove()
    actual suspend fun detect(frame: RgbaFrame, preferences: VisionPreferences): List<VisionDetection> {
        check(supported()) { "当前桌面平台暂不支持 YOLO 本地推理" }
        return models.detect(frame, preferences)
    }
    actual suspend fun unload() = models.unload()
}
