package com.aifacemate.face.vision

import java.io.File

actual object LocalVisionAnalyzer {
    private val engine = OpenCvLocalVisionEngine {
        File(System.getProperty("user.home"), ".aifacemate/models/vision/local-analysis-${LocalVisionModelCatalog.modelVersion}")
    }
    actual fun supported(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        return (os.contains("windows") && arch in setOf("amd64", "x86_64")) ||
            ((os.contains("linux") || os.contains("mac")) && arch in setOf("amd64", "x86_64", "aarch64", "arm64"))
    }
    actual fun modelsInstalled(task: LocalVisionTask) = supported() && engine.modelsInstalled(task)
    actual fun modelsAreDownloadable() = supported()
    actual suspend fun downloadModels(task: LocalVisionTask, onProgress: (Long, Long) -> Unit) {
        check(supported()) { "此桌面平台暂不支持本地视觉分析" }
        engine.downloadModels(task, onProgress)
    }
    actual suspend fun removeModels(task: LocalVisionTask) = engine.removeModels(task)
    actual suspend fun analyze(frame: RgbaFrame, preferences: VisionPreferences) = engine.analyze(frame, preferences)
    actual suspend fun unload() = engine.unload()
}
