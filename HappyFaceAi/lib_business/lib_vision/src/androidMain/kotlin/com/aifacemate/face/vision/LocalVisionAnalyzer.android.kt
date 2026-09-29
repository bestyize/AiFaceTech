package com.aifacemate.face.vision

import com.aifacemate.face.data.AndroidAppContext
import java.io.File

actual object LocalVisionAnalyzer {
    private val engine = OpenCvLocalVisionEngine {
        File(checkNotNull(AndroidAppContext.context()) { "Android Context 未初始化" }.noBackupFilesDir,
            "vision/local-analysis-${LocalVisionModelCatalog.modelVersion}")
    }
    actual fun supported(): Boolean = AndroidAppContext.context() != null &&
        android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }
    actual fun modelsInstalled(task: LocalVisionTask) = engine.modelsInstalled(task)
    actual fun modelsAreDownloadable() = supported()
    actual suspend fun downloadModels(task: LocalVisionTask, onProgress: (Long, Long) -> Unit) {
        check(supported()) { "本地表情和手势识别需要 Android arm64-v8a 或 x86_64" }
        engine.downloadModels(task, onProgress)
    }
    actual suspend fun removeModels(task: LocalVisionTask) = engine.removeModels(task)
    actual suspend fun analyze(frame: RgbaFrame, preferences: VisionPreferences): List<LocalVisualFinding> {
        check(supported()) { "本地视觉分析暂不支持此 Android ABI" }
        return engine.analyze(frame, preferences)
    }
    actual suspend fun unload() = engine.unload()
}
