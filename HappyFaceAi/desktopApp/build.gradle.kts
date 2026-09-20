import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":lib_face"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.material3)

    implementation(libs.compose.uiToolingPreview)
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
