package com.aifacemate.face

import com.aifacemate.face.data.SettingsStore
import com.aifacemate.face.voice.systemAsrAvailable
import com.aifacemate.face.voice.systemTtsAvailable
import com.aifacemate.face.voice.realtimeAudioAvailable
import com.aifacemate.face.voice.tts.client.PiperTts

enum class VoiceMode(val label: String) {
    Realtime("实时语音对话"), Traditional("分段语音对话")
}

enum class RealtimeProvider(val label: String) { Doubao("豆包"), Qwen("千问") }
enum class TraditionalAsr(val label: String) {
    System("系统语音识别"), Mimo("小米 MiMo 在线识别"), SenseVoice("SenseVoice 本地识别")
}
enum class TraditionalTts(val label: String) { System("系统语音合成"), Mimo("小米 MiMo 在线合成"), Piper("Piper 本地合成") }

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
    val traditionalAsr: TraditionalAsr = TraditionalAsr.Mimo,
    val traditionalTts: TraditionalTts = TraditionalTts.Mimo,
    val proactiveConversation: Boolean = true,
)

/** A saved option can be unavailable on a different device. */
fun VoicePreferences.forDevice(): VoicePreferences {
    var available = if (mode == VoiceMode.Realtime && !realtimeAudioAvailable()) copy(mode = VoiceMode.Traditional) else this
    if (available.traditionalAsr == TraditionalAsr.SenseVoice &&
        !com.aifacemate.face.voice.asr.client.SenseVoiceAsr.supported()
    ) available = available.copy(traditionalAsr = TraditionalAsr.Mimo)
    if (!systemAsrAvailable() && available.traditionalAsr == TraditionalAsr.System) {
        available = available.copy(traditionalAsr = TraditionalAsr.Mimo)
    }
    if (!systemTtsAvailable() && available.traditionalTts == TraditionalTts.System) {
        available = available.copy(traditionalTts = TraditionalTts.Mimo)
    }
    if (!PiperTts.supported() && available.traditionalTts == TraditionalTts.Piper) {
        available = available.copy(traditionalTts = TraditionalTts.Mimo)
    }
    return available
}

/** Voice settings remain separate from the LLM selected in AI 大脑. */
object VoicePreferencesStore {
    private const val PREFIX = "voice."
    fun load(): VoicePreferences {
        val savedMode = SettingsStore.get(PREFIX + "mode")
        val legacySystemMode = savedMode == "Native"
        return VoicePreferences(
            mode = if (legacySystemMode) VoiceMode.Traditional else
                savedMode?.let { raw -> VoiceMode.entries.firstOrNull { it.name == raw } } ?: VoiceMode.Realtime,
            keepFaceVisible = SettingsStore.get(PREFIX + "keepFaceVisible")?.toBooleanStrictOrNull() ?: true,
            proactiveConversation = SettingsStore.get(PREFIX + "proactiveConversation")?.toBooleanStrictOrNull() ?: true,
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
            traditionalAsr = if (legacySystemMode) TraditionalAsr.System else
                SettingsStore.get(PREFIX + "traditional.asr")?.let { raw -> TraditionalAsr.entries.firstOrNull { it.name == raw } } ?: TraditionalAsr.Mimo,
            traditionalTts = if (legacySystemMode) TraditionalTts.System else
                SettingsStore.get(PREFIX + "traditional.tts")?.let { raw -> TraditionalTts.entries.firstOrNull { it.name == raw } } ?: TraditionalTts.Mimo,
        )
    }

    fun save(value: VoicePreferences) {
        val fields = mapOf(
            "mode" to value.mode.name, "realtime.provider" to value.provider.name,
            "keepFaceVisible" to value.keepFaceVisible.toString(),
            "proactiveConversation" to value.proactiveConversation.toString(),
            "qwen.model" to value.qwenModel, "qwen.endpoint" to value.qwenEndpoint.trim(), "qwen.apiKey" to value.qwenApiKey.trim(),
            "doubao.appId" to value.doubaoAppId.trim(), "doubao.accessKey" to value.doubaoAccessKey.trim(), "doubao.appKey" to value.doubaoAppKey.trim(),
            "mimo.apiKey" to value.mimoApiKey.trim(), "mimo.baseUrl" to value.mimoBaseUrl.trim(), "mimo.voice" to value.mimoVoice,
            "traditional.asr" to value.traditionalAsr.name, "traditional.tts" to value.traditionalTts.name,
        )
        fields.forEach { (key, text) -> SettingsStore.put(PREFIX + key, text) }
    }
}
