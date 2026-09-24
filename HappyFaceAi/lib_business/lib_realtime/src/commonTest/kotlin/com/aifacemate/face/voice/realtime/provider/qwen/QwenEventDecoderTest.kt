package com.aifacemate.face.voice.realtime.provider.qwen

import com.aifacemate.face.voice.realtime.RealtimeEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class QwenEventDecoderTest {
    @Test fun intermediateTranscriptUsesConfirmedPrefixAndStash() {
        val event = Json.parseToJsonElement("""{"type":"conversation.item.input_audio_transcription.delta","text":"今天","stash":"天气不错"}""").jsonObject
        assertEquals(RealtimeEvent.InputText("今天天气不错", false), QwenEventDecoder.input(event))
    }

    @Test fun transcriptionFailureIsNonfatalEvent() {
        val event = Json.parseToJsonElement("""{"type":"conversation.item.input_audio_transcription.failed","error":{"message":"audio invalid"}}""").jsonObject
        assertEquals(RealtimeEvent.TranscriptionFailed("audio invalid"), QwenEventDecoder.input(event))
    }

    @Test fun audioDeltaDecodesPcm() {
        val event = Json.parseToJsonElement("""{"type":"response.audio.delta","delta":"AQIDBA=="}""").jsonObject
        val audio = QwenEventDecoder.outputAudio(event)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), audio.bytes)
        assertEquals("pcm_s16le", audio.format)
        assertEquals(24000, audio.sampleRate)
    }
}
