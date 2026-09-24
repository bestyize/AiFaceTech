import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    jvm()

    android {
        namespace = "com.aifacemate.face.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":lib_business:lib_face"))
            implementation(project(":lib_business:lib_brains"))
            implementation(project(":lib_business:lib_lifecenter"))
            implementation(project(":lib_business:lib_chat"))
            implementation(project(":lib_business:lib_voice"))
            implementation(project(":lib_common:lib_network"))
            implementation(project(":lib_common:lib_storage"))
            implementation(project(":lib_common:lib_theme"))
            implementation(project(":lib_common:lib_ui"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
