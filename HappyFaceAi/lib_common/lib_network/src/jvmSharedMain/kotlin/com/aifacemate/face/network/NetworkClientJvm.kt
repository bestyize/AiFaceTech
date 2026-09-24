package com.aifacemate.face.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

actual class NetworkClient {
    actual suspend fun get(url: String, headers: Map<String, String>): NetworkResponse = withContext(Dispatchers.IO) {
        execute(url, "GET", headers, ByteArray(0))
    }

    actual suspend fun postJson(url: String, headers: Map<String, String>, body: String): NetworkResponse = withContext(Dispatchers.IO) {
        execute(url, "POST", headers + ("Content-Type" to "application/json"), body.toByteArray())
    }

    actual suspend fun postMultipart(url: String, headers: Map<String, String>, parts: List<MultipartPart>): NetworkResponse = withContext(Dispatchers.IO) {
        val boundary = "----HappyFaceAiBoundary"
        val payload = buildMultipart(boundary, parts)
        execute(url, "POST", headers + ("Content-Type" to "multipart/form-data; boundary=$boundary"), payload)
    }

    private fun execute(url: String, method: String, headers: Map<String, String>, body: ByteArray): NetworkResponse {
        val connection = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method; doOutput = method != "GET"; connectTimeout = 15_000; readTimeout = 60_000
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        return try {
            if (method != "GET") connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            NetworkResponse(code, connection.contentType, bytes)
        } finally { connection.disconnect() }
    }

    private fun buildMultipart(boundary: String, parts: List<MultipartPart>): ByteArray = buildList {
        parts.forEach { part ->
            add("--$boundary\r\n".toByteArray())
            if (part.fileName == null) add("Content-Disposition: form-data; name=\"${part.name}\"\r\n\r\n${part.value.orEmpty()}\r\n".toByteArray())
            else {
                add("Content-Disposition: form-data; name=\"${part.name}\"; filename=\"${part.fileName}\"\r\nContent-Type: ${part.mimeType ?: "application/octet-stream"}\r\n\r\n".toByteArray())
                add(part.bytes); add("\r\n".toByteArray())
            }
        }
        add("--$boundary--\r\n".toByteArray())
    }.fold(ByteArray(0)) { acc, next -> acc + next }
}
