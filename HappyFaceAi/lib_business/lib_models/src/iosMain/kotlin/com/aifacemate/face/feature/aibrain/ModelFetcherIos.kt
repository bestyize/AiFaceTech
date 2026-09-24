package com.aifacemate.face.feature.aibrain

/**
 * iOS 端尚未接入模型列表拉取。
 *
 * 这里只保证 iOS 目标能编译；真正接入时需要在这里补 NSURLSession 实现。
 */
actual suspend fun fetchProviderModels(baseUrl: String, apiKey: String): ModelFetchResult =
    ModelFetchResult.Failure("iOS 端暂未接入模型列表拉取，请先在 Android 或 Desktop 上使用")
