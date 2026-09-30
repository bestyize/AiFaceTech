package com.aifacemate.face

import com.aifacemate.face.data.SettingsStore

data class FaceEffectsPreferences(val enabled: Boolean = true, val strength: Float = .75f, val emoji: Boolean = true)
object FaceEffectsSettings {
    fun load() = FaceEffectsPreferences(
        enabled = SettingsStore.get("effects.enabled")?.toBooleanStrictOrNull() ?: true,
        strength = SettingsStore.get("effects.strength")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(.2f, 1f) ?: .75f,
        emoji = SettingsStore.get("effects.emoji")?.toBooleanStrictOrNull() ?: true,
    )
    fun save(value: FaceEffectsPreferences) {
        SettingsStore.put("effects.enabled", value.enabled.toString())
        SettingsStore.put("effects.strength", value.strength.coerceIn(.2f, 1f).toString())
        SettingsStore.put("effects.emoji", value.emoji.toString())
    }
}
