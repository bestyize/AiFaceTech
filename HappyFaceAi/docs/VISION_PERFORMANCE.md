# 手机视觉监测：冷启动手势与持续负载

## 发现

2026-09-30，手机已开启目标检测、表情识别、手势识别；保存的阈值分别为 0.65、0.6、0.4。猫脸适配层曾额外要求手部/目标置信度至少 0.7，导致设置与实际行为不一致。三项任务又串行运行在同一帧，重型 YOLO 及模型初始化延迟会让手势结果超过行为层的一秒有效期。

Android ONNX Runtime 原来使用默认 SessionOptions，每个模型都保留自己的线程池并允许空转。一次 dumpsys cpuinfo 采样显示应用 CPU 为 361%（按单核 100% 计），不能直接解释为温度或耗电测量。

## 当前处理

- 移除猫脸适配层额外的 0.7 阈值，沿用感知管线已执行的配置过滤。
- 猫脸页将手势/表情与 YOLO 独立调度，分别在每轮后休息 350ms / 1500ms；YOLO 不阻塞点赞证据。
- 不同流的事件分别维护时间顺序，未分析的任务传 null，不当作阴性证据。
- Android SessionOptions 设置 intra-op=2、inter-op=1，关闭两类线程池 spinning；仍使用 CPU、FP32 模型。
- 猫脸状态在首个感知结果产生前显示“识别启动中”，而非仅凭相机打开就标为识别可用。
- FaceVision 日志只输出推理时长、结果年龄、手数量和点赞数量，不记录图像、识别文字或身份信息。

## 验证与限制

行为规则、视觉、共享层 JVM 测试及 Android Debug 构建通过；新增测试覆盖低于 0.7 的已分类点赞、无初始化结果、物体流不清除手势证据。

手机更新后直接冷启动：交互链路日志在一次采样中耗时 26–171ms，后续记录识别到两次点赞。top 两次短时 CPU 采样约 105% / 110%。旧值来自 dumpsys、新值来自 top，采样窗口和场景不同，不可当作严格的降幅或温升对照。尚未完成长时间同场景功耗、表面温度和官方 APK 对照测试。

用户实机反馈确认：这两次点赞都切换到了赞同表情。

手机安装的 Ultralytics YOLO APK 为 0.6.13。官方当前文档描述 LiteRT GPU→CPU 路径和可用的量化模型，但仅凭型号 YOLO26m 或版本号不能确认该 APK 此时实际使用的后端、精度和输入尺寸。我们的 ONNX 实现没有设置加速执行提供者，不能宣称已达到官方实现的能效。

参考：[ONNX Runtime 线程配置](https://onnxruntime.ai/docs/performance/tune-performance/threading.html)、[Ultralytics GPU 配置](https://github.com/ultralytics/yolo-flutter-app/blob/main/doc/use_gpu_feature.md)。
