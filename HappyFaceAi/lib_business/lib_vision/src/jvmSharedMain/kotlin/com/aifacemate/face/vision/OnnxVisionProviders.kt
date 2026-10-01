package com.aifacemate.face.vision

import com.aifacemate.face.capability.Availability
import com.aifacemate.face.capability.CpuArchitecture
import com.aifacemate.face.capability.DeviceEnvironment
import com.aifacemate.face.capability.DevicePlatform
import com.aifacemate.face.capability.ModelSpec
import com.aifacemate.face.capability.ModelStore
import com.aifacemate.face.capability.OperatingSystem
import com.aifacemate.face.capability.Provider
import com.aifacemate.face.capability.currentDeviceEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ONNX Runtime providers shared by Android and desktop JVM hosts, including RK3566 boards on either OS. */
fun onnxVisionProviders(store: ModelStore, environment: DeviceEnvironment = currentDeviceEnvironment()): List<Provider<*>> =
    yolo26Variants(environment).map { Yolo26ObjectDetectorProvider(store, it) } + listOf(
    YunetFaceDetectorProvider(store),
    MobileFaceNetExpressionProvider(store),
    MediaPipeHandLandmarkerProvider(store),
)

/** The native runtimes packaged by `onnxruntime-android` and the desktop `onnxruntime` jar. */
internal fun onnxRuntimeAvailable(environment: DeviceEnvironment): Boolean = when (environment.platform) {
    DevicePlatform.Android -> environment.architecture in setOf(CpuArchitecture.Arm64, CpuArchitecture.X86_64)
    DevicePlatform.Desktop -> when (environment.operatingSystem) {
        OperatingSystem.Windows -> environment.architecture == CpuArchitecture.X86_64
        OperatingSystem.MacOs, OperatingSystem.Linux -> environment.architecture in setOf(CpuArchitecture.X86_64, CpuArchitecture.Arm64)
        else -> false
    }
    DevicePlatform.Ios -> false
}

/** Managed models are checked by size here and by SHA-256 when opened. */
internal abstract class OnnxProvider<C : AutoCloseable>(
    protected val store: ModelStore,
    final override val models: List<ModelSpec>,
) : Provider<C> {
    override fun availability(environment: DeviceEnvironment): Availability {
        if (!onnxRuntimeAvailable(environment)) return Availability.Unsupported("此设备暂不支持 ONNX Runtime 本地推理")
        val missing = models.filterNot(store::installed)
        return if (missing.isEmpty()) Availability.Ready else Availability.NeedsModels(missing)
    }

    protected suspend fun load(model: ModelSpec, description: String): OnnxModel {
        val path = store.verifiedPath(model)
        // Returning to the caller's dispatcher can be cancelled after the native session was created.
        // Keep ownership here until withContext has successfully delivered it to the caller.
        var created: OnnxModel? = null
        try {
            return withContext(Dispatchers.IO) { OnnxModel(path, description).also { created = it } }
        } catch (failure: Throwable) {
            runCatching { created?.close() }.onFailure { failure.addSuppressed(it) }
            throw failure
        }
    }
}

/** Same COCO classes and `[1,300,6]` output; they trade accuracy for compute (5.5 / 20.9 / 68.4 GFLOPs). */
enum class Yolo26Variant(val model: ModelSpec, val label: String) {
    Nano(VisionModels.yolo26n, "最快"),
    Small(VisionModels.yolo26s, "均衡"),
    Medium(VisionModels.yolo26m, "最准"),
}

/**
 * Device default first. Boards with four cores or fewer, such as RK3566, run CPU FP32 and need the smallest
 * model; other phones get the balanced one; desktops have the headroom for the most accurate one.
 */
internal fun yolo26Variants(environment: DeviceEnvironment): List<Yolo26Variant> = when {
    environment.processors <= 4 -> listOf(Yolo26Variant.Nano, Yolo26Variant.Small, Yolo26Variant.Medium)
    environment.platform == DevicePlatform.Android -> listOf(Yolo26Variant.Small, Yolo26Variant.Nano, Yolo26Variant.Medium)
    else -> listOf(Yolo26Variant.Medium, Yolo26Variant.Small, Yolo26Variant.Nano)
}

internal class Yolo26ObjectDetectorProvider(store: ModelStore, private val variant: Yolo26Variant) :
    OnnxProvider<ObjectDetector>(store, listOf(variant.model)) {
    /** Stable: `vision.objects.yolo26m-onnx` was the only detector before n and s were added. */
    override val id = "vision.objects.${variant.model.id}-onnx"
    override val capability = ObjectDetector
    override val displayName = variant.model.id.replaceFirst("yolo", "YOLO")
    override val description = "COCO 80 类 · ${variant.label}"
    override suspend fun open(): ObjectDetector = Yolo26OnnxObjectDetector(load(variant.model, "YOLO 模型"))
}

internal class YunetFaceDetectorProvider(store: ModelStore) : OnnxProvider<FaceDetector>(store, listOf(VisionModels.yunet)) {
    override val id = "vision.face.yunet-onnx"
    override val capability = FaceDetector
    override val displayName = "YuNet"
    override val description = "人脸检测"
    override suspend fun open(): FaceDetector = YunetFaceDetector(load(VisionModels.yunet, "YuNet 人脸检测模型"))
}

internal class MobileFaceNetExpressionProvider(store: ModelStore) :
    OnnxProvider<ExpressionClassifier>(store, listOf(VisionModels.mobileFaceNetExpression)) {
    override val id = "vision.expression.mobilefacenet-onnx"
    override val capability = ExpressionClassifier
    override val displayName = "MobileFaceNet"
    override val description = "七类表情"
    override suspend fun open(): ExpressionClassifier =
        MobileFaceNetClassifier(load(VisionModels.mobileFaceNetExpression, "表情分类模型"))
}

