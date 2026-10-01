package com.aifacemate.face.capability

import android.os.Build
import com.aifacemate.face.data.AndroidAppContext

actual fun currentDeviceEnvironment() = DeviceEnvironment(
    DevicePlatform.Android, OperatingSystem.Android,
    cpuArchitecture(Build.SUPPORTED_ABIS.firstOrNull().orEmpty()),
    Runtime.getRuntime().availableProcessors(),
)

private val androidModelStore by lazy {
    FileModelStore({ checkNotNull(AndroidAppContext.context()) { "Android Context 未初始化" }.noBackupFilesDir })
}

actual fun platformModelStore(): ModelStore = androidModelStore
