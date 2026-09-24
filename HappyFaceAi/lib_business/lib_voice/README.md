# lib_voice

语音能力聚合入口。具体实现拆分到：

- `lib_tts`：文字转语音，当前内置小米 MiMo。
- `lib_asr`：语音转文字，当前内置小米 ASR。

两个子模块都提供平台中立的 `Provider + Request + Result` 接口，Android/Desktop 已接入 HTTP 请求实现。
