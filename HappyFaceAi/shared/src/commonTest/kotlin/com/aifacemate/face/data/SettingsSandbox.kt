package com.aifacemate.face.data

/**
 * 借用几个设置项，用完**原样还回去**。
 *
 * JVM 上的 [SettingsStore] 是真的 `java.util.prefs`，和桌面端跑起来的 App 共用同一个节点
 * （`com/aifacemate/face`）。测试里直接写、写完 remove，删的就是**用户自己**填的那份配置——
 * 一次 `./gradlew test` 能把人填好的 API Key 抹掉，而且不会有任何提示。
 *
 * 所以进出各做一次：进来时先记下原值再清空（用例从干净状态开始，也不会读到上一个用例的残留），
 * 出去时把原值一个个放回，原本没有的键才真删掉。
 */
internal class SettingsSandbox(private val keys: List<String>) {

    private var saved: Map<String, String?> = emptyMap()

    fun enter() {
        saved = keys.associateWith { SettingsStore.get(it) }
        keys.forEach { SettingsStore.remove(it) }
    }

    fun leave() {
        saved.forEach { (key, value) ->
            if (value == null) SettingsStore.remove(key) else SettingsStore.put(key, value)
        }
        saved = emptyMap()
    }
}
