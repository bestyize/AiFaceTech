package com.aifacemate.face.feature.aibrain.domain
import com.aifacemate.face.feature.aibrain.presentation.theme.*

import com.aifacemate.face.feature.aibrain.*


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

enum class ConnectionStatus { Connected, NotConfigured, Invalid }

/** 「获取模型列表」的运行状态。 */
enum class FetchStatus { Idle, Loading, Success, Failure }

data class AiBrainUiState(
    val providers: List<AiProvider> = defaultAiProviders,
    val config: BrainConfig = BrainConfig(),
    val thinkingMode: ThinkingMode = ThinkingMode.Balanced,
    val capabilities: Map<AiCapability, Boolean> = AiCapability.entries.associateWith { it in setOf(AiCapability.WebSearch, AiCapability.LongTermMemory, AiCapability.ToolCalling, AiCapability.Schedule) },
    val connectionStatus: ConnectionStatus = ConnectionStatus.NotConfigured,
    val statusMessage: String = "",
    /** 从供应商 `/models` 拉回来的模型；为空时回退到内置目录。 */
    val fetchedModels: List<AiModel> = emptyList(),
    /** 用户自己添加的模型名，按供应商分开保存。 */
    val customModels: Map<String, List<String>> = emptyMap(),
    val fetchStatus: FetchStatus = FetchStatus.Idle,
    /** 没有 API Key 就点「获取模型列表」时，弹窗提示去填写。 */
    val apiKeyPromptVisible: Boolean = false,
    /** 各供应商各自的 API Key，切换供应商时互不串用。 */
    val apiKeys: Map<String, String> = emptyMap(),
    /** 校验通过并保存下来的密钥配置，按供应商分开存。 */
    val savedSecrets: Map<String, SavedSecret> = emptyMap(),
    /** 正在向供应商校验密钥。 */
    val secretChecking: Boolean = false,
    /** 一次性提示文案（toast），展示后由界面清掉。 */
    val toastMessage: String? = null,
) {
    /**
     * 当前供应商的密钥是否已保存。
     *
     * 只比 Base URL 和 API Key：换个模型不该让「已保存」变回「保存」。
     */
    val secretSaved: Boolean
        get() = savedSecrets[config.providerId] == SavedSecret(config.baseUrl.trim(), config.apiKey.trim())

    val selectedProvider: AiProvider
        get() = providers.firstOrNull { it.id == config.providerId } ?: providers.first()

    /** 模型选择里展示的列表：拉取结果优先，其次是内置目录，最后是用户自定义。 */
    val visibleModels: List<AiModel>
        get() {
            val base = if (fetchedModels.isNotEmpty()) fetchedModels else selectedProvider.models
            val custom = customModels[config.providerId].orEmpty().map { name ->
                AiModel(id = name, name = name, description = "自定义模型")
            }
            return (base + custom).distinctBy { it.id }
        }

    val selectedModel: AiModel
        get() = visibleModels.firstOrNull { it.id == config.modelId }
            ?: AiModel(id = config.modelId, name = config.modelId, description = "")

    fun selectProvider(providerId: String): AiBrainUiState {
        val provider = providers.firstOrNull { it.id == providerId } ?: return this
        val custom = customModels[provider.id].orEmpty()
        val model = provider.models.firstOrNull { it.recommended }
            ?: provider.models.firstOrNull()
            ?: custom.firstOrNull()?.let { name -> AiModel(id = name, name = name, description = "自定义模型") }
            ?: AiModel(id = "", name = "", description = "")

        val saved = savedSecrets[provider.id]
        val key = saved?.apiKey ?: apiKeys[provider.id].orEmpty()
        val base = saved?.baseUrl ?: provider.defaultBaseUrl
        val connected = ConnectionStatus.NotConfigured

        return copy(
            config = config.copy(
                providerId = provider.id,
                modelId = model.id,
                baseUrl = base,
                apiKey = key,
            ),
            fetchedModels = emptyList(),
            fetchStatus = FetchStatus.Idle,
            connectionStatus = connected,
            statusMessage = "",
            secretChecking = false,
        )
    }

    fun selectModel(modelId: String): AiBrainUiState = copy(config = config.copy(modelId = modelId))

    fun withFetchedModels(models: List<RemoteModel>): AiBrainUiState {
        val converted = models.distinctBy { it.id }.map { remote ->
            selectedProvider.models.firstOrNull { it.id == remote.id }
                ?: AiModel(remote.id, remote.id, "", tags = ModelCapabilities.of(remote.id))
        }
        // A /models response must not silently replace an explicitly selected model.
        return copy(
            fetchedModels = converted,
            fetchStatus = FetchStatus.Success,
            connectionStatus = ConnectionStatus.Connected,
            statusMessage = "模型接口验证通过（不代表对话调用已验证）",
            config = if (config.modelId.isBlank()) config.copy(modelId = converted.firstOrNull()?.id.orEmpty()) else config,
            toastMessage = "已获取 ${converted.size} 个模型",
        )
    }

    fun withFetchFailure(message: String): AiBrainUiState = copy(
        fetchStatus = FetchStatus.Failure,
        connectionStatus = ConnectionStatus.Invalid,
        statusMessage = message,
        toastMessage = message.ifEmpty { "获取模型列表失败，请检查网络和 API Key" },
    )

    /** Ignore responses from an old provider or edited credentials. */
    fun completeConnection(request: BrainConfig, result: ModelFetchResult): AiBrainUiState {
        if (config != request) return this
        return when (result) {
            is ModelFetchResult.Success -> withFetchedModels(result.models)
            is ModelFetchResult.Failure -> withFetchFailure(result.message)
        }.copy(secretChecking = false)
    }

    fun showApiKeyPrompt(): AiBrainUiState = copy(apiKeyPromptVisible = true)

    fun dismissApiKeyPrompt(): AiBrainUiState = copy(apiKeyPromptVisible = false)

    fun addCustomModel(modelName: String): AiBrainUiState {
        val trimmed = modelName.trim()
        if (trimmed.isEmpty()) return this
        val current = customModels[config.providerId].orEmpty()
        if (current.contains(trimmed)) {
            return copy(config = config.copy(modelId = trimmed))
        }
        val next = current + trimmed
        return copy(
            customModels = customModels + (config.providerId to next),
            config = config.copy(modelId = trimmed),
            toastMessage = "已添加自定义模型：$trimmed",
        )
    }

    /**
     * 校验通过后把当前配置写入「已保存」集合，同时刷新连接状态。
     *
     * 切换供应商不会丢掉上一家的已保存状态，只要别点「清除」。
     */
    fun withSecretSaved(): AiBrainUiState {
        val saved = SavedSecret(config.baseUrl.trim(), config.apiKey.trim())
        val nextSecrets = savedSecrets + (config.providerId to saved)
        return copy(
            savedSecrets = nextSecrets,
            connectionStatus = ConnectionStatus.Connected,
            toastMessage = "密钥已保存",
        )
    }

    /** 清掉当前供应商的已保存密钥，连接状态退回 NotConfigured。 */
    fun withSecretCleared(): AiBrainUiState {
        val nextSecrets = savedSecrets - config.providerId
        val nextKeys = apiKeys - config.providerId
        return copy(
            savedSecrets = nextSecrets,
            apiKeys = nextKeys,
            config = config.copy(baseUrl = "", apiKey = "", modelId = ""),
            fetchedModels = emptyList(),
            fetchStatus = FetchStatus.Idle,
            secretChecking = false,
            statusMessage = "配置已清空",
            connectionStatus = ConnectionStatus.NotConfigured,
            toastMessage = "密钥已清除",
        )
    }

    fun withSecretChecking(checking: Boolean): AiBrainUiState = copy(secretChecking = checking)
}
