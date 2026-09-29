rootProject.name = "HappyFaceAi"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
        ivy {
            name = "SherpaOnnxOfficialReleases"
            url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download")
            patternLayout { artifact("v[revision]/[artifact]-[revision].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.k2-fsa.sherpa-onnx", "sherpa-onnx-static-link-onnxruntime") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":shared")
include(":lib_common:lib_theme")
include(":lib_common:lib_resource")
include(":lib_common:lib_storage")
include(":lib_common:lib_ui")
include(":lib_common:lib_network")
include(":lib_business:lib_face")
include(":lib_business:lib_voice")
include(":lib_business:lib_vision")
include(":lib_business:lib_camera")
include(":lib_business:lib_realtime")
include(":lib_business:lib_chat")
include(":lib_business:lib_tts")
include(":lib_business:lib_asr")
include(":lib_business:lib_models")
include(":lib_business:lib_brains")
include(":lib_business:lib_lifecenter")
