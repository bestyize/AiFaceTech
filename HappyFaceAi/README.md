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
- 生命中心包含角色卡、九个能力入口和本次运行中的表情变化记录。
- 顶栏按设计稿收窄（常规 44dp / 小横屏 36dp）：返回是矢量箭头加「返回」两字的胶囊按钮；竖屏放在标题左侧，横屏收到右上角；待接入详情页共用这一套顶栏。
- 「表情与外观」复用原有调试台，右上角保留「返回上一页」胶囊，本身没有整行标题栏，因此不加返回图标顶栏。
- 「AI 大脑」「声音」「视觉」已实现独立配置页；人格、记忆、能力、生活节律和设备系统仍为待接入详情页。
- Android 仅允许左右横屏（`sensorLandscape`）；宽屏双栏、窄屏纵向滚动，小横屏保留双栏。
- 当前角色资料为默认本地资料，变化记录不持久化，不展示虚构的陪伴天数。

生命中心页面位于 `lib_business/lib_lifecenter/src/commonMain/kotlin/com/aifacemate/face/feature/lifecenter/`。
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

### 声音与对话设置

#### 本地测试默认配置

参考 `local.properties.example` 在被 Git 忽略的 `local.properties` 中配置 DeepSeek 与 MiMo 按量密钥。
Android debug 包内置这些默认值；release 包保持密钥为空。桌面本地运行可读该文件，
需在独立桌面测试包中内置时使用 `-PincludeDevApiKeys=true`；不加该参数的标准构建会生成空的默认配置资源。
已有非空用户配置优先；未配置或空值使用开发默认值。MiMo Token Plan 使用独立服务地址与凭据，不混入语音配置。

#### 全屏对话

全屏与展开后的对话使用暖金色消息气泡，聊天记录占据可用空间；返回、收起和输入/语音操作固定。
保留完整消息记录，查看较早消息时新回复不会抢走阅读位置。文字可选中，长提示可以滚动查看。
`./gradlew :desktopApp:exportConversation` 用内存示例检查十一种尺寸/缩放/键盘可用空间配置，
验证文字发送、语音启停、处理中状态、旧记录保留、返回与收起，并导出 44 张图片到 `desktopApp/build/conversation/`。

- 小屏用顶部紧凑模式切换与独立滚动配置区；中屏左右分栏；大屏把服务选择与连接配置并排显示。
- 依据安全区域内可用 dp 和字体缩放调整布局，返回与保存始终固定在顶栏；输入时避让软键盘。
- 保留服务商与模型、MiMo 音色、本地模型下载/删除、猫脸显示和配置持久化；不支持的选项仍可见并标明设备限制。
- Android/Desktop SenseVoice 模型从魔搭兼容导出版按需下载并校验，模型保存在各自应用数据目录。仅本地识别无需密钥，生成回答需要 AI 大脑配置，MiMo 朗读及在线回退需要 MiMo 密钥；Android 与桌面端也可按需下载 Piper 中文 INT8 模型离线朗读。
- `./gradlew :desktopApp:exportVoiceSettings` 用内存示例导出 45 张页面图，并检查九种尺寸/缩放配置上的真实 Compose 交互，不读取密钥或发起下载。图片位于 `desktopApp/build/voice-settings/`。

YOLO26m 视觉检测已加入 [`lib_business/lib_vision`](lib_business/lib_vision/README.md)：Android / Desktop 通过 ONNX Runtime 运行本地模型，可在视觉设置中按需下载，也可提供自己的兼容导出。iOS 已有相机预览与采集，视觉推理后端尚未接入。

### 视觉配置

- 从生命中心的「视觉」进入，分别配置 YOLO 目标检测和表情识别的启用偏好、本地 ONNX 模型路径与置信度阈值。
- 点击保存后持久化到独立的 `vision.*` 设置项。YOLO 路径留空使用下载模型，也可填写自定义 `.onnx` 路径；表情识别启用时仍须填写路径。保存只校验路径格式，实际加载时检查文件与模型格式。
- YOLO26m 默认模型约 82 MB，在 Android/桌面端按需下载并校验固定版本的文件大小和 SHA-256。支持进度、取消、失败重试和删除；模型不打包进安装包，下载和打开设置页不会载入推理实例，首次调用检测时创建并复用实例，结束识别可通过 `Yolo26mModel.unload()` 释放内存。
- 开关默认关闭。保存配置不会启动摄像头；点击「保存并打开摄像头」后可在相机页面显式启动。Android/Desktop 已联动 YOLO，iOS YOLO 与表情识别后端仍待接入，页面明确展示状态。
- 返回与保存、两组配置切换固定在顶部，正文滚动；未保存编辑可在 Android 页面重建时保留，直接返回会放弃编辑。
- `./gradlew :desktopApp:exportVisionSettings` 检查七种尺寸/缩放上的实际 Compose 交互及下载/删除/取消、忙碌/错误状态，导出 22 张页面图到 `desktopApp/build/vision-settings/`，不读取或写入用户配置。
- `./gradlew :lib_business:lib_vision:verifyManagedYolo26m` 在隔离的构建目录实测 HTTPS 下载、完整性校验、真实 ONNX 连续推理、释放和删除，不影响用户模型目录；普通 JVM 测试保持离线。

### 三端摄像头

新增独立 [`lib_camera`](lib_business/lib_camera/README.md)：Android CameraX、iOS AVFoundation、Desktop OpenCV 共用控制器、状态和 RGBA 帧接口，采集与推理模块分别维护。生命中心 → 视觉 →「保存并打开摄像头」可启动实时预览，切换移动端设备或桌面设备编号；支持权限拒绝提示、停止和重试，离开页面/进入后台后释放采集资源。

Android/Desktop 在 YOLO 已启用且模型可用时绘制检测框、类别和置信度，前置预览镜像与框坐标一致；未配置模型时继续预览并提示。iOS 已接相机采集和预览，视觉推理后端仍待接入。视频帧仅在本机处理，不保存或上传。

`./gradlew :desktopApp:exportCamera` 用合成帧导出七种屏幕配置共 28 张图片，并检查实际页面交互；`./gradlew :lib_business:lib_camera:verifyDesktopCamera` 可显式验证真实采集、停止与重新打开；相机到本地 YOLO 的实测命令见模块说明。当前已验证 Android 构建与 Windows 相机/YOLO 调用，iOS 相机模块已通过 Kotlin/Native 编译，完整 Xcode 构建和真机行为仍需 Mac/iPhone 验证。

`shared` 只组合页面与平台入口；Android、Desktop 与 iOS 共用这一层。模块目录见 [docs/MODULES.md](docs/MODULES.md)。

- `lib_common/`：`lib_theme`（应用配色）、`lib_resource`（Compose 图像）、`lib_storage`（平台设置存储）、`lib_ui`（通用弹窗和提示）。
- `lib_business/`：`lib_face`（表情渲染与动画）、`lib_models`（模型服务商目录与请求）、`lib_brains`（AI 大脑状态和页面）、`lib_lifecenter`（生命中心）、`lib_voice`（实时对话、平台音频和 ASR/TTS 聚合接口）、`lib_camera`（三端相机采集与权限）、`lib_vision`（视觉模型与推理）。
