package com.aifacemate.face.network

/** HTTP readers handle UTF-8 and arbitrary packet boundaries; this handles SSE line framing. */
class SseEventDecoder(private val maxEventSize: Int = 8 * 1024 * 1024) {
    private val data = StringBuilder()
    private var hasData = false

    fun acceptLine(raw: String): String? {
        val line = raw.removePrefix("\uFEFF")
        if (line.isEmpty()) return finish()
        if (line == "data" || line.startsWith("data:")) {
            val value = line.substringAfter(':', "").removePrefix(" ")
            check(data.length + value.length + 1 <= maxEventSize) { "流式响应事件过大" }
            if (hasData) data.append('\n')
            data.append(value)
            hasData = true
        }
        return null
    }

    fun finish(): String? {
        val event = if (hasData) data.toString() else null
        data.clear(); hasData = false
        return event
    }
}
