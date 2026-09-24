package com.aifacemate.face.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Android 上要 Context 才能拿到 SharedPreferences，由入口 Activity 在 `onCreate` 里注入一次。
 *
 * 存应用私有的 SharedPreferences，不申请任何权限，卸载时随之清除。
 */
object AndroidAppContext {
    private var application: Context? = null
    internal var defaults: Map<String, String> = emptyMap()

    fun install(context: Context, developmentDefaults: Map<String, String> = emptyMap()) {
        application = context.applicationContext
        defaults = developmentDefaults
    }

    internal fun preferences(): SharedPreferences? =
        application?.getSharedPreferences("aifacemate", Context.MODE_PRIVATE)
}

actual object SettingsStore {
    actual fun get(key: String): String? =
        runCatching { AndroidAppContext.preferences()?.getString(key, null) ?: AndroidAppContext.defaults[key] }.getOrNull()

    actual fun put(key: String, value: String) {
        runCatching { AndroidAppContext.preferences()?.edit()?.putString(key, value)?.apply() }
    }

    actual fun remove(key: String) {
        runCatching { AndroidAppContext.preferences()?.edit()?.remove(key)?.apply() }
    }
}
