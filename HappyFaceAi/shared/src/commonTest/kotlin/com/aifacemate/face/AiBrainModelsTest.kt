package com.aifacemate.face

import com.aifacemate.face.feature.aibrain.AiBrainUiState
import com.aifacemate.face.feature.aibrain.ConnectionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiBrainModelsTest {
    @Test
    fun everyProviderEndsWithACustomModel() {
        AiBrainUiState().providers.forEach { provider ->
            assertEquals("custom", provider.models.last().id, "${provider.name} 的最后一个模型应为自定义")
            assertTrue(provider.models.size <= 5, "${provider.name} 默认模型不应超过 5 个")
        }
    }

    @Test
    fun switchingProviderSelectsItsRecommendedModelAndBaseUrl() {
        val state = AiBrainUiState().selectProvider("kimi")
        assertEquals("kimi", state.config.providerId)
        assertEquals("kimi-k2.5", state.config.modelId)
        assertEquals("https://api.moonshot.cn/v1", state.config.baseUrl)
        assertEquals(ConnectionStatus.Connected, state.connectionStatus)
    }

    @Test
    fun customProviderRequiresConfiguration() {
        val state = AiBrainUiState().selectProvider("custom")
        assertEquals("custom", state.config.providerId)
        assertEquals("", state.config.baseUrl)
        assertEquals(ConnectionStatus.NotConfigured, state.connectionStatus)
    }
}
