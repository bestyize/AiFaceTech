package com.aifacemate.face.capability

enum class DevicePlatform { Android, Desktop, Ios }
enum class OperatingSystem { Android, Ios, Windows, MacOs, Linux, Other }
enum class CpuArchitecture { Arm64, Arm32, X86_64, X86, Other }

/** What providers check to decide whether they can run here. Desktop covers any JVM host, e.g. RK3566 on Ubuntu. */
data class DeviceEnvironment(
    val platform: DevicePlatform,
    val operatingSystem: OperatingSystem,
    val architecture: CpuArchitecture,
)

expect fun currentDeviceEnvironment(): DeviceEnvironment

internal fun cpuArchitecture(name: String): CpuArchitecture = when (name.lowercase()) {
    "arm64-v8a", "aarch64", "arm64" -> CpuArchitecture.Arm64
    "armeabi-v7a", "armeabi", "arm", "armv7", "armv7l" -> CpuArchitecture.Arm32
    "x86_64", "amd64" -> CpuArchitecture.X86_64
    "x86", "i386", "i686" -> CpuArchitecture.X86
    else -> CpuArchitecture.Other
}
