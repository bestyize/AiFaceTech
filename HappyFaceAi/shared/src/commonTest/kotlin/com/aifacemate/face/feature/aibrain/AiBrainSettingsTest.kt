package com.aifacemate.face.feature.aibrain

import com.aifacemate.face.data.SettingsSandbox
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 落盘与冷启还原。
 *
 * 这些键在 JVM 上就是桌面端在用的那一份 `java.util.prefs`，所以进出都走 [SettingsSandbox]，
 * 免得跑一次测试把用户自己填的 Key 抹掉。
 */
class AiBrainSettingsTest {

    private val sandbox = SettingsSandbox(
        listOf(
            "brain.provider",
            "brain.thinking",
            "brain.model:deepseek",
            "brain.model:zhipu",
            "apikey:deepseek",
            "baseUrl:deepseek",
            "models:deepseek",
            "brain.capability.WebSearch",
            "brain.capability.LongTermMemory",
            "brain.capability.ToolCalling",
            "brain.capability.Schedule",
        ),
    )

    @BeforeTest
    fun setUp() = sandbox.enter()

    @AfterTest
    fun tearDown() = sandbox.leave()

    @Test
    fun savedSecretRoundTrip() {
        val secret = SavedSecret(baseUrl = "https://api.deepseek.com/v1", apiKey = "sk-test-1234")
        AiBrainSettings.saveSecret("deepseek", secret)

        assertEquals(secret, AiBrainSettings.savedSecret("deepseek"))
        // 没配过的供应商读回 null，而不是空壳，否则「已保存」会误判为真
        assertNull(AiBrainSettings.savedSecret("zhipu"))
    }

    @Test
    fun coldStartRestoresEverything() {
        AiBrainSettings.saveSecret("deepseek", SavedSecret("https://api.deepseek.com/v1", "sk-test-1234"))
        AiBrainSettings.saveSelection("deepseek", "deepseek-flash")
        AiBrainSettings.saveThinkingMode(ThinkingMode.Deep)
        AiBrainSettings.saveCapability(AiCapability.WebSearch.name, false)
        AiBrainSettings.saveCustomModels("deepseek", listOf("my-local-llm"))

        val state = restoreSettings(AiBrainUiState())

        assertEquals("deepseek", state.config.providerId)
        assertEquals("deepseek-flash", state.config.modelId)
        assertEquals("sk-test-1234", state.config.apiKey)
        assertEquals(ThinkingMode.Deep, state.thinkingMode)
        assertEquals(false, state.capabilities[AiCapability.WebSearch])
        // 其它能力保持默认
        assertEquals(true, state.capabilities[AiCapability.ToolCalling])
        assertTrue("my-local-llm" in state.visibleModels.map { it.id })
        assertTrue(state.secretSaved, "存过并校验通过的 Key 冷启后就该是「已保存」")
    }

    @Test
    fun emptyStoreFallsBackToDefaults() {
        val state = restoreSettings(AiBrainUiState())

        assertEquals("deepseek", state.config.providerId)
        assertEquals("deepseek-v4-pro", state.config.modelId)
        assertEquals("https://api.deepseek.com/v1", state.config.baseUrl)
        assertEquals("", state.config.apiKey)
        assertFalse(state.secretSaved)
    }

    /** 模型名是用户输入，可能带逗号；分隔符用换行，逗号要原样保留。 */
    @Test
    fun customModelNamesSurviveCommas() {
        val names = listOf("glm-4.5-air", "my,model")
        AiBrainSettings.saveCustomModels("zhipu", names)

        assertEquals(names, AiBrainSettings.customModels("zhipu"))
    }
}
