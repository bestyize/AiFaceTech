package com.aifacemate.face.data

/**
 * 极简的本地键值存储。
 *
 * 只存字符串，不认识任何业务类型——怎么组织数据是上层的事（见 [com.aifacemate.face.feature.aibrain.AiBrainSettings]）。
 * 三端各给一份实现：桌面 `java.util.prefs`、Android `SharedPreferences`、iOS `NSUserDefaults`，
 * 所以这个文件本身零平台依赖，也不需要任何权限。
 *
 * 没装实现（比如 Android 还没走到 `AndroidAppContext.install`）时读回 null、写直接丢弃，
 * 上层据此当成"这次冷启没读到配置"，不该崩。
 */
expect object SettingsStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}
