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
./gradlew :lib_business:lib_face:jvmTest :lib_business:lib_models:jvmTest :lib_business:lib_brains:jvmTest :shared:jvmTest
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:packageDistributionForCurrentOS
```

iOS 应用入口在 `iosApp/`，由 Xcode 构建；GitHub Actions 产出的是无签名的模拟器包。
