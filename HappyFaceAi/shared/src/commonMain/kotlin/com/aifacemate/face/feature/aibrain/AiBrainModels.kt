package com.aifacemate.face.feature.aibrain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

data class AiModel(
    val id: String,
    val name: String,
    val description: String,
    val recommended: Boolean = false,
    val tags: List<String> = emptyList(),
    /** 「当前大脑」卡片里的一句话概括；留空时回退到 [description]。 */
    val summary: String = "",
)

data class AiProvider(
    val id: String,
    val name: String,
    val subtitle: String,
    val symbol: String,
    val defaultBaseUrl: String,
    val models: List<AiModel>,
)

data class BrainConfig(
    val providerId: String = "deepseek",
    val modelId: String = "deepseek-v4-pro",
    val baseUrl: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
)

/** 思考模式。[detail] 用换行分成两行，对齐设计稿的两行说明。 */
enum class ThinkingMode(val title: String, val detail: String, val symbol: String) {
    Fast("快速", "更快响应\n适合简单问题", "⚡"),
    Balanced("平衡", "效果与速度兼顾\n日常推荐", "⚖"),
    Deep("深度", "更深入的思考\n适合复杂问题", "◉"),
}

enum class AiCapability(val title: String, val description: String, val symbol: String) {
    WebSearch("联网搜索", "获取最新的网络信息", "◎"),
    LongTermMemory("长期记忆", "记住重要的对话内容", "▤"),
    ToolCalling("工具调用", "使用各种实用工具", "◆"),
    Schedule("日程与提醒", "管理日程、设置提醒", "□"),
}

enum class ConnectionStatus { Connected, NotConfigured, Invalid }

/** 「获取模型列表」的运行状态。 */
enum class FetchStatus { Idle, Loading, Success, Failure }

data class AiBrainUiState(
    val providers: List<AiProvider> = defaultAiProviders,
    val config: BrainConfig = BrainConfig(),
    val thinkingMode: ThinkingMode = ThinkingMode.Balanced,
    val capabilities: Map<AiCapability, Boolean> = AiCapability.entries.associateWith { true },
    val connectionStatus: ConnectionStatus = ConnectionStatus.Connected,
    val statusMessage: String = "",
    /** 从供应商 `/models` 拉回来的模型；为空时回退到内置目录。 */
    val fetchedModels: List<AiModel> = emptyList(),
    /** 用户自己添加的模型名，按供应商分开保存。 */
    val customModels: Map<String, List<String>> = emptyMap(),
    val fetchStatus: FetchStatus = FetchStatus.Idle,
    /** 没有 API Key 就点「获取模型列表」时，弹窗提示去填写。 */
    val apiKeyPromptVisible: Boolean = false,
    /** 校验通过并保存下来的密钥配置；和当前配置不一致时「保存」按钮重新可点。 */
    val savedSecret: BrainConfig? = null,
    /** 正在向供应商校验密钥。 */
    val secretChecking: Boolean = false,
    /** 一次性提示文案（toast），展示后由界面清掉。 */
    val toastMessage: String? = null,
) {
    val selectedProvider: AiProvider
        get() = providers.firstOrNull { it.id == config.providerId } ?: providers.first()

    /** 模型选择里展示的列表：拉取结果优先，其次是内置目录，最后是用户自定义。 */
    val visibleModels: List<AiModel>
        get() {
            val base = if (fetchedModels.isNotEmpty()) fetchedModels else selectedProvider.models
            val custom = customModels[config.providerId].orEmpty().map { name ->
                AiModel(id = name, name = name, description = "自定义模型")
            }
            return base + custom
        }

    val selectedModel: AiModel
        get() = visibleModels.firstOrNull { it.id == config.modelId }
            ?: visibleModels.firstOrNull()
            ?: AiModel(id = config.modelId, name = config.modelId, description = "")

    fun selectProvider(providerId: String): AiBrainUiState {
        val provider = providers.firstOrNull { it.id == providerId } ?: return this
        val custom = customModels[provider.id].orEmpty()
        val model = provider.models.firstOrNull { it.recommended }
            ?: provider.models.firstOrNull()
            ?: custom.firstOrNull()?.let { name -> AiModel(id = name, name = name, description = "自定义模型") }
            ?: AiModel(id = "", name = "", description = "")
        return copy(
            config = config.copy(
                providerId = provider.id,
                modelId = model.id,
                baseUrl = provider.defaultBaseUrl,
            ),
            fetchedModels = emptyList(),
            fetchStatus = FetchStatus.Idle,
            apiKeyPromptVisible = false,
            connectionStatus = if (provider.id == "custom") ConnectionStatus.NotConfigured else ConnectionStatus.Connected,
            statusMessage = if (provider.id == "custom") "请填写自己的 AI 服务" else "已切换到 ${provider.name}",
        )
    }

    fun selectModel(modelId: String): AiBrainUiState {
        val model = visibleModels.firstOrNull { it.id == modelId } ?: return this
        // 选中这件事由模型卡右上角的「已选择」标签表达，不再写一行状态文案。
        return copy(
            config = config.copy(modelId = model.id),
            statusMessage = "",
        )
    }

    /** 拉取成功：用服务端返回的模型覆盖列表，并默认选中当前模型（若还在）。 */
    fun withFetchedModels(models: List<RemoteModel>): AiBrainUiState {
        val mapped = models.map { remote ->
            AiModel(id = remote.id, name = remote.id, description = remote.ownedBy?.let { "由 $it 提供" }.orEmpty())
        }
        val stillThere = mapped.any { it.id == config.modelId }
        return copy(
            fetchedModels = mapped,
            config = if (stillThere || mapped.isEmpty()) config else config.copy(modelId = mapped.first().id),
            fetchStatus = FetchStatus.Success,
            apiKeyPromptVisible = false,
            connectionStatus = ConnectionStatus.Connected,
            statusMessage = "已获取 ${mapped.size} 个模型",
        )
    }

    fun withFetchFailure(message: String): AiBrainUiState = copy(
        fetchStatus = FetchStatus.Failure,
        connectionStatus = ConnectionStatus.Invalid,
        statusMessage = message,
    )

    fun addCustomModel(name: String): AiBrainUiState {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return this
        val providerId = config.providerId
        val existing = customModels[providerId].orEmpty()
        val updated = if (trimmed in existing) existing else existing + trimmed
        return copy(
            customModels = customModels + (providerId to updated),
            config = config.copy(modelId = trimmed),
            statusMessage = "已添加自定义模型 $trimmed",
        )
    }
}

