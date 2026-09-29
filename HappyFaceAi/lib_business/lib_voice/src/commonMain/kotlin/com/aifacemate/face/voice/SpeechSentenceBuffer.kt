package com.aifacemate.face.voice

/** Incremental sentence/phrase boundaries; a short first phrase lowers time to first audio. */
class SpeechSentenceBuffer {
    private val pending = StringBuilder()
    private var first = true

    fun append(delta: String): List<String> {
        pending.append(delta)
        return extract(finishing = false)
    }

    fun finish(): List<String> = extract(finishing = true)

    private fun extract(finishing: Boolean): List<String> = buildList {
        while (pending.isNotEmpty()) {
            val limit = if (first) 32 else 80
            var cut = 0
            for (index in pending.indices) {
                val char = pending[index]
                val next = pending.getOrNull(index + 1)
                val previous = pending.getOrNull(index - 1)
                val strong = char in "。！？!?；;\n"
                val period = char == '.' && !(previous?.isDigit() == true && next?.isDigit() == true) &&
                    (next?.isWhitespace() == true || next?.code?.let { it >= 0x3000 } == true || (finishing && next == null))
                val phrase = char in "，,：:" && index + 1 >= if (first) 12 else 48
                if (strong || period || phrase) {
                    cut = index + 1
                    while (cut < pending.length && pending[cut] in "”’\"')）】") cut++
                    break
                }
                if (index + 1 >= limit) {
                    // Keep English words intact where possible, and never split a surrogate pair.
                    cut = if (char.isHighSurrogate()) index else index + 1
                    val space = pending.substring(0, cut).indexOfLast { it.isWhitespace() }
                    if (space >= limit / 2) cut = space + 1
                    if (cut > 0) break
                }
            }
            if (cut == 0) {
                if (!finishing) break
                cut = pending.length
            }
            val sentence = pending.substring(0, cut).trim()
            pending.deleteRange(0, cut)
            if (sentence.any { it.isLetterOrDigit() }) { add(sentence); first = false }
        }
    }
}
