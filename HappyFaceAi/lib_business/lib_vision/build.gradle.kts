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
            implementation(project(":lib_common:lib_storage"))
            implementation(libs.kotlinx.coroutinesCore)
        }
        val jvmSharedMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmSharedMain)
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.dependencies { implementation(libs.onnxruntime.jvm) }
        androidMain.dependencies { implementation(libs.onnxruntime.android) }

        val iosMain = maybeCreate("iosMain").apply { dependsOn(commonMain.get()) }
        iosArm64Main.get().dependsOn(iosMain)
        iosSimulatorArm64Main.get().dependsOn(iosMain)
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

// Explicit network/runtime check; ordinary tests remain offline and use temporary fixtures.
val yoloJvmTest = kotlin.targets.getByName("jvm").compilations.getByName("test")
tasks.register<JavaExec>("verifyManagedYolo26m") {
    group = "verification"
    description = "Download the pinned YOLO26m export and exercise lazy ONNX inference in an isolated directory"
    dependsOn("jvmTestClasses")
    mainClass.set("com.aifacemate.face.vision.VerifyManagedYolo26mKt")
    classpath = files(yoloJvmTest.output.allOutputs, yoloJvmTest.runtimeDependencyFiles)
    args = listOf(layout.buildDirectory.dir("managed-yolo-check").get().asFile.absolutePath)
}
