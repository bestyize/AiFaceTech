package com.aifacemate.face.feature.aibrain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class AiModel(
    val id: String,
    val name: String,
    val description: String,
    val recommended: Boolean = false,
    val tags: List<String> = emptyList(),
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

enum class ThinkingMode(val title: String, val description: String, val symbol: String) {
    Fast("快速", "更快响应 · 简单问答", "⚡"),
    Balanced("平衡", "效果与速度兼顾", "⚖"),
    Deep("深度", "更深入思考 · 复杂任务", "◉"),
}

enum class AiCapability(val title: String, val description: String, val symbol: String) {
    WebSearch("联网搜索", "获取最新的网络信息", "◎"),
    LongTermMemory("长期记忆", "记住重要的对话内容", "▤"),
    ToolCalling("工具调用", "使用各种实用工具", "◆"),
    Schedule("日程与提醒", "管理日程、设置提醒", "□"),
}

enum class ConnectionStatus { Connected, NotConfigured, Invalid }

data class AiBrainUiState(
    val providers: List<AiProvider> = defaultAiProviders,
    val config: BrainConfig = BrainConfig(),
    val thinkingMode: ThinkingMode = ThinkingMode.Balanced,
    val capabilities: Map<AiCapability, Boolean> = AiCapability.entries.associateWith { true },
    val connectionStatus: ConnectionStatus = ConnectionStatus.Connected,
    val statusMessage: String = "当前大脑已连接",
) {
    val selectedProvider: AiProvider
        get() = providers.firstOrNull { it.id == config.providerId } ?: providers.first()

    val selectedModel: AiModel
        get() = selectedProvider.models.firstOrNull { it.id == config.modelId } ?: selectedProvider.models.first()

    fun selectProvider(providerId: String): AiBrainUiState {
        val provider = providers.firstOrNull { it.id == providerId } ?: return this
        val model = provider.models.firstOrNull { it.recommended } ?: provider.models.first()
        return copy(
            config = config.copy(
                providerId = provider.id,
                modelId = model.id,
                baseUrl = provider.defaultBaseUrl,
            ),
            connectionStatus = if (provider.id == "custom") ConnectionStatus.NotConfigured else ConnectionStatus.Connected,
            statusMessage = if (provider.id == "custom") "请填写自己的 AI 服务" else "已切换到 ${provider.name}",
        )
    }

    fun selectModel(modelId: String): AiBrainUiState {
        val model = selectedProvider.models.firstOrNull { it.id == modelId } ?: return this
        return copy(
            config = config.copy(modelId = model.id),
            statusMessage = "已选择 ${model.name}",
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
    fun updateModel(value: String) { uiState = uiState.copy(config = uiState.config.copy(modelId = value)) }

    /** 当前 Alpha 只做必填项格式校验；真实网络探测由后续服务层接入。 */
    fun saveAndValidate() {
        val valid = uiState.config.baseUrl.startsWith("http") && uiState.config.modelId.isNotBlank()
        uiState = uiState.copy(
            connectionStatus = if (valid) ConnectionStatus.Connected else ConnectionStatus.Invalid,
            statusMessage = if (valid) "配置已保存，本地格式校验通过" else "请填写有效的 API 地址和模型名称",
        )
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
            AiModel("deepseek-v4-pro", "DeepSeek-V4-Pro", "最强大的推理与创作能力", true, listOf("深度思考", "中文友好", "高性价比")),
            AiModel("deepseek-flash", "DeepSeek-Flash", "更快的响应速度，适合日常对话"),
            AiModel("deepseek-v3", "DeepSeek-V3", "稳定可靠，适合通用场景"),
            AiModel("custom", "自定义", "使用自定义模型名称接入"),
        ),
    ),
    AiProvider(
        id = "zhipu",
        name = "智谱",
        subtitle = "智谱 AI",
        symbol = "ZP",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        models = listOf(
            AiModel("glm-4.5", "GLM-4.5", "通用推理与工具使用", true, listOf("中文友好", "工具调用")),
            AiModel("glm-4.5-air", "GLM-4.5-Air", "轻量、快速的日常模型"),
            AiModel("custom", "自定义", "使用自定义模型名称接入"),
        ),
    ),
    AiProvider(
        id = "kimi",
        name = "Kimi",
        subtitle = "月之暗面",
        symbol = "K",
        defaultBaseUrl = "https://api.moonshot.cn/v1",
        models = listOf(
            AiModel("kimi-k2.5", "Kimi-K2.5", "长文本理解与复杂任务", true, listOf("长上下文", "中文友好")),
            AiModel("kimi-k2-turbo", "Kimi-K2-Turbo", "更快速的长文本对话"),
            AiModel("custom", "自定义", "使用自定义模型名称接入"),
        ),
    ),
    AiProvider(
        id = "minimax",
        name = "MiniMax",
        subtitle = "MiniMax",
        symbol = "MM",
        defaultBaseUrl = "https://api.minimax.chat/v1",
        models = listOf(
            AiModel("minimax-m2.5", "MiniMax-M2.5", "综合能力与自然表达", true, listOf("自然对话", "高性价比")),
            AiModel("minimax-m2.1", "MiniMax-M2.1", "稳定的通用模型"),
            AiModel("custom", "自定义", "使用自定义模型名称接入"),
        ),
    ),
    AiProvider(
        id = "custom",
        name = "自定义",
        subtitle = "OpenAI Compatible",
        symbol = "+",
        defaultBaseUrl = "",
        models = listOf(AiModel("custom", "自定义", "通过 OpenAI Compatible 格式接入", true, listOf("私有服务"))),
    ),
)
