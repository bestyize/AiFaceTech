package com.aifacemate.face.voice.asr
import com.aifacemate.face.voice.asr.catalog.*
import com.aifacemate.face.voice.asr.model.*
import com.aifacemate.face.voice.asr.client.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsrTest {
    @Test fun includesXiaomiProvider() {
        val provider = defaultAsrProviders.single { it.id == "xiaomi-asr" }
        assertEquals("小米 ASR", provider.name)
        assertTrue(provider.models.contains("mimo-asr"))
    }
    @Test fun requestHasSafeDefaults() {
        val request = AsrRequest(apiKey = "key", audio = byteArrayOf())
        assertEquals("audio/wav", request.mimeType)
        assertEquals("mimo-asr", request.model)
    }
}
