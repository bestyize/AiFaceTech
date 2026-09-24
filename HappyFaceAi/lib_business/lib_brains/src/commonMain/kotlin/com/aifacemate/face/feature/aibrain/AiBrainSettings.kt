package com.aifacemate.face.feature.aibrain

import com.aifacemate.face.data.SettingsStore

/**
 * AI 大脑配置的落盘。
 *
 * 键沿用一个约定：跟某一家供应商绑的东西带 `:id` 后缀（`apikey:deepseek`、`models:deepseek`），
 * 全局偏好不带（`brain.provider`、`brain.thinking`）。
 *
 * **只有「保存」才写 Key 与 Base URL**：草稿态的输入只活在内存里，所以"落盘"这件事本身
 * 就等于"校验通过并保存过"。切供应商、换模型这些选择则即时落盘——它们不需要校验。
 */
object AiBrainSettings {

    private const val KEY_PROVIDER = "brain.provider"
    private const val KEY_THINKING = "brain.thinking"
    private const val CAPABILITY_PREFIX = "brain.capability."

    private fun modelOf(providerId: String) = "brain.model:$providerId"
    private fun apiKeyOf(providerId: String) = "apikey:$providerId"
    private fun baseUrlOf(providerId: String) = "baseUrl:$providerId"
    private fun customModelsOf(providerId: String) = "models:$providerId"

    // ── 读 ──────────────────────────────────────

    /** 上次选中的供应商。找不到（没存过 / id 已经不在内置列表里）时返回 null，由上层退回默认值。 */
    fun providerId(): String? = SettingsStore.get(KEY_PROVIDER)?.takeIf { it.isNotBlank() }

    /** 某一家上次选中的模型。按供应商分开存，切回来不会拿错别家的模型。 */
    fun modelId(providerId: String): String? =
        SettingsStore.get(modelOf(providerId))

    fun thinkingMode(): ThinkingMode? =
        SettingsStore.get(KEY_THINKING)?.let { saved -> ThinkingMode.entries.firstOrNull { it.name == saved } }

    fun capability(name: String): Boolean? =
        SettingsStore.get(CAPABILITY_PREFIX + name)?.toBooleanStrictOrNull()

    /** 校验通过并保存过的密钥。没存过、或者只剩空串时返回 null。 */
    fun savedSecret(providerId: String): SavedSecret? {
        val apiKey = SettingsStore.get(apiKeyOf(providerId))?.takeIf { it.isNotBlank() } ?: return null
        val baseUrl = SettingsStore.get(baseUrlOf(providerId))?.takeIf { it.isNotBlank() } ?: return null
        return SavedSecret(baseUrl = baseUrl, apiKey = apiKey)
    }

    fun customModels(providerId: String): List<String> = decodeList(SettingsStore.get(customModelsOf(providerId)))

    // ── 写 ──────────────────────────────────────

    fun saveSelection(providerId: String, modelId: String) {
        SettingsStore.put(KEY_PROVIDER, providerId)
        SettingsStore.put(modelOf(providerId), modelId)
    }

    fun saveThinkingMode(mode: ThinkingMode) {
        SettingsStore.put(KEY_THINKING, mode.name)
    }

    fun saveCapability(name: String, enabled: Boolean) {
        SettingsStore.put(CAPABILITY_PREFIX + name, enabled.toString())
    }

    fun saveSecret(providerId: String, secret: SavedSecret) {
        SettingsStore.put(baseUrlOf(providerId), secret.baseUrl)
        SettingsStore.put(apiKeyOf(providerId), secret.apiKey)
    }

    fun clearSecret(providerId: String) {
        SettingsStore.remove(apiKeyOf(providerId))
        SettingsStore.remove(baseUrlOf(providerId))
    }

    fun saveCustomModels(providerId: String, names: List<String>) {
        SettingsStore.put(customModelsOf(providerId), encodeList(names))
    }

    // ── 编码 ────────────────────────────────────
    // 自定义模型名是用户输入，可能带逗号，所以用换行分隔并先掐掉换行。

    internal fun encodeList(values: List<String>): String =
        values.map { it.replace('\n', ' ') }.joinToString("\n")

    internal fun decodeList(raw: String?): List<String> =
        raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