internal class MediaPipeHandLandmarkerProvider(store: ModelStore) :
    OnnxProvider<HandLandmarker>(store, listOf(VisionModels.palmDetection, VisionModels.handLandmarks)) {
    override val id = "vision.hands.mediapipe-onnx"
    override val capability = HandLandmarker
    override val displayName = "MediaPipe 手部关键点"
    override val description = "掌心检测 + 21 点骨架"
    override suspend fun open(): HandLandmarker {
        val palm = load(VisionModels.palmDetection, "手掌检测模型")
        val hand = try { load(VisionModels.handLandmarks, "手部关键点模型") } catch (failure: Throwable) { palm.close(); throw failure }
        return MediaPipeHandLandmarker(palm, hand)
    }
}

/** Closes the model when the wrapper rejects its shape, so an unexpected export never leaks a session. */
private inline fun <T> OnnxModel.validated(check: OnnxModel.() -> T): T =
    try { check() } catch (failure: Throwable) { close(); throw failure }

class Yolo26OnnxObjectDetector internal constructor(private val model: OnnxModel) : ObjectDetector {
    /** For tools and tests with a local export; the app opens detectors through the provider registry. */
    constructor(modelPath: String) : this(OnnxModel(modelPath, "YOLO 模型"))

    private val output = model.validated {
        requireInput(longArrayOf(1, 3, Yolo26mCodec.inputSize.toLong(), Yolo26mCodec.inputSize.toLong()), "YOLO 模型")
        val (name, tensor) = outputTensors.entries.singleOrNull() ?: error("YOLO 模型应只有一个输出")
        val shape = tensor.shape
        require(shape.size == 3 && shape[0] == 1L && shape[1] > 0 && shape[2] == 6L) {
            "YOLO 模型需输出 FP32 [1,N,6]，实际为 ${shape.contentToString()}，导出时请设置 nms=False"
        }
        name
    }

    override suspend fun detect(frame: RgbaFrame, minConfidence: Float): List<DetectedObject> = withContext(Dispatchers.Default) {
        synchronized(model) {
            val prepared = Yolo26mCodec.prepare(frame)
            Yolo26mCodec.decode(model.run(prepared.chw, setOf(output)).getValue(output), prepared.letterbox, minConfidence)
        }
    }

    override fun close() = model.close()
}

internal class YunetFaceDetector(private val model: OnnxModel) : FaceDetector {
    init {
        model.validated {
            requireInput(longArrayOf(1, 3, 640, 640), "YuNet 人脸检测模型")
            require(YunetCodec.outputNames.all(outputSizes::containsKey)) { "YuNet 人脸检测模型输出不完整" }
        }
    }

    override suspend fun detect(frame: RgbaFrame): List<DetectedFace> = withContext(Dispatchers.Default) {
        synchronized(model) {
            val input = YunetCodec.prepare(frame)
            YunetCodec.decode(model.run(input.chw, YunetCodec.outputNames.toSet()), input, frame.width, frame.height)
        }
    }

    override fun close() = model.close()
}

internal class MobileFaceNetClassifier(private val model: OnnxModel) : ExpressionClassifier {
    private val output = model.validated {
        requireInput(longArrayOf(1, 3, 112, 112), "表情分类模型")
        outputOfSize(ExpressionCodec.classes.size.toLong(), "表情分类模型")
    }

    override suspend fun classify(frame: RgbaFrame, face: DetectedFace): ExpressionResult? = withContext(Dispatchers.Default) {
        synchronized(model) {
            val input = ExpressionCodec.prepare(frame, face) ?: return@withContext null
            ExpressionCodec.decode(model.run(input, setOf(output)).getValue(output))
        }
    }

    override fun close() = model.close()
}

internal class MediaPipeHandLandmarker(private val palm: OnnxModel, private val hand: OnnxModel) : HandLandmarker {
    private val deltas: String
    private val scores: String
    private val landmarks: String
    private val presence: String

    init {
        try {
            palm.requireInput(longArrayOf(1, 192, 192, 3), "手掌检测模型")
            hand.requireInput(longArrayOf(1, 224, 224, 3), "手部关键点模型")
            deltas = palm.outputOfSize(PalmCodec.anchorCount * 18L, "手掌检测模型")
            scores = palm.outputOfSize(PalmCodec.anchorCount.toLong(), "手掌检测模型")
            // The export has two 63-value outputs (image and world landmarks) and two scalars; prefer the known names.
            landmarks = "Identity".takeIf { hand.outputSizes[it] == 63L } ?: hand.outputOfSize(63, "手部关键点模型")
            presence = "Identity_1".takeIf { hand.outputSizes[it] == 1L } ?: hand.outputOfSize(1, "手部关键点模型")
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    override suspend fun detect(frame: RgbaFrame): List<DetectedHand> = withContext(Dispatchers.Default) {
        synchronized(this@MediaPipeHandLandmarker) {
            val input = PalmCodec.prepare(frame)
            val palmOutputs = palm.run(input.nhwc, setOf(deltas, scores))
            PalmCodec.decode(palmOutputs.getValue(deltas), palmOutputs.getValue(scores), input, frame.width, frame.height)
                .mapNotNull { detected ->
                    val roi = HandCodec.roi(detected) ?: return@mapNotNull null
                    val outputs = hand.run(HandCodec.prepare(frame, roi), setOf(landmarks, presence))
                    val points = HandCodec.landmarks(outputs.getValue(landmarks), roi, frame.width, frame.height)
                    DetectedHand(points.bounds().clamped(), outputs.getValue(presence).single(), points)
                }
        }
    }

    override fun close() {
        palm.close()
        hand.close()
    }
}
