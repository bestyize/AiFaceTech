package com.aifacemate.face.voice.realtime.provider.doubao

/** Volcengine Realtime Dialogue v1 frame: 4-byte header, event, optional session ID, size, payload. */
internal object DoubaoProtocol {
    data class Message(val event: Int, val payload: ByteArray, val errorCode: Int? = null)

    fun encode(event: Int, payload: ByteArray, sessionId: String? = null, audio: Boolean = false): ByteArray {
        val id = sessionId?.encodeToByteArray() ?: byteArrayOf()
        val frame = ByteArray(4 + 4 + (if (sessionId != null) 4 + id.size else 0) + 4 + payload.size)
        frame[0] = 0x11
        frame[1] = if (audio) 0x24 else 0x14
        frame[2] = if (audio) 0 else 0x10
        var offset = 4
        offset = writeInt(frame, offset, event)
        if (sessionId != null) {
            offset = writeInt(frame, offset, id.size)
            id.copyInto(frame, offset)
            offset += id.size
        }
        offset = writeInt(frame, offset, payload.size)
        payload.copyInto(frame, offset)
        return frame
    }

    fun decode(frame: ByteArray): Message {
        require(frame.size >= 12 && (frame[0].toInt() and 0xff) == 0x11) { "Invalid Doubao frame header" }
        val kind = (frame[1].toInt() ushr 4) and 0xf
        val flags = frame[1].toInt() and 0xf
        require(kind == 9 || kind == 11 || kind == 15) { "Unexpected Doubao frame type" }
        require((frame[2].toInt() and 0xf) == 0) { "Compressed Doubao frame unsupported" }
        var offset = 4
        val code = if (kind == 15) readInt(frame, offset).also { offset += 4 } else null
        if (flags and 3 == 1 || flags and 3 == 3) offset += 4 // sequence
        val event = if (flags and 4 != 0) readInt(frame, offset).also { offset += 4 } else -1
        // Session events (100 and above) include a length-prefixed session ID.
        if (event >= 100) {
            val idSize = readInt(frame, offset)
            require(idSize in 0..256 && offset + 4 + idSize <= frame.size) { "Invalid Doubao session ID" }
            offset += 4 + idSize
        }
        val size = readInt(frame, offset)
        offset += 4
        require(size >= 0 && size <= frame.size - offset) { "Invalid Doubao payload length" }
        return Message(event, frame.copyOfRange(offset, offset + size), code)
    }

    private fun readInt(data: ByteArray, pos: Int): Int {
        require(pos >= 0 && pos + 4 <= data.size) { "Truncated Doubao frame" }
        return ((data[pos].toInt() and 255) shl 24) or ((data[pos + 1].toInt() and 255) shl 16) or
            ((data[pos + 2].toInt() and 255) shl 8) or (data[pos + 3].toInt() and 255)
    }

    private fun writeInt(data: ByteArray, pos: Int, number: Int): Int {
        data[pos] = (number ushr 24).toByte()
        data[pos + 1] = (number ushr 16).toByte()
        data[pos + 2] = (number ushr 8).toByte()
        data[pos + 3] = number.toByte()
        return pos + 4
    }
}
