package com.aifacemate.face.data

import java.util.prefs.Preferences

/**
 * 桌面用 `java.util.prefs`：Windows 上落在注册表 `HKCU\Software\JavaSoft\Prefs\com\aifacemate\face`，
 * 不需要任何初始化，所以桌面入口不用装东西。
 */
actual object SettingsStore {
    private val prefs: Preferences = Preferences.userRoot().node("com/aifacemate/face")

    actual fun get(key: String): String? = runCatching { prefs.get(key, null) }.getOrNull()

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
