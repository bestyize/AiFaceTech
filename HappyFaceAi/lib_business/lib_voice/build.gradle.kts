import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    jvm()

    android {
        namespace = "com.aifacemate.face.voice"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }

    sourceSets {
        val jvmSharedMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmSharedMain)
        jvmSharedMain.dependencies { implementation(libs.kotlinx.coroutinesCore) }
        androidMain.dependencies { implementation(libs.kotlinx.coroutinesCore) }
        val iosMain = maybeCreate("iosMain").apply { dependsOn(commonMain.get()) }
        iosArm64Main.get().dependsOn(iosMain)
        iosSimulatorArm64Main.get().dependsOn(iosMain)
        iosMain.dependencies { implementation(libs.kotlinx.coroutinesCore) }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(project(":lib_common:lib_storage"))
            api(project(":lib_business:lib_tts"))
            api(project(":lib_business:lib_asr"))
            api(project(":lib_business:lib_realtime"))
        }
    }
}
