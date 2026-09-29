package com.aifacemate.face.vision

object Yolo26mModelInfo {
    const val sizeBytes = 81_963_205L
    const val revision = "c526dae7a5a5b4f1d79d704903b4406011da2716"
    const val sha256 = "fff318f5bf564af5510997bdbfd248c83dac63313fa55ff35ad4812d5340908e"
    const val downloadUrl = "https://huggingface.co/prithivMLmods/YOLO26-ONNX/resolve/$revision/yolo26m/yolo26m.onnx"
}

/** Download on explicit request, then lazily create/reuse the detector on the first frame. */
expect object Yolo26mModel {
    fun supported(): Boolean
    fun installed(): Boolean
    suspend fun install(onProgress: (Long, Long) -> Unit = { _, _ -> })
    suspend fun remove()
    /** Empty yoloModelPath selects the downloaded model; a non-empty path selects a custom file. */
    suspend fun detect(frame: RgbaFrame, preferences: VisionPreferences = VisionPreferencesStore.load()): List<VisionDetection>
    /** Releases native memory without deleting the cached model. Call when a vision session ends. */
    suspend fun unload()
}
