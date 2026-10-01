package com.aifacemate.face.capability

import java.io.File

actual fun currentDeviceEnvironment(): DeviceEnvironment {
    val os = System.getProperty("os.name").lowercase()
    return DeviceEnvironment(
        DevicePlatform.Desktop,
        when {
            os.contains("windows") -> OperatingSystem.Windows
            os.contains("mac") -> OperatingSystem.MacOs
            os.contains("linux") -> OperatingSystem.Linux
            else -> OperatingSystem.Other
        },
        cpuArchitecture(System.getProperty("os.arch")),
        Runtime.getRuntime().availableProcessors(),
    )
}

private val desktopModelStore by lazy { FileModelStore({ File(System.getProperty("user.home"), ".aifacemate/models") }) }

actual fun platformModelStore(): ModelStore = desktopModelStore
