package com.aifacemate.face.voice.realtime.provider.qwen

import com.aifacemate.face.voice.realtime.RealtimeEvent
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Pure event mapping, independent of the WebSocket and audio hardware. */
internal object QwenEventDecoder {
    fun input(data: JsonObject): RealtimeEvent? = when (value(data, "type")) {
        "conversation.item.input_audio_transcription.delta" ->
            RealtimeEvent.InputText(value(data, "text") + value(data, "stash"), false)
        "conversation.item.input_audio_transcription.completed" ->
            RealtimeEvent.InputText(value(data, "transcript"), true)
        "conversation.item.input_audio_transcription.failed" ->
            RealtimeEvent.TranscriptionFailed(
                data["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Input transcription failed"
            )
        else -> null
    }

    fun outputAudio(data: JsonObject): RealtimeEvent.OutputAudio =
        RealtimeEvent.OutputAudio(Base64.Default.decode(value(data, "delta")), "pcm_s16le", 24000)

    private fun value(data: JsonObject, key: String): String = data[key]?.jsonPrimitive?.content.orEmpty()
}
