import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

abstract class GenerateDevelopmentDefaults : DefaultTask() {
    @get:Input abstract val includeCredentials: Property<Boolean>
    @get:InputFile @get:Optional abstract val localConfig: RegularFileProperty
    @get:OutputFile abstract val outputConfig: RegularFileProperty

    @TaskAction
    fun generate() {
        val output = outputConfig.get().asFile
        output.parentFile.mkdirs()
        val defaults = Properties()
        if (includeCredentials.get()) {
            val source = localConfig.orNull?.asFile
            require(source?.isFile == true) { "Set local.properties before building with includeDevApiKeys=true" }
            val local = Properties().apply { source!!.inputStream().use(::load) }
            listOf("provider", "apiKey", "baseUrl", "model", "mimoApiKey", "mimoBaseUrl").forEach { field ->
                local.getProperty("aifacemate.dev.$field")?.let { defaults.setProperty("aifacemate.dev.$field", it) }
            }
        }
        // Always rewrite the resource so a subsequent standard build cannot reuse embedded keys.
        output.outputStream().use { defaults.store(it, "Explicitly opted-in development defaults") }
    }
}

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val generateDevelopmentDefaults = tasks.register<GenerateDevelopmentDefaults>("generateDevelopmentDefaults") {
    includeCredentials.set(providers.gradleProperty("includeDevApiKeys").map(String::toBoolean).orElse(false))
    val localFile = rootProject.layout.projectDirectory.file("local.properties")
    if (localFile.asFile.isFile) localConfig.set(localFile)
    outputConfig.set(layout.buildDirectory.file("generated/developmentResources/aifacemate-development.properties"))
}
sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated/developmentResources"))
tasks.named("processResources") { dependsOn(generateDevelopmentDefaults) }

dependencies {
    implementation(project(":shared"))
    implementation(project(":lib_business:lib_chat"))
    implementation(project(":lib_business:lib_face"))
    implementation(project(":lib_business:lib_brains"))
    implementation(project(":lib_business:lib_lifecenter"))
    implementation(project(":lib_business:lib_voice"))
    implementation(project(":lib_business:lib_vision"))
    implementation(project(":lib_business:lib_camera"))
    implementation(project(":lib_common:lib_theme"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.androidx.lifecycle.runtimeCompose)
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
            if (providers.gradleProperty("includeDevApiKeys").orNull == "true") {
                outputBaseDir.set(project.layout.buildDirectory.dir("compose/development"))
            }
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.aifacemate.face"
            packageVersion = "1.0.0"
            macOS {
                infoPlist {
                    extraKeysRawXml = "<key>NSCameraUsageDescription</key><string>需要摄像头以便让小星看到周围环境并进行本地视觉识别。</string>"
                }
                entitlementsFile.set(project.layout.projectDirectory.file("camera-entitlements.plist"))
            }
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

// Offline visual and interaction checks for vision settings.
tasks.register<JavaExec>("exportVisionSettings") {
    group = "verification"
    description = "Render and exercise vision configuration with in-memory settings"
    mainClass.set("com.aifacemate.face.tools.ExportVisionSettingsKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(layout.buildDirectory.dir("vision-settings").get().asFile.absolutePath)
}

tasks.register<JavaExec>("exportCamera") {
    group = "verification"
    description = "Render and exercise responsive camera controls with synthetic frames and no hardware access"
    mainClass.set("com.aifacemate.face.tools.ExportCameraKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(layout.buildDirectory.dir("camera").get().asFile.absolutePath)
}

tasks.register<JavaExec>("verifyCameraVision") {
    group = "verification"
    description = "Explicitly run three live desktop camera frames through a provided local YOLO model"
    mainClass.set("com.aifacemate.face.tools.RunCameraVisionKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(providers.gradleProperty("visionModel").orElse("").get(), providers.gradleProperty("cameraDevice").orElse("0").get())
}

// Offline full-screen conversation visual and interaction checks.
tasks.register<JavaExec>("exportConversation") {
    group = "verification"
    description = "Render and exercise full-screen conversation using offline fixtures"
    mainClass.set("com.aifacemate.face.tools.ExportConversationKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(layout.buildDirectory.dir("conversation").get().asFile.absolutePath)
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