class AiBrainViewModel : ViewModel() {
    var uiState by mutableStateOf(AiBrainUiState())
        private set

    fun selectProvider(id: String) { uiState = uiState.selectProvider(id) }
    fun selectModel(id: String) { uiState = uiState.selectModel(id) }
    fun selectThinkingMode(mode: ThinkingMode) { uiState = uiState.copy(thinkingMode = mode) }
    fun toggleCapability(capability: AiCapability) {
        uiState = uiState.copy(capabilities = uiState.capabilities + (capability to !(uiState.capabilities[capability] ?: false)))
    }
    fun updateBaseUrl(value: String) { uiState = uiState.copy(config = uiState.config.copy(baseUrl = value)) }
    fun updateApiKey(value: String) { uiState = uiState.copy(config = uiState.config.copy(apiKey = value)) }

    fun dismissApiKeyPrompt() { uiState = uiState.copy(apiKeyPromptVisible = false) }

    fun addCustomModel(name: String) { uiState = uiState.addCustomModel(name) }

    /** 界面把 toast 展示完后清掉，避免重复弹。 */
    fun consumeToast() {
        if (uiState.toastMessage != null) uiState = uiState.copy(toastMessage = null)
    }

    /**
     * 「保存」密钥配置：先校验再保存。
     *
     * 没填 API Key 只提示；填了就拿它去请求一次供应商的 `/models`，通了才算有效。
     * 保存成功后按钮变「已保存」，改动 API Key / Base URL 才会重新可点。
     */
    fun saveSecret() {
        val current = uiState
        val apiKey = current.config.apiKey.trim()
        if (apiKey.isBlank()) {
            uiState = current.copy(toastMessage = "需要先填写 API Key")
            return
        }
        val config = current.config.copy(baseUrl = current.config.baseUrl.trim(), apiKey = apiKey)
        uiState = current.copy(config = config, secretChecking = true, toastMessage = null)
        viewModelScope.launch {
            when (val result = fetchProviderModels(config.baseUrl, apiKey)) {
                is ModelFetchResult.Success -> uiState = uiState
                    .withFetchedModels(result.models)
                    .copy(secretChecking = false, savedSecret = config, toastMessage = "API Key 校验通过，已保存")

                is ModelFetchResult.Failure -> uiState = uiState
                    .withFetchFailure(result.message)
                    .copy(secretChecking = false, toastMessage = "校验未通过：${result.message}")
            }
        }
    }

