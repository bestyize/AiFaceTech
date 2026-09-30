package com.aifacemate.face.capability

actual fun currentDeviceEnvironment() = DeviceEnvironment(DevicePlatform.Ios, OperatingSystem.Ios, CpuArchitecture.Arm64)

/** iOS providers use built-in system frameworks; no model is downloaded yet. */
private object UnsupportedModelStore : ModelStore {
    override val supportsDownloads = false
    override fun installed(model: ModelSpec) = false
    override suspend fun install(models: List<ModelSpec>, onProgress: (Long, Long) -> Unit): Unit = error("iOS 暂不支持下载模型")
    override suspend fun remove(models: List<ModelSpec>) = Unit
    override suspend fun verifiedPath(model: ModelSpec): String = error("iOS 暂不支持下载模型")
}

actual fun platformModelStore(): ModelStore = UnsupportedModelStore
