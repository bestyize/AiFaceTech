package com.aifacemate.face.feature.aibrain

/** 从供应商 `/models` 接口拉到的一条模型记录。 */
data class RemoteModel(val id: String, val ownedBy: String? = null)

sealed interface ModelFetchResult {
    data class Success(val models: List<RemoteModel>) : ModelFetchResult
    data class Failure(val message: String) : ModelFetchResult
}

/**
 * 拉取供应商的模型列表。
 *
 * 走 OpenAI Compatible 的 `GET {baseUrl}/models`，需要 `Authorization: Bearer <apiKey>`。
 * 平台实现：Android / Desktop(JVM) 用 `HttpURLConnection`，iOS 尚未接入。
 */
expect suspend fun fetchProviderModels(baseUrl: String, apiKey: String): ModelFetchResult

/**
 * 把用户填写的 Base URL 规范成 `/models` 端点。
 *
 * - `https://api.deepseek.com/v1` → `https://api.deepseek.com/v1/models`
 * - `https://api.deepseek.com/v1/` → 同上
 * - `https://my.host` → `https://my.host/v1/models`
 * - `https://my.host/v1/models` → 原样
 */
internal fun modelsEndpoint(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    return when {
        trimmed.isEmpty() -> ""
        trimmed.endsWith("/models") -> trimmed
        Regex("/v\\d+$").containsMatchIn(trimmed) -> "$trimmed/models"
        else -> "$trimmed/v1/models"
    }
}

/**
 * 解析 `/models` 的响应体，取出 `data[].id`。
 *
 * 只读取 OpenAI Compatible 规定的结构，解析失败时返回空列表，由调用方给出错误提示。
 */
internal fun parseModelIds(body: String): List<RemoteModel> {
    val root = JsonReader(body).parse() as? JsonValue.Obj ?: return emptyList()
    val data = root.entries["data"] as? JsonValue.Arr ?: return emptyList()
    return data.items.mapNotNull { item ->
        val entry = item as? JsonValue.Obj ?: return@mapNotNull null
        val id = (entry.entries["id"] as? JsonValue.Str)?.value ?: return@mapNotNull null
        RemoteModel(id = id, ownedBy = (entry.entries["owned_by"] as? JsonValue.Str)?.value)
    }
}

/** 把 HTTP 失败转成给用户看的中文提示，优先使用服务端返回的 message。 */
internal fun httpErrorMessage(code: Int, body: String): String {
    val serverMessage = parseErrorMessage(body)
    return when {
        serverMessage != null -> "请求失败（HTTP $code）：$serverMessage"
        code == 401 || code == 403 -> "API Key 无效或没有权限（HTTP $code）"
        code == 404 -> "没找到 /models 接口（HTTP 404），请检查 API Base URL"
        code == 429 -> "请求过于频繁（HTTP 429），稍后再试"
        else -> "请求失败（HTTP $code）"
    }
}

private fun parseErrorMessage(body: String): String? {
    val root = JsonReader(body).parse() as? JsonValue.Obj ?: return null
    val fromError = when (val error = root.entries["error"]) {
        is JsonValue.Obj -> (error.entries["message"] as? JsonValue.Str)?.value
        is JsonValue.Str -> error.value
        else -> null
    }
    return fromError ?: (root.entries["message"] as? JsonValue.Str)?.value
}

/**
 * 模型能力标签。
 *
 * 供应商的 `/models` 接口不会返回模态信息，所以这里只从模型 ID 的命名约定推断，
 * 且标签只会是「文本 / 图片 / 视频 / 音频」。判断不出来时返回空列表，不猜。
 */
object ModelCapabilities {
    /** 内置目录里人工核对过的模型。 */
    private val curated: Map<String, List<String>> = mapOf(
        "deepseek-v4-pro" to listOf(TEXT),
        "deepseek-flash" to listOf(TEXT),
        "deepseek-v3" to listOf(TEXT),
        "glm-4.5" to listOf(TEXT),
        "glm-4.5-air" to listOf(TEXT),
        "kimi-k2.5" to listOf(TEXT),
        "kimi-k2-turbo" to listOf(TEXT),
        "minimax-m2.5" to listOf(TEXT),
        "minimax-m2.1" to listOf(TEXT),
    )

    const val TEXT = "文本"
    const val IMAGE = "图片"
    const val VIDEO = "视频"
    const val AUDIO = "音频"

    /** 纯生成类模型，只出对应模态。 */
    private val imageGenMarkers = listOf("dall-e", "imagen", "flux", "stable-diffusion", "sd3", "sd-xl", "kolors")
    private val videoGenMarkers = listOf("video", "sora", "veo", "kling", "cogvideo", "wan2")
    private val audioOnlyMarkers = listOf("tts", "whisper", "asr", "speech", "voice-clone")

    /** 可以同时理解文本与其它模态的模型。 */
    private val visionMarkers = listOf("vl", "vision", "-4v", "4v-", "omni", "multimodal")
    private val audioChatMarkers = listOf("realtime", "audio")

