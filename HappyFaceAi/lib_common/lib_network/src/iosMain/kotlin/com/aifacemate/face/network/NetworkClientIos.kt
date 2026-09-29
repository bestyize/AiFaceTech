package com.aifacemate.face.network

actual class NetworkClient {
    actual suspend fun postEventStream(url: String, headers: Map<String, String>, body: String,
        onEvent: suspend (String) -> Boolean): NetworkResponse = NetworkResponse(501, text = "iOS 网络实现尚未接入")
    actual suspend fun get(url: String, headers: Map<String, String>): NetworkResponse = NetworkResponse(501, text = "iOS 网络实现尚未接入")
    actual suspend fun postJson(url: String, headers: Map<String, String>, body: String): NetworkResponse = NetworkResponse(501, text = "iOS 网络实现尚未接入")
    actual suspend fun postMultipart(url: String, headers: Map<String, String>, parts: List<MultipartPart>): NetworkResponse = NetworkResponse(501, text = "iOS 网络实现尚未接入")
}
