package com.aifacemate.face.feature.aibrain

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
