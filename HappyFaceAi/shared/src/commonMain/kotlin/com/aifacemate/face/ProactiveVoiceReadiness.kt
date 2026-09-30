package com.aifacemate.face

import com.aifacemate.face.voice.systemAsrAvailable
import com.aifacemate.face.voice.systemTtsAvailable
import com.aifacemate.face.voice.realtimeAudioAvailable
import com.aifacemate.face.voice.asr.client.SenseVoiceAsr
import com.aifacemate.face.voice.tts.client.PiperTts

/** No automatic model download or credential changes when a visual cue arrives. */
internal fun proactiveVoiceReady(preferences: VoicePreferences): Boolean {
    if (!preferences.keepFaceVisible) return false
    val speech = when (preferences.traditionalTts) {
        TraditionalTts.System -> systemTtsAvailable()
        TraditionalTts.Piper -> PiperTts.supported() && PiperTts.installed()
        TraditionalTts.Mimo -> preferences.mimoApiKey.isNotBlank()
    }
    val input = when (preferences.mode) {
        VoiceMode.Realtime -> realtimeAudioAvailable() && when (preferences.provider) {
            RealtimeProvider.Qwen -> preferences.qwenApiKey.isNotBlank() && preferences.qwenEndpoint.isNotBlank()
            RealtimeProvider.Doubao -> preferences.doubaoAppId.isNotBlank() && preferences.doubaoAccessKey.isNotBlank() && preferences.doubaoAppKey.isNotBlank()
        }
        VoiceMode.Traditional -> when (preferences.traditionalAsr) {
            TraditionalAsr.System -> systemAsrAvailable()
            TraditionalAsr.SenseVoice -> SenseVoiceAsr.supported() && SenseVoiceAsr.installed()
            TraditionalAsr.Mimo -> preferences.mimoApiKey.isNotBlank()
        }
    }
    return speech && input
}
