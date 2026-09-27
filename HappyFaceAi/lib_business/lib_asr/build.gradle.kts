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
            implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8")
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
