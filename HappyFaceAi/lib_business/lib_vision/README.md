# YOLO26m 视觉检测

`lib_vision` 提供跨平台帧/框契约、COCO 80 类标签、letterbox 预处理和 `[1,N,6]` 解码；Android 与桌面 JVM 使用 ONNX Runtime 执行本地推理。这是**目标检测**，不是分割、OCR 或人脸识别。

## 模型准备

### 按需下载（Android / Desktop）

生命中心 → 视觉 → YOLO 目标检测 →「下载 YOLO26m 模型」。模型约 82 MB，显示进度，可取消、失败后重试或删除。自定义模型路径留空时使用下载模型；填写路径则使用自己的本地文件，删除下载模型不会删除自定义文件。

默认下载 [prithivMLmods/YOLO26-ONNX](https://huggingface.co/prithivMLmods/YOLO26-ONNX) 发布的 YOLO26m FP32 检测导出，固定提交 `c526dae7a5a5b4f1d79d704903b4406011da2716` 的 `yolo26m/yolo26m.onnx`，文件大小 `81,963,205` 字节，SHA-256 为 `fff318f5bf564af5510997bdbfd248c83dac63313fa55ff35ad4812d5340908e`。已实测兼容现有输入/输出契约，来源是社区导出。需要能访问 Hugging Face 的网络。

下载使用临时 `.part` 文件，验证完成后才放入缓存；取消、网络或校验失败会清理临时文件。Android 保存到应用私有 `noBackupFilesDir/vision/`，桌面保存到 `~/.aifacemate/models/vision/`，版本分别隔离，不打包到 APK 或桌面发行包。

进入设置页、检查下载状态或下载文件均不创建 ONNX 会话。`Yolo26mModel.detect()` 在首次实际识别时校验缓存并创建会话，后续帧复用；更换模型、关闭识别、删除正在使用的下载模型或调用 `unload()` 时关闭会话。通过 Mutex 串行化模型操作与识别，避免删除过程中继续使用模型。

## 本地表情与手势分析

生命中心 → 视觉中分别启用表情识别或手势识别。Android/Desktop 使用 OpenCV DNN 执行本地 ONNX 模型，模型由用户单独按需下载，不打包进应用：表情模型包包含 YuNet 人脸检测和 MobileFaceNet 七类表情分类，合计约 5.0 MB；手势模型包包含 MediaPipe 掌心检测和 21 点手部关键点模型，合计约 8.0 MB。文件下载后可离线分析。资源来自固定版本的 [OpenCV Zoo 人脸模型](https://github.com/opencv/opencv_zoo/tree/4.x/models/face_detection_yunet)、[表情分类模型](https://github.com/opencv/opencv_zoo/tree/4.x/models/facial_expression_recognition)、[掌心检测模型](https://github.com/opencv/opencv_zoo/tree/4.x/models/palm_detection_mediapipe) 和 [手部姿态模型](https://github.com/opencv/opencv_zoo/tree/4.x/models/handpose_estimation_mediapipe)，下载时核对固定长度和 SHA-256。

手部关键点随后由本机几何规则识别点赞、张开手掌、握拳、V 字、指向和捏合。它识别静态手势，不识别挥手等动态动作，也不需要训练专用 YOLO。表情分类输出七类外观标签（愤怒、厌恶、恐惧、开心、平静、难过、惊讶），不能据此推断人的真实情绪或心理状态。

iOS 不下载这些 ONNX 文件，而调用 Apple Vision 在本机获取人脸/手部关键点。表情页目前只显示微笑、张嘴或平静等几何动作线索，不等同于 Android/Desktop 的七类表情分类；手势使用共同的 21 点静态姿势规则。iOS YOLO 推理暂未接入。

### 自定义导出

在安装了 Ultralytics 的 Python 环境手动导出官方 COCO 检测权重：

```python
from ultralytics import YOLO

YOLO("yolo26m.pt").export(
    format="onnx", imgsz=640, batch=1, dynamic=False, nms=False,
)
```

需要 **FP32 `[1,3,640,640]` → FP32 `[1,N,6]`**，N 通常为 300，每行依次是 `x1,y1,x2,y2,confidence,class_id`。默认 `nms=None` 的 `[1,84,8400]` 原始预测不能用于本模块；加载时会检查输入/输出形状并报错。不要使用 `yolo26m-seg.pt` 或 `yolo26m-cls.pt`。这个 one-to-one 检测头只过滤置信度，不重复进行 NMS。

模型文件不提交仓库。请根据应用发布方式和 Ultralytics 的 **AGPL-3.0 / Enterprise** 条款确认模型与代码的使用条件，再将 `yolo26m.onnx` 放在应用私有目录或开发者本机。部署到机器人之前请在目标设备实测内存、帧耗时和功耗；官方 CPU ONNX 速度是在指定 Xeon 上测得的，不能代表你的设备。

## 桌面端手动验证

实测按需下载与加载（使用隔离的构建目录，不读取/写入用户模型）：

```bash
./gradlew :lib_business:lib_vision:verifyManagedYolo26m
```

该命令真实下载并校验固定模型、连续执行两帧 ONNX 推理、释放实例后确认缓存仍在，再删除测试缓存。普通 `jvmTest` 无网络，检查实例延迟创建/复用、校验失败、下载取消/失败清理、删除及自定义文件保留。

已有模型与本地图片单独验证：

```bash
./gradlew :desktopApp:runYolo26m \
  -PvisionModel=/absolute/path/yolo26m.onnx \
  -PvisionImage=/absolute/path/image.jpg
```

输出为类别、置信度与**原图像素坐标**，不会自动下载权重或读取摄像头。

## Android 调用

使用视觉设置中保存的配置（调用是挂起函数，内部在后台推理）：

```kotlin
try {
    val result = Yolo26mModel.detect(bitmap.toVisionFrame(), VisionPreferencesStore.load())
    // YOLO 开关关闭时返回空结果；开启但尚未下载时提示先下载，不自动联网。
} finally {
    // 连续帧复用会话，在整个相机/图片识别会话结束时调用一次。
    Yolo26mModel.unload()
}
```

也可继续使用手动管理 detector 的接口：

先将 ONNX 文件复制到 `context.filesDir` 等应用私有可读路径，避免每帧重复读取。将现有摄像头帧转换为 Bitmap 后，在后台线程调用：

```kotlin
val detector = openYolo26mDetector(File(context.filesDir, "yolo26m.onnx"))
try {
    val result = withContext(Dispatchers.Default) {
        detector.detect(bitmap.toVisionFrame(), confidenceThreshold = 0.3f)
    }
    // result.box 是 bitmap 原图的像素坐标。
} finally {
    detector.close()
}
```

`openYolo26mDetector`、`toVisionFrame` 位于 `com.aifacemate.face.vision`。调用方应直接依赖 `:lib_business:lib_vision`。画到裁剪/镜像后的预览时还要转换坐标。复用 detector、在退出页面时关闭；连续帧忙碌时丢帧，避免推理排队。RGBA 输入要求行优先，不使用 Alpha 通道；传入不透明图像。

## 现阶段边界

- Android/Desktop 已提供本地 YOLO、七类表情分类和静态手势分析；生命中心视觉页为三项功能分别提供开关、阈值、按需下载与模型缓存操作，共享摄像头页面按已保存配置绘制结果。iOS 使用系统 Vision 提供表情动作线索和静态手势分析；语音/LLM 视觉工具调用尚未接入。
- `VisionPreferencesStore` 通过 `lib_storage` 持久化 YOLO、表情和手势三组启用偏好、可选模型路径与置信度阈值；默认关闭。空路径选用按需下载的模型，非空路径选用自定义文件。保存校验路径格式，实际推理时才加载并检查模型。
- iOS 本地分析依赖 Apple Vision；YOLO 推理后端仍需后续接入 Core ML 或 ONNX Runtime iOS。
- 默认 COCO 80 类仅匹配官方检测权重；自训练权重的类别名称需自行适配。
