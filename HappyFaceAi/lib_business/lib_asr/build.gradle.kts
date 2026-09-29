import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}
kotlin {
    iosArm64(); iosSimulatorArm64(); jvm()
    android {
        namespace = "com.aifacemate.face.voice.asr"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    sourceSets {
        commonMain.dependencies { implementation(project(":lib_common:lib_network")) }
        androidMain.dependencies {
            implementation(project(":lib_common:lib_storage"))
            implementation(libs.kotlinx.coroutinesCore)
            // Private, statically linked ORT avoids ABI clashes with the vision module's shared ORT.
            implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx-static-link-onnxruntime:1.13.8@aar")
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx-jvm:1.13.8")
            sherpaDesktopNative()?.let { implementation("com.github.k2-fsa.sherpa-onnx:$it:1.13.8") }
            compileOnly(libs.onnxruntime.jvm)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}


fun sherpaDesktopNative(): String? {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("windows") && (arch == "amd64" || arch == "x86_64") -> "sherpa-onnx-native-lib-win-x64"
        os.contains("windows") && (arch == "aarch64" || arch == "arm64") -> "sherpa-onnx-native-lib-win-arm64"
        os.contains("linux") && (arch == "amd64" || arch == "x86_64") -> "sherpa-onnx-native-lib-linux-x64"
        os.contains("linux") && (arch == "aarch64" || arch == "arm64") -> "sherpa-onnx-native-lib-linux-aarch64"
        os.contains("mac") && (arch == "amd64" || arch == "x86_64") -> "sherpa-onnx-native-lib-osx-x64"
        os.contains("mac") && (arch == "aarch64" || arch == "arm64") -> "sherpa-onnx-native-lib-osx-aarch64"
        else -> null
    }
}
