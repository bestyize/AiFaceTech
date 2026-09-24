package com.aifacemate.face

import com.aifacemate.face.feature.aibrain.AiBrainUiState
import com.aifacemate.face.feature.aibrain.ConnectionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiBrainUiStateTest {
    @Test
    fun catalogsDoNotCarryACustomPlaceholder() {
        AiBrainUiState().providers.forEach { provider ->
            assertTrue(
                provider.models.none { it.id == "custom" },
                "${provider.name} 里不该再有「自定义」占位模型，自定义改由加号卡片添加",
            )
        }
    }

    @Test
    fun switchingProviderSelectsItsRecommendedModelAndBaseUrl() {
        val state = AiBrainUiState().selectProvider("kimi")
        assertEquals("kimi", state.config.providerId)
        assertEquals("kimi-k2.5", state.config.modelId)
        assertEquals("https://api.moonshot.cn/v1", state.config.baseUrl)
        // Choosing a catalog entry is not proof that its network connection works.
        assertEquals(ConnectionStatus.NotConfigured, state.connectionStatus)
    }

    @Test
    fun customProviderRequiresConfiguration() {
        val state = AiBrainUiState().selectProvider("custom")
        assertEquals("custom", state.config.providerId)
        assertEquals("", state.config.baseUrl)
        assertEquals(ConnectionStatus.NotConfigured, state.connectionStatus)
    }

    @Test
    fun customModelIsAppendedAndSelected() {
        val state = AiBrainUiState().addCustomModel("my-self-hosted-model")
        assertEquals("my-self-hosted-model", state.config.modelId)
        assertTrue(
            state.visibleModels.any { it.id == "my-self-hosted-model" },
            "自定义模型应当出现在模型选择列表末尾",
        )
        assertEquals("my-self-hosted-model", state.visibleModels.last().id)
    }

    @Test
    fun switchingProviderDropsFetchedModels() {
        val fetched = AiBrainUiState().withFetchedModels(
            listOf(com.aifacemate.face.feature.aibrain.RemoteModel("kimi-k2-0905-preview")),
        )
        assertTrue(fetched.visibleModels.any { it.id == "kimi-k2-0905-preview" })
        val switched = fetched.selectProvider("zhipu")
        assertTrue(switched.fetchedModels.isEmpty(), "换供应商后要清掉上一家的拉取结果")
    }

}
