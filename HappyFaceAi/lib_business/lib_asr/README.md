# lib_asr

语音转文字模块，按 `catalog`、`model`、`client`、`internal` 分包。
当前内置小米 ASR（`mimo-v2.5-asr`）。Android/Desktop 使用 multipart HTTP 实现，iOS 保留同一接口。

## SenseVoiceSmall INT8（Android）

声音设置 → 传统对话 → SenseVoice 本地识别，可主动下载或删除模型。模型不打包进 APK；从
`csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17` 的固定提交
`2365baeacb507f821a0c8120fcee3d484dba7a07` 下载 `model.int8.onnx` 与 `tokens.txt`，
校验文件长度和 SHA-256 后保存在应用私有的 `noBackupFilesDir`。模型约 239 MB。
运行库使用 sherpa-onnx Android AAR `v1.13.8`，由 JitPack 解析；不将模型放入安装包。

当前按现有单次录音流程识别 16 kHz 单声道 PCM16 WAV。SenseVoice 无需识别 API Key；
MiMo 仍用于语音合成，并在本地模型缺失或识别失败时提供在线回退，因此传统对话仍需
配置 MiMo API Key。iOS 与桌面暂不显示本地识别选项。
