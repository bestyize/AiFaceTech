package com.aifacemate.face.vision

import com.aifacemate.face.data.AndroidAppContext
import java.io.File

actual object Yolo26mModel {
    private val models = ManagedYolo26m(directory = {
        File(checkNotNull(AndroidAppContext.context()) { "Android Context 未初始化" }.noBackupFilesDir,
            "vision/yolo26m-${Yolo26mModelInfo.revision}")
    })
    actual fun supported() = AndroidAppContext.context() != null
    actual fun installed() = supported() && models.installed()
    actual suspend fun install(onProgress: (Long, Long) -> Unit) = models.install(onProgress)
    actual suspend fun remove() = models.remove()
    actual suspend fun detect(frame: RgbaFrame, preferences: VisionPreferences) = models.detect(frame, preferences)
    actual suspend fun unload() = models.unload()
}
