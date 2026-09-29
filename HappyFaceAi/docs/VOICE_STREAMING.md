# 分段对话的流式回复

分段对话在识别到文字后，默认使用“大脑流式出字 → 按句 / 短段合成 → 顺序播放”。三个阶段并行工作，首句不用等完整回答和整段音频。现有豆包、千问实时语音链路保持原来的实现。

- MiMo 预置音色模型 `mimo-v2.5-tts` 使用 `stream:true`，读取 SSE 中的 `choices[].delta.audio.data`，按 24 kHz、单声道 PCM16LE 播放。输入文字仍按短段提交，音频输出按块接收。
- Piper Android 和桌面使用 sherpa-onnx 1.13.8 的 `generateWithCallback`，原生回调将每句生成的浮点音频复制为 PCM16。Piper 的粒度是句 / 短段；一句内部的模型推理仍需要等待。
- Android 系统 TTS 按短句依次提交，复用已初始化的 `TextToSpeech`，避免每句启动并销毁引擎。
- 桌面 / Android PCM 播放器只打开扬声器，不打开麦克风。播放器在一次回复内保持打开，最后等待已写入的音频播完，再释放资源。

首段最长 32 个 UTF-16 单元，后续段最长 80 个；遇到句末标点会提前提交，首段较长时也允许逗号处分段，英文优先按空格切分。不会将 DeepSeek 的 `reasoning_content`、工具参数送去朗读。文字历史保留模型的原始输出，分句不会改变显示内容。

句子队列容量为 2，音频队列容量为 8；PCM 块最长约 250 毫秒。这样可以预取后续音频并限制积压。停止回复会取消大脑、合成和播放，关闭正在等待的 HTTP 连接，丢弃尚未播放的队列。已生成的文字保留。语音服务或扬声器失败时，停止音频但继续接收完整文字，并在结束后提供查看提示的入口。

没有 AI 大脑密钥时，SenseVoice 仍只返回本地识别文字；没有 MiMo 密钥时，仍生成并展示文字回复。当前网络流与 PCM 播放支持 Android 和桌面，iOS 的网络及 Piper 限制保持原状。

## 验证

在项目目录执行：

```text
gradlew.bat :lib_common:lib_network:jvmTest :lib_business:lib_chat:jvmTest :lib_business:lib_tts:jvmTest :shared:jvmTest
gradlew.bat :desktopApp:exportVoiceFaceControls :androidApp:assembleDebug
gradlew.bat :desktopApp:verifyStreamingSpeech
```

HTTP 测试通过服务器屏障确保首块音频进入播放时，大脑和 TTS 响应均未完成；另测 UTF-8 / SSE 分块、PCM 采样对齐、完成信号、取消、错误与文字保留。界面导出检查处理和播放期间的停止入口，以及长按记录、小屏和字体缩放。

`verifyStreamingSpeech` 默认仅测已安装的本地 Piper 模型，不打开麦克风或扬声器；加 `-PstreamVerifyApi=true` 后，使用忽略的 `local.properties` 中的开发测试配置测 MiMo 和大脑链路，只输出耗时与计数，不输出密钥或回答。首音时间取决于大脑首句、模型初始化、服务负载、网络和设备算力；流式播放不会让原生模型推理本身变快。

协议参考：[MiMo 官方合成文档](https://mimo.mi.com/docs/en-US/quick-start/usage-guide/audio/speech-synthesis)、[DeepSeek Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)、[sherpa-onnx 1.13.8 TTS API](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/kotlin-api/Tts.kt)。

## Android 原生回调回归

sherpa-onnx 的 Android JNI 按签名查找 `invoke(float[]) -> java.lang.Integer`。Kotlin invokedynamic lambda 经 D8 转换后只有擦除的 `invoke(Object)`，会使原生层触发 `NoSuchMethodError` 并终止进程。因此 Android Piper 使用具体的 `PiperAudioCallback` 类，保留 JNI 要求的数组参数和装箱返回类型。JVM 回归测试检查该精确签名。

设备回归使用现有模型，全程离线，不读取密钥、不使用麦克风、不清空应用数据：

```text
gradlew.bat :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
adb -s <serial> install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s <serial> install -r androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb -s <serial> shell am instrument -w com.aifacemate.face.test/com.aifacemate.face.PiperStreamingInstrumentation
```

设备测试检查 D8 后的回调签名、原生流式合成、真实扬声器播放、连续两次取消以及取消后重新合成。测试包可单独卸载：`adb -s <serial> uninstall com.aifacemate.face.test`；保留主应用及其设置、模型。
