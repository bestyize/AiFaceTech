package com.aifacemate.face.tools

import com.aifacemate.face.voice.realtime.RealtimeConfig
import java.util.UUID

internal fun realtimeProviderConfig(provider: String): RealtimeConfig = when (provider.lowercase()) {
    "doubao" -> RealtimeConfig.Doubao(
        appId = credential("DOUBAO_APP_ID"),
        accessKey = credential("DOUBAO_ACCESS_KEY"),
        appKey = credential("DOUBAO_APP_KEY"),
        sessionId = UUID.randomUUID().toString(),
    )
    "qwen" -> RealtimeConfig.Qwen(
        apiKey = credential("DASHSCOPE_API_KEY"),
        workspaceEndpoint = credential("QWEN_WORKSPACE_ENDPOINT"),
    )
    else -> error("Provider must be doubao or qwen")
}

private fun credential(name: String): String = System.getenv(name)?.takeIf { it.isNotBlank() }
    ?: error("Missing environment variable $name")
