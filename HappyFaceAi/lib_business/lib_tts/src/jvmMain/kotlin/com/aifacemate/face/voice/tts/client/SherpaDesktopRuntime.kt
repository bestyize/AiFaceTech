package com.aifacemate.face.voice.tts.client

/** Official sherpa-onnx JVM binaries currently package the matching ORT 1.28 line. */
internal object SherpaDesktopRuntime {
    private val os = System.getProperty("os.name").lowercase()
    private val arch = System.getProperty("os.arch").lowercase()
    private val is64BitArm = arch == "aarch64" || arch == "arm64"
    private val isX64 = arch == "amd64" || arch == "x86_64"
    val supported: Boolean = is64BitArm || isX64
    val unsupportedMessage = "当前 sherpa-onnx 桌面版暂不支持 $os/$arch"
}
