package com.aifacemate.face.feature.aibrain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

/**
 * Android 与 Desktop(JVM) 共用的实现：OpenAI Compatible 的 `GET /models`。
 *
 * 时间上限制在 15s 连接 / 20s 读取，失败时返回可直接展示的中文提示。
 */
actual suspend fun fetchProviderModels(baseUrl: String, apiKey: String): ModelFetchResult =
    withContext(Dispatchers.IO) {
        val endpoint = modelsEndpoint(baseUrl)
        if (endpoint.isEmpty()) {
            return@withContext ModelFetchResult.Failure("请先填写 API Base URL")
        }
        if (apiKey.isBlank()) {
            return@withContext ModelFetchResult.Failure("请先填写 API Key")
        }

        try {
            val connection = (URI.create(endpoint).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()

                when {
                    code !in 200..299 -> ModelFetchResult.Failure(httpErrorMessage(code, body))
                    else -> {
                        val models = parseModelIds(body)
                        if (models.isEmpty()) {
                            ModelFetchResult.Failure("接口返回成功，但没有解析到模型列表")
                        } else {
                            ModelFetchResult.Success(models)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            ModelFetchResult.Failure("无法连接模型服务：${error.message ?: "网络错误"}")
        }
    }
