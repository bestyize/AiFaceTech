This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM).

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
### 生命中心

- 首页轻点显示临时操作层，3.5 秒后自动收起；长按进入生命中心。
- 抚摸会短暂切换开心表情；对话入口明确提示语音尚未接入。
- 生命中心包含角色卡、八个能力入口和本次运行中的表情变化记录。
- 顶栏按设计稿收窄（常规 44dp / 小横屏 36dp）：返回是矢量箭头加「返回」两字的胶囊按钮；竖屏放在标题左侧，横屏收到右上角；待接入详情页共用这一套顶栏。
- 「表情与外观」复用原有调试台，右上角保留「返回上一页」胶囊，本身没有整行标题栏，因此不加返回图标顶栏。
- 「AI 大脑」已实现独立配置页；声音、人格、记忆、能力、生活节律和设备系统仍为待接入详情页。
- Android 仅允许左右横屏（`sensorLandscape`）；宽屏双栏、窄屏纵向滚动，小横屏保留双栏。
- 当前角色资料为默认本地资料，变化记录不持久化，不展示虚构的陪伴天数。

共享页面位于 `shared/src/commonMain/kotlin/com/aifacemate/face/feature/lifecenter/`。
运行 `./gradlew :desktopApp:exportLifeCenter` 可渲染宽屏、小横屏和手机窄屏，用于视觉检查。

### AI 大脑

- 左侧展示当前供应商、模型、特点标签、小星表情和快速 / 平衡 / 深度三种思考模式。
- 右侧提供可横滑的供应商与模型卡片、联网搜索 / 长期记忆 / 工具调用 / 日程提醒开关，以及 OpenAI Compatible 密钥配置。
- 供应商与模型来自独立 catalog 数据层；切换供应商会选择推荐模型并刷新 Base URL，最后一个模型固定为「自定义」。
- 配置状态由 `AiBrainViewModel` 管理，UI 不直接持有供应商、模型或能力开关状态。
- 当前「保存并校验」只做本地必填项与 URL 格式检查，真实网络连接测试留给后续服务层。

共享页面位于 `shared/src/commonMain/kotlin/com/aifacemate/face/feature/aibrain/`。
运行 `./gradlew :desktopApp:exportAiBrain` 可导出 1440×900 宽屏和 817×375 实机横屏走查图。

本次验证：Android Debug 构建、shared 与 lib_face JVM 测试通过；宽屏、小横屏、手机窄屏三个尺寸离屏渲染通过，
确认竖屏「‹ 返回」在标题左侧、小横屏收到右上角。
真机补充验证（M2012K11AC）：安装启动、长按进入生命中心、AI 大脑详情与系统返回、表情页切换开心表情并返回均正常。
横屏下生命中心与 AI 大脑详情的顶栏都是矮顶栏，「‹ 返回」胶囊在右上角，点它能回到上一级；
横屏紧凑布局下左侧资料和操作按钮完整可见，八个能力入口同屏显示。
尚未覆盖所有机型、字体放大和完整旋转状态恢复测试。
