package com.aifacemate.face

import com.aifacemate.face.feature.aibrain.ModelCapabilities
import com.aifacemate.face.feature.aibrain.httpErrorMessage
import com.aifacemate.face.feature.aibrain.modelsEndpoint
import com.aifacemate.face.feature.aibrain.parseModelIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelParsingTest {
    @Test
    fun modelsEndpointNormalisesCommonBaseUrls() {
        assertEquals("https://api.deepseek.com/v1/models", modelsEndpoint("https://api.deepseek.com/v1"))
        assertEquals("https://api.deepseek.com/v1/models", modelsEndpoint("https://api.deepseek.com/v1/"))
        assertEquals("https://my.host/v1/models", modelsEndpoint("https://my.host"))
        assertEquals("https://my.host/v1/models", modelsEndpoint("https://my.host/v1/models"))
        assertEquals("", modelsEndpoint("   "))
    }

    @Test
    fun parseModelIdsReadsOpenAiCompatiblePayload() {
        val body = """
            {
              "object": "list",
              "data": [
                {"id": "deepseek-chat", "object": "model", "owned_by": "deepseek"},
                {"id": "deepseek-reasoner", "object": "model", "owned_by": "deepseek"},
                {"id": "weird\"name", "object": "model"}
              ]
            }
        """.trimIndent()
        val models = parseModelIds(body)
        assertEquals(listOf("deepseek-chat", "deepseek-reasoner", "weird\"name"), models.map { it.id })
        assertEquals("deepseek", models.first().ownedBy)
    }

    @Test
    fun parseModelIdsReturnsEmptyForErrorPayload() {
        val body = """{"error": {"message": "Invalid API key", "type": "authentication_error"}}"""
        assertTrue(parseModelIds(body).isEmpty())
    }

    @Test
    fun httpErrorMessagePrefersServerMessage() {
        val message = httpErrorMessage(401, """{"error": {"message": "Invalid API key"}}""")
        assertTrue(message.contains("Invalid API key"), "应优先展示服务端返回的 message，实际为 $message")
        assertTrue(httpErrorMessage(404, "").contains("404"))
    }

    @Test
    fun capabilityTagsOnlyUseTheFourSupportedModalities() {
        val allowed = setOf(
            ModelCapabilities.TEXT,
            ModelCapabilities.IMAGE,
            ModelCapabilities.VIDEO,
            ModelCapabilities.AUDIO,
        )
        val samples = listOf(
            "deepseek-v4-pro", "deepseek-chat", "glm-4.5", "glm-4v", "kimi-k2.5",
            "qwen-vl-max", "gpt-4o-audio-preview", "tts-1", "dall-e-3", "sora-video",
            "some-unknown-model-x", "bge-m3",
        )
        samples.forEach { id ->
            val tags = ModelCapabilities.of(id)
            assertTrue(tags.all { it in allowed }, "$id 出现了非法标签：$tags")
            assertTrue(tags.distinct().size == tags.size, "$id 的标签重复：$tags")
        }
    }

    @Test
    fun capabilityTagsAreNotGuessedForUnknownModels() {
        assertTrue(
            ModelCapabilities.of("some-unknown-model-x").isEmpty(),
            "判断不出能力的模型不应该贴标签",
        )
        assertEquals(listOf(ModelCapabilities.TEXT), ModelCapabilities.of("deepseek-chat"))
        assertEquals(listOf(ModelCapabilities.TEXT, ModelCapabilities.IMAGE), ModelCapabilities.of("glm-4v"))
        assertEquals(listOf(ModelCapabilities.AUDIO), ModelCapabilities.of("tts-1"))
    }
}
