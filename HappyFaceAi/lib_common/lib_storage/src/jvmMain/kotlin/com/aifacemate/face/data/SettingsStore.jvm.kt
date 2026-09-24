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
        prefs.get(key, null)
            ?: if (System.getProperty("aifacemate.devDefaults") == "true") developerDefault(key) else null
    }.getOrNull()

    /** 本机开发配置来自被 .gitignore 忽略的 local.properties，不会进入发布包。 */
    private fun developerDefault(key: String): String? {
        val file = sequenceOf(File("local.properties"), File("../local.properties"))
            .firstOrNull { it.isFile } ?: return null
        val properties = Properties().apply { file.inputStream().use(::load) }
        return when (key) {
            "apikey:deepseek" -> properties.getProperty("aifacemate.dev.apiKey")
            "baseUrl:deepseek" -> properties.getProperty("aifacemate.dev.baseUrl")
            "brain.provider" -> properties.getProperty("aifacemate.dev.provider")
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
