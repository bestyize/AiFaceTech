package com.aifacemate.face.voice.tts
import com.aifacemate.face.voice.tts.catalog.*
import com.aifacemate.face.voice.tts.model.*
import com.aifacemate.face.voice.tts.client.*
import com.aifacemate.face.voice.tts.internal.jsonEscape

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TtsTest {
    @Test fun includesMimoProvider() {
        val provider = defaultTtsProviders.single { it.id == "xiaomi-mimo" }
        assertEquals("小米 MiMo", provider.name)
        assertTrue(provider.models.contains("mimo-v2-tts"))
    }
    @Test fun escapesSpeechText() {
        assertEquals("a\\\"b\\nc", jsonEscape("a\"b\nc"))
    }
}
