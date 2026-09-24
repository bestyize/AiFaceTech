# 实时对话

`lib_realtime` 只负责长连接、协议编解码和统一事件；`lib_voice` 暴露该模块，原有 `lib_asr`/`lib_tts` 保留独立的单次请求能力。`shared` 后续只需消费 `RealtimeSession.events`，无需了解服务商协议。

```kotlin
val session = RealtimeConversationEngine().connect(
    RealtimeConfig.Qwen(apiKey = key, workspaceEndpoint = "wss://YOUR_ID.cn-beijing.maas.aliyuncs.com")
)
// 立即启动单个协程收集 session.events，收到 Ready 后开始持续发送 PCM16/16 kHz/单声道帧。
session.sendAudio(RealtimeAudio(pcmFrame))
// 收到 InterruptPlayback 时立即清空播放器缓冲区；结束时 session.close()。
```

豆包使用 `RealtimeConfig.Doubao(appId, accessKey, appKey, sessionId)`；`appKey` 按官方文档在运行时提供，每次连接传入新的 UUID。默认以服务端 VAD 判断轮次，发送音频时不要在静音期间停流。豆包输出为 24 kHz 单声道 float32 PCM，千问输出为 24 kHz 单声道 int16 PCM；播放器按 `OutputAudio.format` 解码。豆包的 ASRInfo 会通知本地停止播报；千问还发送 `response.cancel`。音频采集、回声消除、播放器及 Face 状态映射由平台或 UI 层接入。

调用方从运行时配置获取凭据。公开发行版应由自己的服务端签发短期凭据，避免将长期 API Key 打入客户端包。失败通过 `Failure` 事件和连接关闭通知，调用方可创建新会话重连。

协议文档：[豆包端到端实时语音](https://www.volcengine.com/docs/6561/1594356)、[千问 Omni Realtime](https://help.aliyun.com/zh/model-studio/realtime)、[千问客户端事件](https://help.aliyun.com/zh/model-studio/client-events)。
