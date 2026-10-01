package com.aifacemate.face.voice.asr.vad

/** iOS records through AVAudioRecorder without sherpa-onnx; recognition falls back to the text check only. */
actual fun createSpeechDetector(): SpeechDetector? = null
