package com.aifacemate.face.voice.asr.model

sealed interface AsrResult {
    data class Success(val text: String, val language: String? = null, val durationMs: Long? = null) : AsrResult
    data class Failure(val message: String, val httpCode: Int? = null) : AsrResult
}
