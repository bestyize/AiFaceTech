package com.aifacemate.face.voice.realtime.provider.qwen

/** Late frames from a cancelled response must never terminate or play over the next turn. */
internal class QwenResponseTracker {
    private var activeId: String? = null

    fun start(id: String) { activeId = id.takeIf { it.isNotBlank() } }

    fun accepts(id: String): Boolean = activeId != null && activeId == id

    /** Returns whether a response was active before interruption. */
    fun interrupt(): Boolean = (activeId != null).also { activeId = null }

    /** Returns true only for the response that is currently being played. */
    fun finish(id: String): Boolean {
        if (!accepts(id)) return false
        activeId = null
        return true
    }
}
