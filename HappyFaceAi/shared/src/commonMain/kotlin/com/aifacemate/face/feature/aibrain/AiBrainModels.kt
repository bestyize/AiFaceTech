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

/** 校验通过并保存下来的密钥配置。按供应商分开存，切供应商不会串用别人的 Key。 */
data class SavedSecret(val baseUrl: String, val apiKey: String)

/** 思考模式。[detail] 用换行分成两行，对齐设计稿的两行说明。 */
enum class ThinkingMode(val title: String, val detail: String, val symbol: String) {
    Fast("快速", "更快响应\n适合简单问题", "⚡"),
    Balanced("平衡", "效果与速度兼顾\n日常推荐", "⚖"),
    Deep("深度", "更深入的思考\n适合复杂问题", "◉"),
}

enum class AiCapability(val title: String, val description: String, val symbol: String, val icon: String) {
    WebSearch("联网搜索", "获取网络信息，提供有时效的回答", "◎", "globe"),
    LongTermMemory("长期记忆", "记住偏好与重要信息", "▤", "memory"),
    ToolCalling("工具调用", "调用工具和服务，完成复杂任务", "◆", "model"),
    Schedule("日程提醒", "管理日程安排与重要事项", "□", "calendar"),
    SmartHome("智能家居", "连接家中的智能设备", "⌂", "home"),
    CameraVision("相机视觉", "理解图像内容与视觉问题", "◉", "camera"),
    VoiceConversation("语音对话", "语音输入与回复，自然交流", "♫", "voice"),
    FileUnderstanding("文件理解", "解析文件，提取关键信息", "▤", "file"),
}

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
    internal fun completeConnection(request: BrainConfig, result: ModelFetchResult): AiBrainUiState {
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

internal fun restoreSettings(base: AiBrainUiState): AiBrainUiState {
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

/** 完整对齐新版设计稿的 11 家模型服务提供商 */
val defaultAiProviders = listOf(
    AiProvider(
        id = "openai",
        name = "OpenAI",
        subtitle = "全球领先的人工智能研发机构",
        symbol = "OAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        models = listOf(
            AiModel(
                id = "gpt-4o",
                name = "GPT-4o",
                description = "强大的多模态通用大模型",
                recommended = true,
                tags = listOf("多模态", "逻辑推理", "旗舰性能"),
                summary = "强大的多模态通用大模型",
            ),
            AiModel("gpt-4o-mini", "GPT-4o-mini", "轻巧快速的高性价比多模态模型"),
            AiModel("o1", "o1", "擅长深层复杂推理的思维链模型"),
        ),
    ),
    AiProvider(
        id = "claude",
        name = "Claude",
        subtitle = "Anthropic 核心模型",
        symbol = "CL",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        models = listOf(
            AiModel(
                id = "claude-3-5-sonnet",
                name = "Claude 3.5 Sonnet",
                description = "卓越的代码、写作与视觉理解能力",
                recommended = true,
                tags = listOf("代码专精", "长上下文", "高智能"),
                summary = "卓越的代码、写作与视觉理解能力",
            ),
            AiModel("claude-3-5-haiku", "Claude 3.5 Haiku", "极速敏捷的轻量级模型"),
        ),
    ),
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
                summary = "最强大的推理与深度思考模型",
            ),
            AiModel("deepseek-flash", "DeepSeek-Flash", "更快的响应速度\n适合日常对话"),
            AiModel("deepseek-v3", "DeepSeek-V3", "稳定可靠的通用大模型"),
            AiModel("deepseek-r1", "DeepSeek-R1", "最强大的推理与深度思考模型"),
        ),
    ),
    AiProvider(
        id = "qwen",
        name = "Qwen",
        subtitle = "通义千问",
        symbol = "QW",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        models = listOf(
            AiModel(
                id = "qwen-2.5-max",
                name = "Qwen-2.5-Max",
                description = "千问系列旗舰超大规模模型",
                recommended = true,
                tags = listOf("中文原生", "工具调用", "全能旗舰"),
                summary = "千问系列旗舰超大规模模型",
            ),
            AiModel("qwen-2.5-72b", "Qwen-2.5-72B", "开源领跑的高性能大模型"),
        ),
    ),
    AiProvider(
        id = "kimi",
        name = "Kimi",
        subtitle = "月之暗面",
        symbol = "KIMI",
        defaultBaseUrl = "https://api.moonshot.cn/v1",
        models = listOf(
            AiModel(
                id = "kimi-k2.5",
                name = "Kimi-K2.5",
                description = "超长上下文理解与对话探索",
                recommended = true,
                tags = listOf("长上下文", "无损记忆"),
                summary = "超长上下文理解与对话探索",
            ),
            AiModel("kimi-k2-turbo", "Kimi-K2-Turbo", "极速长文本推理模型"),
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
                id = "glm-4.5",
                name = "GLM-4.5",
                description = "新一代通用推理与智能体模型",
                recommended = true,
                tags = listOf("智能体", "工具调用"),
                summary = "新一代通用推理与智能体模型",
            ),
            AiModel("glm-4.5-air", "GLM-4.5-Air", "高性价比轻量级模型"),
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
                id = "minimax-m2.5",
                name = "MiniMax-M2.5",
                description = "自然生动的口语表达与拟人陪伴",
                recommended = true,
                tags = listOf("自然对话", "情感表达"),
                summary = "自然生动的口语表达与拟人陪伴",
            ),
            AiModel("minimax-m2.1", "MiniMax-M2.1", "稳定的通用大模型"),
        ),
    ),
    AiProvider(
        id = "volcengine",
        name = "火山方舟",
        subtitle = "字节跳动",
        symbol = "VC",
        defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        models = listOf(
            AiModel(
                id = "doubao-pro",
                name = "Doubao-Pro",
                description = "高响应速度与强泛化能力模型",
                recommended = true,
                tags = listOf("极速响应", "多场景覆盖"),
                summary = "高响应速度与强泛化能力模型",
            ),
            AiModel("doubao-lite", "Doubao-Lite", "极致成本的高速轻量模型"),
        ),
    ),
    AiProvider(
        id = "tencent",
        name = "腾讯云",
        subtitle = "混元大模型",
        symbol = "TC",
        defaultBaseUrl = "https://api.hunyuan.cloud.tencent.com/v1",
        models = listOf(
            AiModel(
                id = "hunyuan-large",
                name = "Hunyuan-Large",
                description = "腾讯自研大规模 MoE 混合专家模型",
                recommended = true,
                tags = listOf("MoE架构", "中文理解"),
                summary = "腾讯自研大规模 MoE 混合专家模型",
            ),
            AiModel("hunyuan-standard", "Hunyuan-Standard", "高稳定日常通用模型"),
        ),
    ),
    AiProvider(
        id = "bailian",
        name = "阿里云百炼",
        subtitle = "阿里云百炼平台",
        symbol = "BL",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        models = listOf(
            AiModel(
                id = "qwen-plus",
                name = "Qwen-Plus",
                description = "能力全面升级的中等规模大模型",
                recommended = true,
                tags = listOf("企业级", "稳定调用"),
                summary = "能力全面升级的中等规模大模型",
            ),
            AiModel("qwen-turbo", "Qwen-Turbo", "百炼高并发秒级响应模型"),
        ),
    ),
    AiProvider("custom", "更多", "OpenAI Compatible / 自定义", "+", "", emptyList()),
)
