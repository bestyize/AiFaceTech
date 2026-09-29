import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    jvm()
    android {
        namespace = "com.aifacemate.face.camera"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutinesCore)
            implementation(libs.compose.runtime)
            api(libs.compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        jvmMain.dependencies {
            implementation("org.bytedeco:opencv:4.13.0-1.5.13")
            cameraNativeClassifier()?.let { native ->
                runtimeOnly("org.bytedeco:opencv:4.13.0-1.5.13:$native")
                runtimeOnly("org.bytedeco:openblas:0.3.31-1.5.13:$native")
                runtimeOnly("org.bytedeco:javacpp:1.5.13:$native")
            }
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

fun cameraNativeClassifier(): String? {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val cpu = when (arch) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        else -> return null
    }
    return when {
        os.contains("windows") && cpu == "x86_64" -> "windows-$cpu"
        os.contains("linux") -> "linux-$cpu"
        os.contains("mac") -> "macosx-$cpu"
        else -> null
    }
}

val cameraJvmTest = kotlin.targets.getByName("jvm").compilations.getByName("test")
tasks.register<JavaExec>("verifyDesktopCamera") {
    group = "verification"
    description = "Explicitly capture a live frame, stop and reopen the default desktop camera without saving images"
    dependsOn("jvmTestClasses")
    mainClass.set("com.aifacemate.face.camera.VerifyDesktopCameraKt")
    classpath = files(cameraJvmTest.output.allOutputs, cameraJvmTest.runtimeDependencyFiles)
    args = listOf(providers.gradleProperty("cameraDevice").orElse("0").get())
}
