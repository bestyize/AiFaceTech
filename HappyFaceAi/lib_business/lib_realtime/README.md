# 实时对话

`lib_realtime` 只负责长连接、协议编解码和统一事件；`lib_voice` 暴露该模块，原有 `lib_asr`/`lib_tts` 保留独立的单次请求能力。`shared` 后续只需消费 `RealtimeSession.events`，无需了解服务商协议。

```kotlin
val session = RealtimeConversationEngine().connect(
    RealtimeConfig.Qwen(apiKey = key, workspaceEndpoint = "wss://YOUR_ID.cn-beijing.maas.aliyuncs.com")
)
// 立即启动单个协程收集 session.events，收到 Ready 后开始持续发送 PCM16/16 kHz/单声道帧。
session.sendAudio(RealtimeAudio(pcmFrame))
// 收到 InterruptPlayback 时立即清空播放器缓冲区；OutputComplete 后等本地播放完毕再切换脸部状态。
// 结束时 session.close()。
```

豆包使用 `RealtimeConfig.Doubao(appId, accessKey, appKey, sessionId)`；`appKey` 按官方文档在运行时提供，每次连接传入新的 UUID。默认以服务端 VAD 判断轮次，发送音频时不要在静音期间停流。豆包输出为 24 kHz 单声道 float32 PCM，千问输出为 24 kHz 单声道 int16 PCM；播放器按 `OutputAudio.format` 解码。豆包的 ASRInfo 会通知本地停止播报；千问还发送 `response.cancel`。千问的 `InputText(final = false)` 为实时预览，后一条预览应替换前一条；`TranscriptionFailed` 仅表示当前识别失败，会话可以继续。`OutputComplete` 只表示服务端生成结束，播放完毕由本地播放器判定。音频采集、回声消除、播放器及 Face 状态映射由平台或 UI 层接入。

调用方从运行时配置获取凭据。公开发行版应由自己的服务端签发短期凭据，避免将长期 API Key 打入客户端包。失败通过 `Failure` 事件和连接关闭通知，调用方可创建新会话重连。

## 桌面端协议联调（手动运行）

准备**无 WAV 文件头**、16 kHz、单声道、16-bit little-endian PCM 的 `input.pcm`。在 `HappyFaceAi/` 目录执行下面任一命令。任务会每 20 ms 发送一帧音频，等服务端 VAD 完成首轮回复后，将流式音频写入输出文件；不会启动 Compose 界面。

```bash
export DOUBAO_APP_ID=... DOUBAO_ACCESS_KEY=... DOUBAO_APP_KEY=...
./gradlew :desktopApp:runRealtimeVoice -PvoiceProvider=doubao -PvoiceInput=/path/input.pcm -PvoiceOutput=/path/doubao.pcm

export DASHSCOPE_API_KEY=... QWEN_WORKSPACE_ENDPOINT=wss://YOUR_ID.cn-beijing.maas.aliyuncs.com
./gradlew :desktopApp:runRealtimeVoice -PvoiceProvider=qwen -PvoiceInput=/path/input.pcm -PvoiceOutput=/path/qwen.pcm
```

豆包输出为 24 kHz float32 PCM，千问输出为 24 kHz int16 PCM。例如有 `ffplay` 时，分别使用 `ffplay -f f32le -ar 24000 -ac 1 doubao.pcm` 和 `ffplay -f s16le -ar 24000 -ac 1 qwen.pcm`。命令从环境变量读取凭据，不把值写入参数或仓库。

## 桌面端麦克风与扬声器（手动运行）

与上面相同，先设置所选服务商的环境变量。在有麦克风、扬声器的桌面电脑上运行：

```bash
./gradlew :desktopApp:runRealtimeLiveVoice -PvoiceProvider=doubao -PvoiceSeconds=120
# 或
./gradlew :desktopApp:runRealtimeLiveVoice -PvoiceProvider=qwen -PvoiceSeconds=120
```

任务运行期间持续采集 16 kHz PCM16 麦克风音频，播放 24 kHz 回复；设备只支持 48 kHz 播放时会做简单的 2 倍采样。按回车提前结束，未连接标准输入时到达 `voiceSeconds` 自动结束（默认 120 秒）。收到打断事件会丢弃排队音频并清空扬声器缓冲，`OutputComplete` 后等待设备播完才提示继续聆听。首次使用需允许终端或 Java 进程访问麦克风。当前仅覆盖 JVM 桌面端，没有声学回声消除；用耳机联调以免扬声器声音被麦克风再次识别。Android/iOS 的硬件采集与播放仍需各自的平台实现。

协议文档：[豆包端到端实时语音](https://www.volcengine.com/docs/6561/1594356)、[千问 Omni Realtime](https://help.aliyun.com/zh/model-studio/realtime)、[千问客户端事件](https://help.aliyun.com/zh/model-studio/client-events)。
