package com.aifacemate.face.feature.aibrain.presentation
import com.aifacemate.face.feature.aibrain.presentation.theme.*

import com.aifacemate.face.feature.aibrain.*
import com.aifacemate.face.feature.aibrain.data.AiBrainSettings
import com.aifacemate.face.feature.aibrain.domain.*


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
class AiBrainViewModel : ViewModel() {
    var uiState by mutableStateOf(restoreSettings(AiBrainUiState()))
        private set

    /**
     * 实际发请求的实现。默认走各平台的 `fetchProviderModels`。
     *
     * 保持无参构造函数是有意为之：`viewModel()` 需要能直接实例化这个类，
     * 把可替换的依赖放在属性上而不是构造参数上，测试同样能注入桩实现。
     */
    internal var fetcher: suspend (String, String) -> ModelFetchResult = ::fetchProviderModels

    private var requestJob: kotlinx.coroutines.Job? = null
    private var requestVersion = 0

    private fun invalidateConnection() {
        requestVersion++
        requestJob?.cancel()
        uiState = uiState.copy(connectionStatus = ConnectionStatus.NotConfigured, statusMessage = "",
            secretChecking = false, fetchStatus = FetchStatus.Idle)
    }

    fun selectProvider(id: String) {
        if (uiState.providers.none { it.id == id } || uiState.config.providerId == id) return
        invalidateConnection()
        uiState = uiState.selectProvider(id)
        AiBrainSettings.modelId(id)?.let { saved -> uiState = uiState.selectModel(saved) }
        AiBrainSettings.saveSelection(uiState.config.providerId, uiState.config.modelId)
    }

    fun selectModel(id: String) {
        invalidateConnection()
        uiState = uiState.selectModel(id)
        AiBrainSettings.saveSelection(uiState.config.providerId, id)
    }

    fun selectThinkingMode(mode: ThinkingMode) {
        uiState = uiState.copy(thinkingMode = mode)
        AiBrainSettings.saveThinkingMode(mode)
    }

    fun toggleCapability(capability: AiCapability) {
        val enabled = !(uiState.capabilities[capability] ?: false)
        uiState = uiState.copy(capabilities = uiState.capabilities + (capability to enabled))
        AiBrainSettings.saveCapability(capability.name, enabled)
    }

    fun updateBaseUrl(value: String) {
        invalidateConnection()
        uiState = uiState.copy(config = uiState.config.copy(baseUrl = value))
    }

    fun updateApiKey(value: String) {
        invalidateConnection()
        uiState = uiState.copy(config = uiState.config.copy(apiKey = value),
            apiKeys = uiState.apiKeys + (uiState.config.providerId to value))
    }

    fun dismissApiKeyPrompt() { uiState = uiState.copy(apiKeyPromptVisible = false) }

    fun addCustomModel(name: String) {
        if (name.isBlank()) return
        invalidateConnection()
        uiState = uiState.addCustomModel(name)
        AiBrainSettings.saveCustomModels(uiState.config.providerId, uiState.customModels[uiState.config.providerId].orEmpty())
        AiBrainSettings.saveSelection(uiState.config.providerId, uiState.config.modelId)
    }

    fun consumeToast() { uiState = uiState.copy(toastMessage = null) }

    fun clearSecret() {
        invalidateConnection()
        AiBrainSettings.clearSecret(uiState.config.providerId)
        AiBrainSettings.saveSelection(uiState.config.providerId, "")
        uiState = uiState.withSecretCleared()
    }

    // Test never persists credentials. Save validates the exact snapshot before writing it.
    fun testConnection() = requestModels(save = false, testing = true)
    fun saveSecret() = requestModels(save = true, testing = true)
    fun fetchModels() = requestModels(save = false, testing = false)

    private fun requestModels(save: Boolean, testing: Boolean) {
        if (uiState.secretChecking || uiState.fetchStatus == FetchStatus.Loading) return
        val request = uiState.config.copy(baseUrl = uiState.config.baseUrl.trim(), apiKey = uiState.config.apiKey.trim())
        if (request.apiKey.isBlank() || request.baseUrl.isBlank()) {
            uiState = uiState.copy(apiKeyPromptVisible = true, toastMessage = "请填写 API Base URL 和 API Key")
            return
        }
        if (!Regex("https?://[^/\\s]+.*").matches(request.baseUrl)) {
            uiState = uiState.copy(toastMessage = "请输入 http:// 或 https:// 开头的完整地址")
            return
        }
        if (save && request.modelId.isBlank()) {
            uiState = uiState.copy(toastMessage = "请先选择或填写模型 ID")
            return
        }
        val version = ++requestVersion
        uiState = uiState.copy(config = request, secretChecking = testing, fetchStatus = FetchStatus.Loading,
            statusMessage = "正在验证模型接口…", toastMessage = null)
        requestJob = viewModelScope.launch {
            val result = try { fetcher(request.baseUrl, request.apiKey) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { ModelFetchResult.Failure("连接未完成，请检查网络与接口地址") }
            if (version != requestVersion || uiState.config != request) return@launch
            uiState = uiState.completeConnection(request, result)
            if (result is ModelFetchResult.Success) {
                if (save) {
                    val secret = SavedSecret(request.baseUrl, request.apiKey)
                    AiBrainSettings.saveSecret(request.providerId, secret)
                    AiBrainSettings.saveSelection(request.providerId, uiState.config.modelId)
                    uiState = uiState.copy(savedSecrets = uiState.savedSecrets + (request.providerId to secret),
                        toastMessage = "连接验证通过，配置已保存")
                } else if (testing) uiState = uiState.copy(toastMessage = "连接测试通过，尚未保存本次配置")
            }
        }
    }
}

fun restoreSettings(base: AiBrainUiState): AiBrainUiState {
    val providerId = AiBrainSettings.providerId()
        ?.takeIf { saved -> base.providers.any { it.id == saved } }
        ?: base.config.providerId

    val savedSecrets = base.providers
        .mapNotNull { provider -> AiBrainSettings.savedSecret(provider.id)?.let { provider.id to it } }
        .toMap()
    val customModels = base.providers
        .mapNotNull { provider -> AiBrainSettings.customModels(provider.id).takeIf { it.isNotEmpty() }?.let { provider.id to it } }
        .toMap()

    val restored = base.copy(
        config = base.config.copy(providerId = providerId),
        savedSecrets = savedSecrets,
        customModels = customModels,
        thinkingMode = AiBrainSettings.thinkingMode() ?: base.thinkingMode,
        capabilities = base.capabilities.mapValues { (capability, default) ->
            AiBrainSettings.capability(capability.name) ?: default
        },
    )

    val modelId = AiBrainSettings.modelId(providerId)
        ?: restored.visibleModels.firstOrNull()?.id
        ?: restored.config.modelId
    val secret = savedSecrets[providerId]
    val provider = restored.selectedProvider

    return restored.copy(
        config = restored.config.copy(
            modelId = modelId,
            baseUrl = secret?.baseUrl ?: provider.defaultBaseUrl,
            apiKey = secret?.apiKey.orEmpty(),
        ),
        apiKeys = savedSecrets.mapValues { (_, saved) -> saved.apiKey },
    )
}

