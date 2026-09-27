# lib_voice

语音能力聚合入口。具体实现拆分到：

- `lib_realtime`：豆包与千问的端到端实时语音会话，提供统一事件和双向音频接口。
- `lib_tts`：文字转语音，当前内置小米 MiMo。
- `lib_asr`：语音转文字，包含小米在线 ASR，以及 Android 可按需下载的 SenseVoiceSmall INT8。

实时会话和单次 ASR/TTS 请求使用独立接口。使用方法、音频格式和凭据要求见 [`lib_realtime/README.md`](../lib_realtime/README.md)。
