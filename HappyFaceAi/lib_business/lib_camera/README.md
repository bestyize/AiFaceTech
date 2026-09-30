# lib_camera

独立的 Kotlin Multiplatform 相机模块，提供三端统一的 `CameraController`、`CameraState`、`CameraFrame` 和预览位图转换。模块负责权限、设备选择、实时采集与资源释放，不依赖 `lib_vision`；`shared/CameraScreen.kt` 负责把采集帧交给 YOLO 与本地表情/手势分析。

## 使用入口

表情页底部「视觉」，或生命中心 → 视觉 →「打开摄像头」，均进入同一个全屏分析页。视觉设置修改后即时保存。进入分析页后自动申请/使用相机，移动端优先前置；离开或进入后台停止采集，回到前台自动恢复，结束识别时释放模型实例。右上角在存在多个摄像头时显示切换图标，右侧滑条调整变焦，拍照按钮保存照片。

Android 在启动时通过 CameraControl 恢复连续 AF，点击预览使用 FocusMeteringAction 请求 AF/AE/AWB，完成后自动恢复连续对焦。iOS 在启动时开启支持的连续 AF/AE，点击位置触发一次对焦并恢复连续 AF。点击坐标先撤销全屏裁剪、前置镜像，再转换到原生相机方向。固定焦距或不支持点选对焦的镜头只调整支持的测光，并明确提示；桌面启用驱动支持的自动对焦，通用 OpenCV 接口不提供点击位置对焦。

安全区域高度小于 400dp 时使用紧凑控件：小横屏快门移到右侧中央，返回/切换按钮的可见圆形缩小，但点击区域保留 48dp；底边只显示一条结果，点击可查看完整结果。点右侧倍率才展开变焦条，停止调整 3 秒后收起。桌面的小屏设备编号入口位于结果详情内。预览始终全屏，系统安全区域只计算一次。

Android 使用 CameraX 原生变焦，iOS 使用设备 videoZoomFactor，桌面使用 1–4× 数字变焦；推理和照片使用同一变焦画面。照片保存当前预览帧，按全屏比例居中裁剪，前置照片与预览一样镜像，不是独立的全分辨率传感器拍摄。Android 10+ 保存到 Pictures/HappyFaceAi 相册，Android 9 及更早版本保存到应用图片目录；iOS 仅在首次拍照时请求相册添加权限，桌面保存到用户 Pictures/HappyFaceAi。视频帧不上传。

- Android：CameraX 1.6.0，支持运行时权限、前后及外接设备选择、生命周期绑定、相机错误和断开提示。
- iOS：AVFoundation，支持授权、设备选择、BGRA 帧采集、方向变化与后台停止；`Info.plist` 已添加 `NSCameraUsageDescription`。模拟器没有实体相机时会显示设备不可用。
- Desktop：OpenCV 4.13.0 的 JavaCPP 1.5.13 绑定，支持 Windows x64、Linux/macOS x64 与 ARM64 的构建。桌面设备按编号选择，0 为默认相机；打包时仅包含当前平台所需的原生库。macOS 安装包增加相机用途说明与 entitlement，并保留原有 JVM 原生运行权限。

前置识别使用未镜像的直立像素，检测框转换到全屏裁剪坐标时再镜像；保存照片按预览镜像。移动端优先在前后置之间切换，没有相反朝向时依次循环设备；桌面启动时探测编号 0–3，多个设备可循环切换，也保留手动填写 0–15 的入口。实际可用编号取决于设备与驱动，权限不足、没有设备或被其它应用占用会显示错误并提供重试。

## 帧和生命周期

`CameraFrame` 是已复制的 RGBA8888 像素，包含宽高、帧序号与预览镜像标记。Android 处理 YUV 平面各自的行/像素 stride 和旋转；iOS 复制锁定的 BGRA 缓冲区并移除行填充；桌面处理 BGR/BGRA/灰度输入。

预览采样约 12 帧/秒，`StateFlow` 只保留最新帧。Android 使用 `STRATEGY_KEEP_ONLY_LATEST`，iOS 丢弃迟到帧，YOLO 消费端使用 `conflate` 并限制识别频率，避免慢推理堆积。推理在后台执行，预览不等待每帧识别完成。切换设备时先完成旧会话的释放，再启动新会话；通过会话标记忽略旧回调。

`rememberCameraController()` 创建当前平台的控制器，并在 Compose owner 释放时关闭它。`start(deviceId)` 显式启动，`stop()` 等待资源释放，`close()` 用于非阻塞的 owner 结束。桌面工具也可显式调用 `createDesktopCameraController()`。

YOLO 沿用视觉设置：Android/Desktop 在启用且模型可用时实时识别，展示框、类别和置信度；模型未下载或加载失败时继续提供相机预览并提示错误。表情和手势也可在视觉页分别启用；Android/Desktop 使用可单独下载的本地 ONNX 模型，iOS 使用 Vision 关键点与手部静态姿势分析。iOS 的表情输出是微笑/张嘴等动作线索，七类表情分类器仅在 Android/Desktop 提供。iOS YOLO 仍待接入。

## 验证

```bash
./gradlew :lib_business:lib_camera:jvmTest :shared:jvmTest :androidApp:assembleDebug
./gradlew :desktopApp:exportCamera :desktopApp:exportVisionSettings
```

离线测试验证像素转换、旋转、全屏裁剪/镜像、点击对焦坐标、照片裁剪、数字变焦和关闭状态。`exportCamera` 用合成帧检查十种尺寸/字体/density 下的控件、预览点击对焦、变焦、拍照、详情、权限与重试；确认点击控件不会误触底层对焦，并导出页面图，不打开真实摄像头。另对实际 `CameraScreen` 注入内存控制器，验证自动前置启动、对焦委派、串行切换、后台停止、前台恢复以及资源释放。

需要实体相机的显式检查（不保存或显示画面）：

```bash
./gradlew :lib_business:lib_camera:verifyDesktopCamera -PcameraDevice=0
./gradlew :desktopApp:verifyCameraVision -PcameraDevice=0 -PvisionModel=/absolute/path/yolo26m.onnx
```

前者检查真实帧、停止和重新打开；后者将三帧真实画面交给本地 YOLO，打印结果数量并释放相机/推理资源，不读取或修改用户保存的配置。

iOS 在 Mac 上可构建：

```bash
./gradlew :lib_business:lib_camera:compileKotlinIosArm64 :lib_business:lib_camera:compileKotlinIosSimulatorArm64
```

本次已完成 Android 构建、Windows 真实相机采集/重开及相机→YOLO 调用、离线交互和像素测试，并用 Kotlin/Native 2.4.20 的真实 iOS 依赖与 Compose 编译插件生成 iOS ARM64 相机模块 `.klib`。当前 Windows 环境不能完成 Xcode 应用链接和 iOS 真机权限/方向/后台行为验证；macOS/Linux 设备运行与打包也须在对应环境验证。

平台 API 依据：[CameraX 图像分析](https://developer.android.com/media/camera/camerax/analyze)、[AVCaptureVideoDataOutput](https://developer.apple.com/documentation/avfoundation/avcapturevideodataoutput)、[OpenCV VideoCapture](https://docs.opencv.org/4.x/d8/dfe/classcv_1_1VideoCapture.html)。
