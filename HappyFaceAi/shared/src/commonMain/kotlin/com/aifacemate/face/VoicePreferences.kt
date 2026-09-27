package com.aifacemate.face

import com.aifacemate.face.data.SettingsStore
import com.aifacemate.face.voice.nativeSpeechAvailable
import com.aifacemate.face.voice.realtimeAudioAvailable

enum class VoiceMode(val label: String) {
    Realtime("实时对话"), Traditional("传统对话 · MiMo"), Native("系统原生语音")
}

enum class RealtimeProvider(val label: String) { Doubao("豆包"), Qwen("千问") }

data class VoicePreferences(
    val mode: VoiceMode = VoiceMode.Realtime,
    val keepFaceVisible: Boolean = true,
    val provider: RealtimeProvider = RealtimeProvider.Qwen,
    val qwenModel: String = "qwen3.5-omni-flash-realtime",
    val qwenEndpoint: String = "",
    val qwenApiKey: String = "",
    val doubaoAppId: String = "",
    val doubaoAccessKey: String = "",
    val doubaoAppKey: String = "",
    val mimoApiKey: String = "",
    val mimoBaseUrl: String = "https://api.xiaomimimo.com/v1",
    val mimoVoice: String = "Chloe",
)

/** A saved mode can be unavailable on a different device. */
fun VoicePreferences.forDevice(): VoicePreferences = when {
    mode == VoiceMode.Realtime && !realtimeAudioAvailable() ->
        copy(mode = if (nativeSpeechAvailable()) VoiceMode.Native else VoiceMode.Traditional)
    mode == VoiceMode.Native && !nativeSpeechAvailable() -> copy(mode = VoiceMode.Traditional)
    else -> this
}

/** Voice settings remain separate from the LLM selected in AI 大脑. */
object VoicePreferencesStore {
    private const val PREFIX = "voice."
    fun load() = VoicePreferences(
        mode = SettingsStore.get(PREFIX + "mode")?.let { raw -> VoiceMode.entries.firstOrNull { it.name == raw } } ?: VoiceMode.Realtime,
        keepFaceVisible = SettingsStore.get(PREFIX + "keepFaceVisible")?.toBooleanStrictOrNull() ?: true,
        provider = SettingsStore.get(PREFIX + "realtime.provider")?.let { raw -> RealtimeProvider.entries.firstOrNull { it.name == raw } } ?: RealtimeProvider.Qwen,
        qwenModel = SettingsStore.get(PREFIX + "qwen.model")?.takeIf(String::isNotBlank) ?: "qwen3.5-omni-flash-realtime",
        qwenEndpoint = SettingsStore.get(PREFIX + "qwen.endpoint").orEmpty(),
        qwenApiKey = SettingsStore.get(PREFIX + "qwen.apiKey").orEmpty(),
        doubaoAppId = SettingsStore.get(PREFIX + "doubao.appId").orEmpty(),
        doubaoAccessKey = SettingsStore.get(PREFIX + "doubao.accessKey").orEmpty(),
        doubaoAppKey = SettingsStore.get(PREFIX + "doubao.appKey").orEmpty(),
        mimoApiKey = SettingsStore.get(PREFIX + "mimo.apiKey").orEmpty(),
        mimoBaseUrl = SettingsStore.get(PREFIX + "mimo.baseUrl")?.takeIf(String::isNotBlank) ?: "https://api.xiaomimimo.com/v1",
        mimoVoice = SettingsStore.get(PREFIX + "mimo.voice")?.takeIf(String::isNotBlank) ?: "Chloe",
    )

    fun save(value: VoicePreferences) {
        val fields = mapOf(
            "mode" to value.mode.name, "realtime.provider" to value.provider.name,
            "keepFaceVisible" to value.keepFaceVisible.toString(),
            "qwen.model" to value.qwenModel, "qwen.endpoint" to value.qwenEndpoint.trim(), "qwen.apiKey" to value.qwenApiKey.trim(),
            "doubao.appId" to value.doubaoAppId.trim(), "doubao.accessKey" to value.doubaoAccessKey.trim(), "doubao.appKey" to value.doubaoAppKey.trim(),
            "mimo.apiKey" to value.mimoApiKey.trim(), "mimo.baseUrl" to value.mimoBaseUrl.trim(), "mimo.voice" to value.mimoVoice,
        )
        fields.forEach { (key, text) -> SettingsStore.put(PREFIX + key, text) }
    }
}
