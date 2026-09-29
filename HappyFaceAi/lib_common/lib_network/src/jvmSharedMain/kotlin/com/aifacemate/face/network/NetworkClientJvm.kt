package com.aifacemate.face.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URI

actual class NetworkClient {
    actual suspend fun postEventStream(url: String, headers: Map<String, String>, body: String,
        onEvent: suspend (String) -> Boolean): NetworkResponse = withContext(Dispatchers.IO) {
        val connection = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15_000; readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        coroutineScope {
            // Cancellation must close a blocking socket read, including while waiting for a first event.
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { connection.disconnect() }
            }
            try {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val type = connection.contentType
                if (code !in 200..299) {
                    val bytes = connection.errorStream?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var size = 0
                        while (size < buffer.size) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer, size, buffer.size - size)
                            if (count < 0) break
                            size += count
                        }
                        buffer.copyOf(size)
                    } ?: ByteArray(0)
                    return@coroutineScope NetworkResponse(code, type, bytes)
                }
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    if (type?.contains("text/event-stream", ignoreCase = true) == true) {
                        val decoder = SseEventDecoder()
                        var keepReading = true
                        while (keepReading) {
                            currentCoroutineContext().ensureActive()
                            val line = reader.readLine() ?: break
                            decoder.acceptLine(line)?.let { keepReading = onEvent(it) }
                        }
                        if (keepReading) decoder.finish()?.let { onEvent(it) }
                    } else {
                        // Some compatible gateways return a single JSON completion despite stream=true.
                        val text = StringBuilder()
                        val buffer = CharArray(4096)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = reader.read(buffer); if (count < 0) break
                            check(text.length + count <= 8 * 1024 * 1024) { "流式响应过大" }
                            text.append(buffer, 0, count)
                        }
                        onEvent(text.toString())
                    }
                }
                NetworkResponse(code, type)
            } catch (failure: Exception) {
                // Closing a socket during cancellation throws IOException; do not report it as a service error.
                currentCoroutineContext().ensureActive()
                throw failure
            } finally { cancellation.cancel(); connection.disconnect() }
        }
    }

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
