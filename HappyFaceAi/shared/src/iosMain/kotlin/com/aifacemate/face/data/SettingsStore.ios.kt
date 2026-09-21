package com.aifacemate.face.data

import platform.Foundation.NSUserDefaults

/** iOS 用 NSUserDefaults，同样不需要额外初始化。 */
actual object SettingsStore {
    private val defaults: NSUserDefaults get() = NSUserDefaults.standardUserDefaults

    actual fun get(key: String): String? = defaults.stringForKey(key)

    actual fun put(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    actual fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}
