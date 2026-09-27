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

### AI 大脑 · v3

实现依据为 `ModuleDesign/AI大脑设置页设计说明包_v3/设计说明.md` 与包内四张 PNG。
图片中仍残留旧的六项菜单，**以文字版的四页范围为准**。

- 左侧仅保留 AI 大脑、密钥配置、能力接入、思考模式；各菜单切换右侧内容，不再打开整页弹窗。
- 当前大脑包含服务商横向选择、内嵌模型下拉菜单、远端模型获取、自定义模型入口。
- 密钥页保留 API Base URL / API Key / Model。点击字段调用现有键盘避让输入条。
- 测试连接只请求兼容接口的 `/models`，不保存凭据；保存配置重新验证后保存地址、密钥和模型选择。
  验证模型列表不等于验证实际对话能力。未测试或配置被编辑时不显示已连接。
- 清空操作需要确认，仅影响当前服务商的连接配置，保留其它服务商和自定义模型目录。
- 异步结果绑定请求时的配置快照，切换服务商、修改字段后，旧响应不能覆盖当前状态。
- 八项能力开关保存用户偏好。新加的相机、语音、文件与家居默认关闭；**不代表对应服务或系统权限已经接入**。
- 思考模式只保留三张选择卡，沿用配置持久化。没有新增硬件或系统设置页。
- 导航和功能图标为单色 Canvas 路径；提供商图标和机器人插画裁自用户提供的 v3 设计板。
  原图分辨率有限，后续可用独立高清素材替换。没有把整张设计图作为可点击界面。

#### 小屏适配

按安全区域内可用 **dp** 与长宽比判断，不把物理像素等同于 dp。
480×320@1x 使用 56dp 图标栏，800×480 展示图文菜单，宽横屏再展示机器人装饰区。
能力列表在小屏单列滚动，较宽屏双列；字体放大、较高 density 时允许内容滚动而不压缩触控区。
仅装饰插画隐藏，功能入口始终保留。短屏可滚动菜单，返回入口固定在底部。

#### 验证命令

```shell
bash gradlew :shared:jvmTest :lib_business:lib_face:jvmTest :androidApp:assembleDebug
bash gradlew :desktopApp:verifyAiBrainV3
bash gradlew :desktopApp:exportAiBrain
```

`verifyAiBrainV3` 在 480×320 / 800×480 的真实 Compose 语义树上检查导航、服务商切换、
测试/保存分发、清空确认、能力开关、思考模式、返回，以及主要按钮的最小触控尺寸。
`exportAiBrain` 输出 40 张页面图到 `desktopApp/build/ai-brain/v3/`，覆盖四页 × 十种配置：
480×320、640×360、640×480、800×480、897×813（折叠屏内屏全屏）、2400×1080@2.75x、1440×900，
以及 480×320 / 640×360 的 1.3 倍字体和 480×320 的 1.5 倍 density。
`640×360`、`640×480` 与 `897×813` 取自实测设备配置：折叠屏在自由窗口下会给出 640×360dp，
而内屏全屏横屏接近正方形，长宽比与普通手机完全不同。
导出使用内存示例状态，不读取用户密钥、不进行外部网络请求。

注意：沿用现有 SettingsStore（Android SharedPreferences、JVM Preferences、iOS UserDefaults）；
本次未改为加密存储，因此界面不会宣称密钥已加密。真实服务商连通性需使用用户自己的配置验证。

## 工程模块

YOLO26m 视觉检测已加入 [`lib_business/lib_vision`](lib_business/lib_vision/README.md)：Android / Desktop 通过 ONNX Runtime 运行本地模型，模型需按说明单独导出并提供。iOS 当前只有共享检测数据契约，尚无视觉推理后端或相机页面。

`shared` 只组合页面与平台入口；Android、Desktop 与 iOS 共用这一层。模块目录见 [docs/MODULES.md](docs/MODULES.md)。

- `lib_common/`：`lib_theme`（应用配色）、`lib_resource`（Compose 图像）、`lib_storage`（平台设置存储）、`lib_ui`（通用弹窗和提示）。
- `lib_business/`：`lib_face`（表情渲染与动画）、`lib_models`（模型服务商目录与请求）、`lib_brains`（AI 大脑状态和页面）、`lib_lifecenter`（生命中心）、`lib_voice`（尚待接入的语音接口）。
