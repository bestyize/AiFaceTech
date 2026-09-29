# lib_tts

文字转语音模块，按 `catalog`、`model`、`client` 分包。

- Android 与 Windows/Linux/macOS 桌面端均支持小米 MiMo 在线语音合成。
- Android 与桌面端可选 sherpa-onnx Piper 中文 Chaowen medium INT8 单说话人模型：约 14 MB 压缩包，模型文件按需下载/删除，解压后约 20.9 MB，不打进 APK/桌面发行包。下载后离线合成。
- iOS 暂不支持 Piper。本地运行使用各平台匹配的官方 sherpa-onnx JNI 运行库。
