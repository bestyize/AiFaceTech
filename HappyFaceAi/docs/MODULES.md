# HappyFaceAi 模块边界

`lib_common` 和 `lib_business` 是一级分组目录，其子目录是 Gradle 模块。目录层级和 Gradle 路径一致。

| 模块 | 职责 | 直接依赖 |
| --- | --- | --- |
| `lib_common:lib_theme` | 应用调色板 | Compose UI |
| `lib_common:lib_resource` | Compose 图片资源与公开的 `Res` | Compose Resources |
| `lib_common:lib_storage` | SettingsStore 的 Android、iOS、JVM 实现 | 平台 SDK |
| `lib_common:lib_ui` | 可复用弹窗和 Toast | lib_theme |
| `lib_business:lib_face` | 表情参数、动画、Canvas 与预览组件 | Compose |
| `lib_business:lib_models` | 供应商目录、模型能力识别与平台 HTTP 请求 | Kotlin / coroutines |
| `lib_business:lib_brains` | AI 大脑状态、设置、交互页面 | lib_models、lib_storage、lib_ui、lib_resource |
| `lib_business:lib_lifecenter` | 生命中心页面、路由模型 | lib_face、lib_theme |
| `lib_business:lib_voice` | 语音输入输出接口；尚无 ASR/TTS 实现 | Kotlin |
| `shared` | 应用导航和三端共用页面组合 | face、brains、lifecenter、theme |
| `androidApp` / `desktopApp` / `iosApp` | 各平台入口、窗口和安装包 | shared 等直接使用的模块 |

依赖方向：入口 → shared / 业务模块 → 通用模块。通用模块不引用业务模块；模型请求模块也不引用 AI 大脑 UI。将来的语音实现应放在 lib_voice 中，再由 App 接入，不应直接塞进 shared。

## 常用命令

从 `HappyFaceAi/` 执行：

```bash
./gradlew :lib_business:lib_face:jvmTest :lib_business:lib_models:jvmTest :lib_business:lib_brains:jvmTest :lib_business:lib_voice:compileKotlinJvm :shared:jvmTest
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:packageDistributionForCurrentOS
```

iOS 应用入口在 `iosApp/`，由 Xcode 构建；GitHub Actions 产出的是无签名的模拟器包。

### `lib_business:lib_brains` 内部包

AI 大脑模块按职责分包，避免页面、状态和设备存储互相引用：

- `feature.aibrain.domain`：`AiBrainUiState`、连接/拉取状态及状态转换。
- `feature.aibrain.data`：`AiBrainSettings`，只负责通过 `SettingsStore` 读写配置。
- `feature.aibrain.presentation`：`AiBrainRoute`、`AiBrainScreen`、`AiBrainViewModel` 和提供商图标。
- `feature.aibrain.presentation.theme`：AI 大脑专用颜色、布局断点、按钮、面板和线性图标。

页面入口只依赖 domain 和 presentation；持久化细节集中在 data 包中。

### 语音能力模块

- `lib_business:lib_voice`：语音能力聚合入口，对外暴露 TTS / ASR 模块。
- `lib_business:lib_tts`：文字转语音契约、供应商目录与请求实现；当前内置小米 MiMo。
- `lib_business:lib_asr`：语音转文字契约、供应商目录与 multipart 请求实现；当前内置小米 ASR。

两个模块都采用 `Provider + Request + Result` 结构。Android/Desktop 已提供 HTTP 实现，iOS 保留同一 expect/actual 接口，待补 NSURLSession 网络实现。供应商地址、模型和音色均可在请求层替换，不把具体服务商写死在 UI 中。

### `lib_tts` / `lib_asr` 内部包

两个语音模块采用相同的职责分层：

- `catalog`：供应商目录与默认配置。
- `model`：请求参数与结果类型。
- `client`：跨平台 `expect/actual` 调用入口。
- `internal`：HTTP 协议、JSON 转义和 multipart 辅助，不向业务层暴露。
