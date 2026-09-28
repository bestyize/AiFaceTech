import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val localSecrets = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { input -> load(input) }
}
fun localSecret(name: String): String = localSecrets.getProperty(name, "").replace("\\", "\\\\").replace("\"", "\\\"")

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))
    implementation(project(":lib_business:lib_face"))
    implementation(project(":lib_common:lib_storage"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.aifacemate.face"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.aifacemate.face"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        ndk {
            // Upstream's static AAR still ships a shared ORT on 32-bit x86.
            // Package only architectures with an isolated static speech runtime.
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        buildConfigField("String", "DEV_DEEPSEEK_API_KEY", "\"${localSecret("aifacemate.dev.apiKey")}\"")
        buildConfigField("String", "DEV_DEEPSEEK_BASE_URL", "\"${localSecret("aifacemate.dev.baseUrl")}\"")
        buildConfigField("String", "DEV_MIMO_API_KEY", "\"${localSecret("aifacemate.dev.mimoApiKey")}\"")
        buildConfigField("String", "DEV_MIMO_BASE_URL", "\"${localSecret("aifacemate.dev.mimoBaseUrl")}\"")
    }
    packaging {
        jniLibs {
            // Native merging runs before ABI filtering; exclude the unsupported shared x86 build.
            excludes += "lib/x86/**"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
