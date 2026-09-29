package com.aifacemate.face.network

data class NetworkResponse(val statusCode: Int, val contentType: String? = null, val body: ByteArray = ByteArray(0), val text: String = body.decodeToString())
data class MultipartPart(val name: String, val value: String? = null, val fileName: String? = null, val mimeType: String? = null, val bytes: ByteArray = ByteArray(0))

/** 基础网络能力由宿主平台实现，业务模块只依赖这个跨平台契约。 */
expect class NetworkClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): NetworkResponse
    suspend fun postJson(url: String, headers: Map<String, String>, body: String): NetworkResponse
    /** Consume SSE data events as they arrive; returning false closes the response immediately. */
    suspend fun postEventStream(url: String, headers: Map<String, String>, body: String,
        onEvent: suspend (String) -> Boolean): NetworkResponse
    suspend fun postMultipart(url: String, headers: Map<String, String>, parts: List<MultipartPart>): NetworkResponse
}
