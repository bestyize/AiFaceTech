# lib_vision：可插拔视觉能力

视觉按「能力接口 + 提供者」组织。App 只依赖接口；每个平台在编译时注册自己打包的实现，用户可在设置中选择，将来接入 RKNN、ESP32 远程终端等实现时不需要改动界面和识别流程。通用的提供者、注册表和模型仓库在 [`lib_common/lib_capability`](../../lib_common/lib_capability)。

## 能力接口

| 接口 | 作用 | 输出 |
| --- | --- | --- |
| `ObjectDetector` | 目标检测 | `DetectedObject`：稳定类别名（COCO 英文名）、相对坐标框、置信度 |
| `FaceDetector` | 人脸检测 | `DetectedFace`：相对坐标框、置信度、可选五点关键点 |
| `ExpressionClassifier` | 表情分类 | `ExpressionResult`：`Expression` 稳定 ID（如 `happy`）与置信度 |
| `HandLandmarker` | 手部关键点 | `DetectedHand`：MediaPipe 顺序的 21 个关键点 |
| `GestureClassifier` | 由关键点判断静态手势 | `HandGesture` 稳定 ID（如 `thumbs_up`）；纯逻辑，所有设备共用 |

坐标都以 0–1 表示，(0, 0) 为直立画面的左上角。识别结果只使用英文 ID，中文文字由界面决定，因此可以直接用于设备间协议。

## 已有提供者

| 平台 | 目标检测 | 人脸 / 表情 | 手部 |
| --- | --- | --- | --- |
| Android、桌面 JVM（含 RK3566 Android / Ubuntu） | ONNX Runtime · YOLO26n / s / m | ONNX Runtime · YuNet + MobileFaceNet 七类表情 | ONNX Runtime · MediaPipe 掌心检测 + 21 点关键点 |
| iOS | 暂未接入 | Apple Vision · 人脸检测与微笑 / 张嘴等动作线索 | Apple Vision · 21 点关键点 |

ONNX 提供者只负责运行模型；图像缩放、人脸对齐、YuNet 解码、掌心锚框和去重（NMS）都写成 `commonMain` 里的纯 Kotlin（`ImageSampling.kt`、`FaceCodecs.kt`、`HandCodecs.kt`、`Yolo26mCodec.kt`）。以后换成其他推理后端，比如 RK3566 的 RKNN，只需要实现「运行模型」这一步，前后处理可以复用。

## 识别流程

- `VisionTask`：用户在设置中开关的三项任务，每项由一个或多个能力组成（表情 = 人脸检测 + 表情分类）。
- `PerceptionPipeline`：对单帧运行已开启的任务。首次用到时才打开提供者并复用；某项任务出错时只停止并释放这一项，其他任务继续。
- `PerceptionService`：独立于界面的感知会话，只分析最新帧，忙时丢帧，取消时释放所有提供者。相机页通过它显示识别结果；以后行为核心也可以订阅同一个 `state`。
- `VisionRuntime.registry`：当前平台的提供者注册表。设置里保存的是提供者 ID（`vision.provider.<能力 ID>`），没有保存时使用注册顺序中第一个受支持的实现。

新增一个实现：实现对应接口和 `Provider`，在该平台的 `platformVisionProviders` 里注册即可。

## 模型

全部模型按需下载，不打包进安装包，由统一的 `ModelStore` 下载到临时 `.part` 文件，校验固定的文件大小和 SHA-256 后才保留；取消或失败不会留下半截文件。huggingface.co 无法访问时改用 hf-mirror.com 镜像，内容同样按 SHA-256 校验。镜像对桌面 JVM 的请求会跳回 huggingface.co，因此桌面端仍需能访问 huggingface.co。Android 存放在应用私有 `noBackupFilesDir/vision/`，桌面在 `~/.aifacemate/models/vision/`，目录与之前的版本相同，已下载的模型继续有效。

- YOLO26：[prithivMLmods/YOLO26-ONNX](https://huggingface.co/prithivMLmods/YOLO26-ONNX) 固定提交 `c526dae7` 的社区导出，三个尺寸输出格式相同，在设置的模型卡片中切换：

  | 模型 | 文件 | COCO mAP | 计算量 | 本机实测（M 系列 Mac，640×480） |
  | --- | --- | --- | --- | --- |
  | YOLO26n | 约 9.9 MB | 40.9 | 5.5 GFLOPs | 26 ms |
  | YOLO26s | 约 38 MB | 48.6 | 20.9 GFLOPs | 67 ms |
  | YOLO26m | 约 82 MB | 53.1 | 68.4 GFLOPs | 97 ms |

  默认值按设备选择：4 核及以下（如 RK3566）用 n，其他 Android 用 s，桌面用 m；已下载过的模型优先，不会因此要求重新下载。mAP 与计算量来自 [Ultralytics](https://huggingface.co/Ultralytics/YOLO26)。
- 表情：[YuNet 人脸检测](https://github.com/opencv/opencv_zoo/tree/4.x/models/face_detection_yunet) + [MobileFaceNet 七类表情](https://github.com/opencv/opencv_zoo/tree/4.x/models/facial_expression_recognition)，合计约 5.0 MB。
- 手势：[MediaPipe 掌心检测](https://github.com/opencv/opencv_zoo/tree/4.x/models/palm_detection_mediapipe) + [21 点手部关键点](https://github.com/opencv/opencv_zoo/tree/4.x/models/handpose_estimation_mediapipe)，合计约 8.0 MB。

表情分类只给出七类外观标签（愤怒、厌恶、恐惧、开心、平静、难过、惊讶），不能据此推断真实情绪。手势是本机几何规则识别的静态手势（点赞、张开手掌、握拳、V 字、指向、捏合），不识别挥手等动作。

### 模型文件

应用只加载上面这些固定版本、校验过的模型，设置页不提供自定义模型文件。更换模型时，在 `VisionModels` 中更新文件、大小和 SHA-256，并确认形状仍符合对应提供者的检查。YOLO 需要 FP32 `[1,3,640,640]` → `[1,N,6]`（导出时设置 `nms=False`）：

```python
from ultralytics import YOLO

YOLO("yolo26m.pt").export(format="onnx", imgsz=640, batch=1, dynamic=False, nms=False)
```

桌面工具 `runYolo26m` 和 `verifyCameraVision` 可以直接传入本机导出的 `.onnx` 文件做测试，不经过设置。请根据 Ultralytics 的 AGPL-3.0 / Enterprise 条款确认模型的使用条件。部署到机器人前请在目标设备实测内存、帧耗时和功耗。

## 验证

```bash
./gradlew :lib_common:lib_capability:jvmTest :lib_business:lib_vision:jvmTest
./gradlew :lib_business:lib_vision:verifyVisionProviders
./gradlew :desktopApp:runYolo26m -PvisionModel=/absolute/path/yolo26m.onnx -PvisionImage=/absolute/path/image.jpg
```

- 常规测试离线运行，覆盖图像采样、YuNet / 掌心 / 手部解码、表情对齐、手势规则、提供者选择、按需打开与释放、单项任务失败隔离，以及模型下载校验、取消和清理。
- `verifyVisionProviders` 在构建目录中下载固定版本的模型和 MediaPipe 样例图，用真实 ONNX Runtime 检查点赞、V 字、握拳、指向和开心的人脸，并输出 640×480 帧的耗时。不读取或写入用户模型目录。
