package com.aifacemate.face.data

import java.util.prefs.Preferences
import java.io.File
import java.util.Properties

/**
 * 桌面用 `java.util.prefs`：Windows 上落在注册表 `HKCU\Software\JavaSoft\Prefs\com\aifacemate\face`，
 * 不需要任何初始化，所以桌面入口不用装东西。
 */
actual object SettingsStore {
    private val prefs: Preferences = Preferences.userRoot().node("com/aifacemate/face")

    actual fun get(key: String): String? = runCatching {
        val saved = prefs.get(key, null)
        saved?.takeIf { it.isNotBlank() }
            ?: (if (System.getProperty("aifacemate.devDefaults") == "true") developerDefault(key) else null)
            ?: saved
    }.getOrNull()

    /** Local overrides take precedence; explicitly opted-in desktop test builds can carry defaults. */
    private fun developerDefault(key: String): String? {
        val file = sequenceOf(File("local.properties"), File("../local.properties"))
            .firstOrNull { it.isFile }
        val properties = Properties().apply {
            SettingsStore::class.java.getResourceAsStream("/aifacemate-development.properties")?.use(::load)
            file?.inputStream()?.use(::load)
        }
        return when (key) {
            "apikey:deepseek" -> properties.getProperty("aifacemate.dev.apiKey")
            "baseUrl:deepseek" -> properties.getProperty("aifacemate.dev.baseUrl")
            "brain.provider" -> properties.getProperty("aifacemate.dev.provider")
            "brain.model:deepseek" -> properties.getProperty("aifacemate.dev.model")
            "voice.mimo.apiKey" -> properties.getProperty("aifacemate.dev.mimoApiKey")
            "voice.mimo.baseUrl" -> properties.getProperty("aifacemate.dev.mimoBaseUrl")
            else -> null
        }
    }

    actual fun put(key: String, value: String) {
        runCatching {
            prefs.put(key, value)
            prefs.flush()
        }
    }

    actual fun remove(key: String) {
        runCatching {
            prefs.remove(key)
            prefs.flush()
        }
    }
}
