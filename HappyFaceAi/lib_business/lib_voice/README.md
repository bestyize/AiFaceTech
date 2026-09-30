# lib_voice

语音能力聚合入口。具体实现拆分到：

- `lib_realtime`：豆包与千问的端到端实时语音会话，提供统一事件和双向音频接口。
- `lib_tts`：文字转语音，当前内置小米 MiMo。
- `lib_asr`：语音转文字，包含小米在线 ASR，以及 Android 可按需下载的 SenseVoiceSmall INT8。

实时会话和单次 ASR/TTS 请求使用独立接口。使用方法、音频格式和凭据要求见 [`lib_realtime/README.md`](../lib_realtime/README.md)。

## 倾听观察

`VoiceObservation` 提供中间文字、归一化实测音量和取消事件。`NativeSpeechEngine.observations` 在 Android 系统识别启用中间转写与 RMS 回调，iOS 系统识别启用中间转写。`VoiceService.observations` 从 Android/Desktop PCM16 采集和 iOS 录音电平提供音量。实时 PCM 在宿主限频测量，供应商的非最终 InputText 单独转发，不写入对话历史，也不触发提交或大模型调用。

分段 SenseVoice/MiMo 只有结束录音后才有文字，倾听期间仅提供音量活动。无中间结果的系统服务也保持普通倾听。所有观察事件在宿主主线程进入行为控制器。iOS 新接入代码尚需 Xcode/实机验证。详见 [语音互动方案](../../docs/VOICE_INTERACTION_PLAN.md)。
