package com.aifacemate.face.voice.realtime.provider.doubao

/** Keeps the remainder of an interrupted TTS turn out of the next playback queue. */
internal class DoubaoOutputGate {
    private var outputInProgress = false
    private var suppressUntilEnd = false

    fun sentenceStarted(): Boolean {
        outputInProgress = true
        return !suppressUntilEnd
    }

    fun accepts(): Boolean = !suppressUntilEnd

    fun interrupt() { if (outputInProgress) suppressUntilEnd = true }

    fun ended(): Boolean {
        outputInProgress = false
        return (!suppressUntilEnd).also { suppressUntilEnd = false }
    }
}