    /**
     * 真实拉取供应商的模型列表。
     *
     * 没填 API Key 时不发请求，直接弹窗让用户去「密钥配置」补上。
     */
    fun fetchModels() {
        val current = uiState
        if (current.config.apiKey.isBlank()) {
            uiState = current.copy(
                apiKeyPromptVisible = true,
                fetchStatus = FetchStatus.Idle,
                statusMessage = "请先在「密钥配置」里填写 API Key",
            )
            return
        }
        uiState = current.copy(
            fetchStatus = FetchStatus.Loading,
            apiKeyPromptVisible = false,
            statusMessage = "正在获取模型列表…",
        )
        viewModelScope.launch {
            val baseUrl = uiState.config.baseUrl
            val apiKey = uiState.config.apiKey
            when (val result = fetchProviderModels(baseUrl, apiKey)) {
                is ModelFetchResult.Success -> uiState = uiState.withFetchedModels(result.models)
                is ModelFetchResult.Failure -> uiState = uiState.withFetchFailure(result.message)
            }
        }
    }
}

val defaultAiProviders = listOf(
    AiProvider(
        id = "deepseek",
        name = "DeepSeek",
        subtitle = "深度求索",
        symbol = "DS",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        models = listOf(
            AiModel(
                id = "deepseek-v4-pro",
                name = "DeepSeek-V4-Pro",
                description = "最强大的推理与创作能力\n适合复杂任务",
                recommended = true,
                tags = listOf("深度思考", "中文友好", "高性价比"),
                summary = "强大、快速、擅长中文的推理模型",
            ),
            AiModel(
                "deepseek-flash", "DeepSeek-Flash", "更快的响应速度\n适合日常对话",
            ),
            AiModel(
                "deepseek-v3", "DeepSeek-V3", "稳定可靠\n适合通用场景",
            ),
        ),
    ),
    AiProvider(
        id = "zhipu",
        name = "智谱",
        subtitle = "智谱 AI",
        symbol = "ZP",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        models = listOf(
            AiModel(
                "glm-4.5", "GLM-4.5", "通用推理与工具使用", true, listOf("中文友好", "工具调用"),
            ),
            AiModel(
                "glm-4.5-air", "GLM-4.5-Air", "轻量、快速的日常模型",
            ),
        ),
    ),
    AiProvider(
        id = "kimi",
        name = "Kimi",
        subtitle = "月之暗面",
        symbol = "K",
        defaultBaseUrl = "https://api.moonshot.cn/v1",
        models = listOf(
            AiModel(
                "kimi-k2.5", "Kimi-K2.5", "长文本理解与复杂任务", true, listOf("长上下文", "中文友好"),
            ),
            AiModel(
                "kimi-k2-turbo", "Kimi-K2-Turbo", "更快速的长文本对话",
            ),
        ),
    ),
    AiProvider(
        id = "minimax",
        name = "MiniMax",
        subtitle = "MiniMax",
        symbol = "MM",
        defaultBaseUrl = "https://api.minimax.chat/v1",
        models = listOf(
            AiModel(
                "minimax-m2.5", "MiniMax-M2.5", "综合能力与自然表达", true, listOf("自然对话", "高性价比"),
            ),
            AiModel(
                "minimax-m2.1", "MiniMax-M2.1", "稳定的通用模型",
            ),
        ),
    ),
    AiProvider(
        id = "custom",
        name = "自定义",
        subtitle = "OpenAI Compatible",
        symbol = "+",
        defaultBaseUrl = "",
        models = emptyList(),
    ),
)
