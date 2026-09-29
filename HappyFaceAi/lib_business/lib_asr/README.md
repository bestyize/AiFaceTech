# lib_asr

语音转文字模块，按 `catalog`、`model`、`client`、`internal` 分包。
当前内置小米 ASR（`mimo-v2.5-asr`）。Android/Desktop 使用 multipart HTTP 实现，iOS 保留同一接口。

## SenseVoiceSmall INT8（Android / Desktop）

声音设置 → 分段语音 → SenseVoice 本地识别，可主动下载或删除模型。模型不打包进 APK 或桌面发行包；从
魔搭社区 [`pengzhendong/sherpa-onnx-sense-voice-zh-en-ja-ko-yue`](https://modelscope.cn/models/pengzhendong/sherpa-onnx-sense-voice-zh-en-ja-ko-yue) 的固定提交
`73eca47697f980daa3d16112404174b6b950b514` 下载 `model.int8.onnx` 与 `tokens.txt`，
校验文件长度和 SHA-256 后保存在应用私有的 `noBackupFilesDir`。模型约 239 MB。
两份文件与原 Hugging Face 固定版本的大小和 SHA-256 完全一致，已有模型无需重新下载。
下载使用魔搭文件接口并跟随 HTTPS 重定向。
运行库使用官方发布的 `sherpa-onnx-static-link-onnxruntime-1.13.8.aar`，由受限 Ivy 仓库解析官方 GitHub Release。
该 AAR 把 ONNX Runtime 静态链接进识别库，与视觉模块的 ONNX Runtime `1.30.0` 分离；不再靠 `pickFirst` 处理两个不兼容的共享库。
官方 AAR 的 SHA-256 为 `b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471`。
模型不放入安装包，Android/JVM 视觉运行库版本不变。
当前 APK 支持 ARM64、ARMv7 和 x86_64。官方静态 AAR 的 32 位 x86 分支仍依赖共享运行库，故暂不打包该 ABI，避免保留不可用的语音后端。

当前按现有单次录音流程识别 16 kHz 单声道 PCM16 WAV。SenseVoice 无需识别 API Key；
MiMo 仍用于语音合成；仅在已配置 MiMo API Key 时提供在线识别回退。
未配置密钥也可使用 SenseVoice 返回识别文字；生成回答需要 AI 大脑配置，MiMo 朗读回复需要 MiMo 密钥。
iOS 暂不提供 SenseVoice 后端并在界面标明平台限制；Android 与桌面版均使用官方 sherpa-onnx 运行时。
