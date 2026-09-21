import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val releaseVersion = providers.gradleProperty("releaseVersion")
    .map { it.removePrefix("v") }
    .orElse("1.0.0")
val releaseVersionCode = providers.gradleProperty("releaseVersionCode")
    .map {
        it.toIntOrNull()
            ?: throw GradleException("releaseVersionCode must be an integer, but was '$it'.")
    }
    .orElse(1)
fun releaseSecret(name: String) = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name))
val releaseSigningKeys = listOf(
    "ANDROID_KEYSTORE_PATH",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
)
val hasCompleteReleaseSigning = releaseSigningKeys.all { !releaseSecret(it).orNull.isNullOrBlank() }
val hasAnyReleaseSigning = releaseSigningKeys.any { !releaseSecret(it).orNull.isNullOrBlank() }

if (hasAnyReleaseSigning && !hasCompleteReleaseSigning) {
    throw GradleException(
        "Android release signing requires ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, " +
            "ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD."
    )
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))
    implementation(project(":lib_face"))

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
        versionCode = releaseVersionCode.get()
        versionName = releaseVersion.get()
    }
    signingConfigs {
        if (hasCompleteReleaseSigning) {
            create("release") {
                storeFile = file(releaseSecret("ANDROID_KEYSTORE_PATH").get())
                storePassword = releaseSecret("ANDROID_KEYSTORE_PASSWORD").orNull
                keyAlias = releaseSecret("ANDROID_KEY_ALIAS").orNull
                keyPassword = releaseSecret("ANDROID_KEY_PASSWORD").orNull
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    }
}
