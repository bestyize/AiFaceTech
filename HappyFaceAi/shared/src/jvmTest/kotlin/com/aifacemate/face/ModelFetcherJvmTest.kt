package com.aifacemate.face

import com.aifacemate.face.feature.aibrain.ModelFetchResult
import com.aifacemate.face.feature.aibrain.fetchProviderModels
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 用本地 HTTP 服务验证「获取模型列表」的真实网络路径，
 * 覆盖 Authorization 头、成功解析与错误提示三条分支。
 */
class ModelFetcherJvmTest {

    private class StubServer(status: Int, body: String) {
        val lastAuthHeader = Array(1) { "" }
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/v1"

        init {
            server.createContext("/v1/models") { exchange: HttpExchange ->
                lastAuthHeader[0] = exchange.requestHeaders.getFirst("Authorization").orEmpty()
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun stop() = server.stop(0)
    }

    @Test
    fun fetchesModelsFromOpenAiCompatibleEndpoint() = runBlocking {
        val stub = StubServer(
            200,
            """{"object":"list","data":[{"id":"deepseek-chat","owned_by":"deepseek"},{"id":"deepseek-reasoner","owned_by":"deepseek"}]}""",
        )
        try {
            val result = fetchProviderModels(stub.baseUrl, "sk-test-key")
            assertEquals("Bearer sk-test-key", stub.lastAuthHeader[0])
            assertTrue(result is ModelFetchResult.Success, "应当成功返回模型列表，实际为 $result")
            assertEquals(
                listOf("deepseek-chat", "deepseek-reasoner"),
                (result as ModelFetchResult.Success).models.map { it.id },
            )
        } finally {
            stub.stop()
        }
    }

    @Test
    fun reportsHttpErrorWithServerMessage() = runBlocking {
        val stub = StubServer(401, """{"error":{"message":"Authentication Fails"}}""")
        try {
            val result = fetchProviderModels(stub.baseUrl, "sk-wrong")
            assertTrue(result is ModelFetchResult.Failure, "401 应当是失败结果，实际为 $result")
            val message = (result as ModelFetchResult.Failure).message
            assertTrue(message.contains("401"), "提示里应带状态码，实际为 $message")
            assertTrue(message.contains("Authentication Fails"), "提示里应带服务端 message，实际为 $message")
        } finally {
            stub.stop()
        }
    }

    @Test
    fun missingApiKeyIsRejectedBeforeSendingAnyRequest() = runBlocking {
        val stub = StubServer(200, """{"data":[]}""")
        try {
            val result = fetchProviderModels(stub.baseUrl, "   ")
            assertTrue(result is ModelFetchResult.Failure, "没有 API Key 时不应发请求，实际为 $result")
            assertEquals("", stub.lastAuthHeader[0], "没有 API Key 时不应该发出请求")
        } finally {
            stub.stop()
        }
    }
}
