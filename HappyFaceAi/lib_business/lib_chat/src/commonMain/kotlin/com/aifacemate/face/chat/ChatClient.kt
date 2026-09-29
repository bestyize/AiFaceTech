package com.aifacemate.face.chat

import com.aifacemate.face.network.NetworkClient
import kotlinx.serialization.json.*

/** OpenAI Compatible 文本对话消息。 */
data class ChatMessage(val role: String, val content: String)
data class ChatConfig(val baseUrl: String, val apiKey: String, val model: String)
sealed interface ChatResult {
    data class Success(val message: ChatMessage) : ChatResult
    data class Failure(val message: String, val httpCode: Int? = null) : ChatResult
}

/** 只负责对话协议，网络实现由 lib_common:lib_network 提供。 */
class ChatClient(private val network: NetworkClient = NetworkClient()) {
    suspend fun stream(config: ChatConfig, messages: List<ChatMessage>, onDelta: suspend (String) -> Unit): ChatResult {
        if (config.apiKey.isBlank()) return ChatResult.Failure("请先在 AI 大脑中配置 API Key")
        if (config.model.isBlank()) return ChatResult.Failure("请先选择对话模型")
        if (messages.isEmpty()) return ChatResult.Failure("对话内容为空")
        val endpoint = chatEndpoint(config.baseUrl)
        if (endpoint.isEmpty()) return ChatResult.Failure("请先配置 API Base URL")
        val body = buildJsonObject {
            put("model", config.model); put("stream", true)
            putJsonArray("messages") { messages.forEach { message ->
                add(buildJsonObject { put("role", message.role); put("content", message.content) })
            } }
        }.toString()
        val text = StringBuilder()
        var completed = false
        val response = network.postEventStream(endpoint, mapOf("Authorization" to "Bearer ${config.apiKey.trim()}"), body) { event ->
            val chunk = decodeChatStreamEvent(event)
            if (chunk.text.isNotEmpty()) { text.append(chunk.text); onDelta(chunk.text) }
            if (chunk.finished) completed = true
            !chunk.finished
        }
        if (response.statusCode !in 200..299) return ChatResult.Failure("对话请求失败（HTTP ${response.statusCode}）", response.statusCode)
        if (!completed) return ChatResult.Failure("大脑连接提前断开，已生成的文字会保留")
        return if (text.isBlank()) ChatResult.Failure("大脑没有返回可朗读的文字")
            else ChatResult.Success(ChatMessage("assistant", text.toString()))
    }

    suspend fun send(config: ChatConfig, messages: List<ChatMessage>): ChatResult {
        if (config.apiKey.isBlank()) return ChatResult.Failure("请先在 AI 大脑中配置 API Key")
        if (config.model.isBlank()) return ChatResult.Failure("请先选择对话模型")
        if (messages.isEmpty()) return ChatResult.Failure("对话内容为空")
        val endpoint = chatEndpoint(config.baseUrl)
        if (endpoint.isEmpty()) return ChatResult.Failure("请先配置 API Base URL")
        val body = "{\"model\":\"${escape(config.model)}\",\"messages\":[${messages.joinToString(",") { "{\"role\":\"${escape(it.role)}\",\"content\":\"${escape(it.content)}\"}" }}]}"
        val response = network.postJson(endpoint, mapOf("Authorization" to "Bearer ${config.apiKey.trim()}"), body)
        if (response.statusCode !in 200..299) return ChatResult.Failure("对话请求失败（HTTP ${response.statusCode}）", response.statusCode)
        val content = Regex("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\"])*)\\\"").find(response.text)?.groupValues?.get(1)?.unescape()
        return if (content.isNullOrBlank()) ChatResult.Failure("对话响应格式无法解析", response.statusCode)
        else ChatResult.Success(ChatMessage("assistant", content))
    }
}

internal data class ChatStreamChunk(val text: String = "", val finished: Boolean = false)

internal fun decodeChatStreamEvent(event: String): ChatStreamChunk {
    if (event.isBlank()) return ChatStreamChunk()
    if (event.trim() == "[DONE]") return ChatStreamChunk(finished = true)
    val root = Json.parseToJsonElement(event).jsonObject
    root["error"]?.takeIf { it != JsonNull }?.let { failure ->
        val message = (failure as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
        error(message ?: "大脑流式请求失败")
    }
    val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return ChatStreamChunk()
    val delta = choice["delta"] as? JsonObject
    val message = choice["message"] as? JsonObject
    // Read answer content only. reasoning_content and tool arguments are never spoken.
    val content = (delta ?: message)?.get("content") as? JsonPrimitive
    val finish = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
    check(finish == null || finish == "stop") { "大脑回复未完整结束（${finish}），已生成的文字会保留" }
    return ChatStreamChunk(content?.contentOrNull.orEmpty(), finish != null || message != null)
}

internal fun chatEndpoint(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    return when {
        trimmed.isEmpty() -> ""
        trimmed.endsWith("/chat/completions") -> trimmed
        trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
        else -> "$trimmed/v1/chat/completions"
    }
}

private fun escape(value: String): String = buildString {
    value.forEach { when (it) { '\\' -> append("\\\\"); '"' -> append("\\\""); '\n' -> append("\\n"); '\r' -> append("\\r"); else -> append(it) } }
}
private fun String.unescape(): String = replace("\\n", "\n").replace("\\r", "\r").replace("\\\"", "\"").replace("\\\\", "\\")
