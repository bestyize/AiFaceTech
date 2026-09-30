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
        namespace = "com.aifacemate.face.vision"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":lib_common:lib_capability"))
            implementation(project(":lib_common:lib_storage"))
            implementation(libs.kotlinx.coroutinesCore)
        }
        val jvmSharedMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmSharedMain)
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.dependencies { implementation(libs.onnxruntime.jvm) }
        androidMain.dependencies {
            implementation(libs.onnxruntime.android)
        }

        val iosMain = maybeCreate("iosMain").apply { dependsOn(commonMain.get()) }
        iosArm64Main.get().dependsOn(iosMain)
        iosSimulatorArm64Main.get().dependsOn(iosMain)
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesCore)
        }
    }
}

// Explicit network check with real models and sample photos; ordinary tests stay offline.
val visionJvmTest = kotlin.targets.getByName("jvm").compilations.getByName("test")
tasks.register<JavaExec>("verifyVisionProviders") {
    group = "verification"
    description = "Download the pinned vision models into the build directory and check them on sample photos"
    dependsOn("jvmTestClasses")
    mainClass.set("com.aifacemate.face.vision.VerifyVisionProvidersKt")
    classpath = files(visionJvmTest.output.allOutputs, visionJvmTest.runtimeDependencyFiles)
    args = listOf(layout.buildDirectory.dir("vision-provider-check").get().asFile.absolutePath)
}
