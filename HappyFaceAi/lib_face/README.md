# lib_face · 机器人脸部表情系统

猫脸渲染与表情动画的独立 Kotlin Multiplatform 库。Android / iOS / Desktop 共用同一套参数与渲染代码，
宿主 App 只需要调用 `FaceStage(...)` 或 `rememberAnimatedFace(...)`。

## 模块结构

```
lib_face/src/commonMain/kotlin/com/aifacemate/face/
├── model/     表情编号、渲染参数、表情库、参数校验
├── anim/      过渡混合、眨眼、呼吸等纯函数与动画状态机
├── render/    几何布局、矢量图形、Canvas 渲染器
└── ui/        动画宿主 FaceStage 与调试台 FacePreviewScreen
```

设计原则：**渲染器不认识表情编号，表情库不认识画布尺寸**。
新增表情 = 在 `ExpressionLibrary` 里加一条参数，不需要改渲染代码。

## 当前状态（Alpha 1）

- 25 个表情位全部有参数，其中 5 个已按设计图精修并冻结：Idle / Happy / Curious / Sleeping / Greeting
- 两种渲染表面：`DEVICE_SCREEN`（实机 4.0 英寸 16:9 屏铺满，只有脸）与 `PRODUCT_MOCKUP`（整机概念图，带发光耳朵与外壳）
- 待机生命感：随机间隔眨眼（2.4s ~ 6.4s）+ 呼吸起伏；弧形眼表情不参与眨眼
- 表情切换：420ms 缓入缓出过渡，数值参数插值、眼型/嘴型过半切换、情绪符号淡入淡出
- 全部图形为矢量绘制，不依赖图片与字体，可等比渲染到 4 英寸屏幕或任意预览窗口

## 常用命令

```bash
./gradlew :lib_face:jvmTest                 # 逻辑层测试（参数、动画、布局）
./gradlew :desktopApp:run                   # 打开调试台，实时切换表情
./gradlew :desktopApp:exportFaceSheets      # 离屏导出 25 张表情图 + 总览图
./gradlew :androidApp:assembleDebug         # 打包 Android 原型
```

导出的图片在 `desktopApp/build/face-sheets/`，可直接与 `assets/04_伴侣机器人表情系统.png` 对照走查。

## 实机形态与调试入口

真机上跑的是 `FaceDisplay`：整块屏只显示一张猫脸，没有控件、没有标题、没有外壳。
耳朵、侧灯环是机身上的硬件灯效，**不在屏幕里**，因此 `DEVICE_SCREEN` 下不绘制耳朵，
表情参数里的 `earGlow` 在实机屏幕上不生效（有测试锁住这个产品决定）。

调试入口：**在实机全屏界面长按屏幕**进入 `FacePreviewScreen`，可以切表情、走查 25 个表情位、
在「整机概念图 / 实机屏幕」两种渲染表面之间切换；点「回到实机全屏」返回。

## 下一步（按 PRD 的 Alpha 阶段）

1. Alpha 2：按设计图继续精修猫脸比例、耳朵与屏幕外壳细节
2. Alpha 3：口型联动（`MouthParams.openness`）、说话与倾听的循环动画、更丰富的待机小动作
3. Alpha 4：把其余 20 个表情位逐个精修，支持导出 Face Pack
4. Alpha 5：接入 ASR / LLM / TTS，让大模型直接输出 `emotion` 驱动本库
