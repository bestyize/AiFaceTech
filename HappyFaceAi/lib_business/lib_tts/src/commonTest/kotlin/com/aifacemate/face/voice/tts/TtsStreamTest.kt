package com.aifacemate.face.voice.tts

import com.aifacemate.face.voice.tts.client.*
import com.aifacemate.face.voice.tts.model.*
import kotlin.test.*
import kotlinx.serialization.json.*

class TtsStreamTest {
    @Test fun requestsPcmStreamingAndPreservesText() {
        val root = Json.parseToJsonElement(requestBody(TtsRequest(apiKey = "test", text = "你好\n\"小星\""), true)).jsonObject
        assertTrue(root["stream"]!!.jsonPrimitive.boolean)
        assertEquals("pcm16", root["audio"]!!.jsonObject["format"]!!.jsonPrimitive.content)
        assertEquals("你好\n\"小星\"", root["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content)
    }
    @Test fun decodesAudioAndAlignsSplitSamples() {
        val chunk = decodeTtsStreamEvent("""{"choices":[{"delta":{"audio":{"data":"AQID"}},"finish_reason":null}]}""")
        val alignment = Pcm16Alignment()
        assertContentEquals(byteArrayOf(1, 2), alignment.append(chunk.audio))
        assertContentEquals(byteArrayOf(3, 4), alignment.append(byteArrayOf(4)))
        alignment.finish()
        assertTrue(decodeTtsStreamEvent("[DONE]").finished)
        alignment.append(byteArrayOf(5))
        assertFailsWith<IllegalStateException> { alignment.finish() }
    }
    @Test fun rejectsServiceErrorsAndConvertsPcmWithClipping() {
        assertFailsWith<IllegalStateException> { decodeTtsStreamEvent("""{"error":{"message":"bad key"}}""") }
        assertContentEquals(byteArrayOf(0, -128, 0, 0, -1, 127, 0, 0), floatSamplesToPcm16(floatArrayOf(-2f, 0f, 2f, Float.NaN)))
    }
}
