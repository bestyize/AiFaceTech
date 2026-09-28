import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":lib_business:lib_face"))
    implementation(project(":lib_business:lib_brains"))
    implementation(project(":lib_business:lib_lifecenter"))
    implementation(project(":lib_business:lib_voice"))
    implementation(project(":lib_business:lib_vision"))
    implementation(project(":lib_common:lib_theme"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.compose.material3)

    implementation(libs.compose.uiToolingPreview)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}

/**
 * 把所有表情离屏渲染成 PNG，用于设计走查与视觉回归。
 * 用法：./gradlew :desktopApp:exportFaceSheets
 */
tasks.register<JavaExec>("exportFaceSheets") {
    group = "verification"
    description = "Render every expression to PNG for visual review"
    mainClass.set("com.aifacemate.face.tools.ExportFaceSheetsKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(layout.buildDirectory.dir("face-sheets").get().asFile.absolutePath)
}

compose.desktop {
    application {
        mainClass = "com.aifacemate.face.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.aifacemate.face"
            packageVersion = "1.0.0"
        }
    }
}

// Visual QA for the shared life center at three viewport sizes.
tasks.register<JavaExec>("exportLifeCenter") {
    group = "verification"
    mainClass.set("com.aifacemate.face.tools.ExportLifeCenterKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(layout.buildDirectory.dir("life-center").get().asFile.absolutePath)
}

// Visual QA for the AI brain page at desktop and real-device landscape sizes.
tasks.register<JavaExec>("exportAiBrain") {
    group = "verification"
    mainClass.set("com.aifacemate.face.tools.ExportAiBrainKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(layout.buildDirectory.dir("ai-brain").get().asFile.absolutePath)
}

// Offline interaction checks using the actual small-screen Compose semantics tree.
tasks.register<JavaExec>("verifyAiBrainV3") {
    group = "verification"
    mainClass.set("com.aifacemate.face.tools.VerifyAiBrainV3Kt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Offline visual and interaction checks for voice settings across display sizes.
tasks.register<JavaExec>("exportVoiceSettings") {
    group = "verification"
    description = "Render and verify responsive voice settings without saved credentials"
    mainClass.set("com.aifacemate.face.tools.ExportVoiceSettingsKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(layout.buildDirectory.dir("voice-settings").get().asFile.absolutePath)
}

// Manual network verification using raw PCM input, without the desktop UI or microphone.
tasks.register<JavaExec>("runRealtimeVoice") {
    group = "verification"
    description = "Send a 16 kHz PCM file to Doubao or Qwen and save streamed output"
    mainClass.set("com.aifacemate.face.tools.RealtimeVoiceSmokeKt")
    classpath = sourceSets["main"].runtimeClasspath
    doFirst {
        args(
            project.providers.gradleProperty("voiceProvider").orNull ?: error("Set -PvoiceProvider=doubao|qwen"),
            project.providers.gradleProperty("voiceInput").orNull ?: error("Set -PvoiceInput=/path/input.pcm"),
            project.providers.gradleProperty("voiceOutput").orNull ?: error("Set -PvoiceOutput=/path/output.pcm"),
        )
    }
}

// Manual live microphone/speaker check; credentials are read from environment variables.
tasks.register<JavaExec>("runRealtimeLiveVoice") {
    group = "verification"
    description = "Run a timed desktop microphone/speaker conversation with Doubao or Qwen"
    mainClass.set("com.aifacemate.face.tools.RealtimeLiveVoiceKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    doFirst {
        args(
            project.providers.gradleProperty("voiceProvider").orNull ?: error("Set -PvoiceProvider=doubao|qwen"),
            project.providers.gradleProperty("voiceSeconds").orNull ?: "120",
        )
    }
}

// Manual local-image inference; requires a separately exported YOLO26m ONNX model.
tasks.register<JavaExec>("runYolo26m") {
    group = "verification"
    description = "Detect objects in a local image with a YOLO26m ONNX model"
    mainClass.set("com.aifacemate.face.tools.RunYolo26mKt")
    classpath = sourceSets["main"].runtimeClasspath
    doFirst {
        args(
            providers.gradleProperty("visionModel").orNull ?: error("Set -PvisionModel=/path/yolo26m.onnx"),
            providers.gradleProperty("visionImage").orNull ?: error("Set -PvisionImage=/path/image.png"),
        )
    }
}
