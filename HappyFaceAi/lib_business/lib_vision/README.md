# YOLO26m 视觉检测

`lib_vision` 提供跨平台帧/框契约、COCO 80 类标签、letterbox 预处理和 `[1,N,6]` 解码；Android 与桌面 JVM 使用 ONNX Runtime 执行本地推理。这是**目标检测**，不是分割、OCR 或人脸识别。

## 模型准备

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

```bash
./gradlew :desktopApp:runYolo26m \
  -PvisionModel=/absolute/path/yolo26m.onnx \
  -PvisionImage=/absolute/path/image.jpg
```

输出为类别、置信度与**原图像素坐标**，不会自动下载权重或读取摄像头。

## Android 调用

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

- Android/JVM 已提供单帧真实推理，桌面端提供图片验证入口；相机采集、视觉页面、语音/LLM 视觉工具调用尚未接入。
- iOS 只有通用数据契约；推理需要后续接入 Core ML 或 ONNX Runtime iOS 后端。
- 默认 COCO 80 类仅匹配官方检测权重；自训练权重的类别名称需自行适配。