    /** 已知的对话模型命名前缀，命中才认为支持文本。 */
    private val textPrefixes = listOf(
        "deepseek", "gpt", "chatgpt", "o1", "o3", "o4", "claude", "glm", "chatglm",
        "kimi", "moonshot", "abab", "minimax", "qwen", "ernie", "hunyuan", "spark",
        "doubao", "yi-", "gemini", "llama", "mistral", "command-r", "grok",
    )

    fun of(modelId: String): List<String> {
        curated[modelId]?.let { return it }

        val id = modelId.lowercase()
        if (videoGenMarkers.any { id.contains(it) }) return listOf(VIDEO)
        if (audioOnlyMarkers.any { id.contains(it) }) return listOf(AUDIO)
        if (imageGenMarkers.any { id.contains(it) }) return listOf(IMAGE)

        val tags = mutableListOf<String>()
        if (textPrefixes.any { id.startsWith(it) }) tags += TEXT
        if (visionMarkers.any { id.contains(it) } && IMAGE !in tags) tags += IMAGE
        if (audioChatMarkers.any { id.contains(it) }) tags += AUDIO

        // 只识别出「图片/音频」而没有任何文本线索时，仍按多模态对话模型处理
        if (tags.isEmpty()) return emptyList()
        if (TEXT !in tags && (IMAGE in tags || AUDIO in tags)) tags.add(0, TEXT)
        return tags
    }
}

// ---------------------------------------------------------------- 极简 JSON 解析

internal sealed interface JsonValue {
    data class Obj(val entries: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val raw: String) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

/** 只解析 `/models` 响应需要的那点 JSON，不引入额外依赖。 */
internal class JsonReader(private val text: String) {
    private var pos = 0

    fun parse(): JsonValue? {
        skipWhitespace()
        val value = readValue() ?: return null
        skipWhitespace()
        return value
    }

    private fun readValue(): JsonValue? {
        skipWhitespace()
        if (pos >= text.length) return null
        return when (val c = text[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()?.let { JsonValue.Str(it) }
            't' -> if (readLiteral("true")) JsonValue.Bool(true) else null
            'f' -> if (readLiteral("false")) JsonValue.Bool(false) else null
            'n' -> if (readLiteral("null")) JsonValue.Null else null
            else -> if (c == '-' || c.isDigit()) readNumber() else null
        }
    }

    private fun readObject(): JsonValue? {
        if (!consume('{')) return null
        val entries = mutableMapOf<String, JsonValue>()
        skipWhitespace()
        if (consume('}')) return JsonValue.Obj(entries)
        while (true) {
            skipWhitespace()
            val key = readString() ?: return null
            skipWhitespace()
            if (!consume(':')) return null
            val value = readValue() ?: return null
            entries[key] = value
            skipWhitespace()
            when {
                consume(',') -> continue
                consume('}') -> return JsonValue.Obj(entries)
                else -> return null
            }
        }
    }

    private fun readArray(): JsonValue? {
        if (!consume('[')) return null
        val items = mutableListOf<JsonValue>()
        skipWhitespace()
        if (consume(']')) return JsonValue.Arr(items)
        while (true) {
            val value = readValue() ?: return null
            items += value
            skipWhitespace()
            when {
                consume(',') -> continue
                consume(']') -> return JsonValue.Arr(items)
                else -> return null
            }
        }
    }

    private fun readString(): String? {
        if (!consume('"')) return null
        val builder = StringBuilder()
        while (pos < text.length) {
            val c = text[pos++]
            when {
                c == '"' -> return builder.toString()
                c == '\\' -> {
                    if (pos >= text.length) return null
                    when (val escaped = text[pos++]) {
                        '"', '\\', '/' -> builder.append(escaped)
                        'b' -> builder.append('\b')
                        'f' -> builder.append('\u000C')
                        'n' -> builder.append('\n')
                        'r' -> builder.append('\r')
                        't' -> builder.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) return null
                            val hex = text.substring(pos, pos + 4)
                            val code = hex.toIntOrNull(16) ?: return null
                            builder.append(code.toChar())
                            pos += 4
                        }
                        else -> return null
                    }
                }
                else -> builder.append(c)
            }
        }
        return null
    }

    private fun readNumber(): JsonValue.Num? {
        val start = pos
        while (pos < text.length && (text[pos].isDigit() || text[pos] in "-+.eE")) pos++
        if (pos == start) return null
        return JsonValue.Num(text.substring(start, pos))
    }

    private fun readLiteral(literal: String): Boolean {
        if (!text.startsWith(literal, pos)) return false
        pos += literal.length
        return true
    }

    private fun consume(expected: Char): Boolean {
        skipWhitespace()
        if (pos < text.length && text[pos] == expected) {
            pos++
            return true
        }
        return false
    }

    private fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }
}
