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
