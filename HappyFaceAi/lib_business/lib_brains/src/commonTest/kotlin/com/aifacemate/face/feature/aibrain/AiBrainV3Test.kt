package com.aifacemate.face.feature.aibrain

import kotlin.test.*

class AiBrainV3Test {
    @Test fun onlyFourNavigationSections() {
        assertEquals(listOf("AI 大脑", "密钥配置", "能力接入", "思考模式"), BrainNavSection.entries.map { it.title })
    }
    @Test fun layoutsUseAvailableDp() {
        assertTrue(brainLayout(480f, 320f).compact)
        assertFalse(brainLayout(480f, 320f).labelledRail)
        assertFalse(brainLayout(800f, 480f).artwork)
        assertTrue(brainLayout(873f, 393f).artwork)
        assertFalse(brainLayout(320f, 213f).artwork)
    }
    @Test fun noProviderIsConnectedWithoutATest() {
        val state = AiBrainUiState()
        assertEquals(ConnectionStatus.NotConfigured, state.connectionStatus)
        state.providers.forEach { assertEquals(ConnectionStatus.NotConfigured, state.selectProvider(it.id).connectionStatus) }
    }
    @Test fun fetchedModelsPreserveSelectionAndDeduplicate() {
        val state = AiBrainUiState().selectModel("my-pinned-model")
            .withFetchedModels(listOf(RemoteModel("other"), RemoteModel("other")))
        assertEquals("my-pinned-model", state.config.modelId)
        assertEquals("my-pinned-model", state.selectedModel.id)
        assertEquals(1, state.fetchedModels.size)
        assertEquals(ConnectionStatus.Connected, state.connectionStatus)
        assertTrue(state.savedSecrets.isEmpty(), "A test must not save a key")
    }
    @Test fun staleNetworkResponseCannotUpdateNewProviderOrEditedKey() {
        val before = AiBrainUiState()
        val switched = before.selectProvider("kimi")
        val result = ModelFetchResult.Success(listOf(RemoteModel("old-provider-model")))
        assertEquals(switched, switched.completeConnection(before.config, result))
        val edited = before.copy(config = before.config.copy(apiKey = "different-key"))
        assertEquals(edited, edited.completeConnection(before.config, result))
    }
    @Test fun failureResetsValidatedStatus() {
        val state = AiBrainUiState(connectionStatus = ConnectionStatus.Connected, secretChecking = true)
        val failed = state.completeConnection(state.config, ModelFetchResult.Failure("HTTP 401"))
        assertEquals(ConnectionStatus.Invalid, failed.connectionStatus)
        assertFalse(failed.secretChecking)
        assertEquals("HTTP 401", failed.statusMessage)
    }
    @Test fun clearOnlyRemovesCurrentProvidersCredentials() {
        val key = SavedSecret("https://example.test/v1", "test-key")
        val state = AiBrainUiState(savedSecrets = mapOf("deepseek" to key, "kimi" to key))
            .withSecretCleared()
        assertEquals(mapOf("kimi" to key), state.savedSecrets)
        assertEquals("", state.config.apiKey)
        assertEquals("", state.config.baseUrl)
        assertEquals("", state.config.modelId)
    }
    @Test fun eightCapabilitiesNewEntriesDefaultOff() {
        assertEquals(8, AiCapability.entries.size)
        val state = AiBrainUiState()
        assertFalse(state.capabilities.getValue(AiCapability.CameraVision))
        assertFalse(state.capabilities.getValue(AiCapability.SmartHome))
    }
}
