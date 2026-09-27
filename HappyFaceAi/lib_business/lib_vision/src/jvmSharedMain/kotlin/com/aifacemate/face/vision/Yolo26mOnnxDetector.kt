package com.aifacemate.face.vision

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Android/JVM ONNX Runtime backend. Keep one instance per camera session; do not create one per frame. */
class Yolo26mOnnxDetector(modelPath: String) : VisionDetector, AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(modelPath)
    private val inputName: String = try {
            require(session.numInputs == 1L && session.numOutputs == 1L) { "Expected one detection input and one output" }
            val input = session.inputInfo.entries.single()
            val inputTensor = input.value.info as? TensorInfo ?: error("Input must be a tensor")
            require(inputTensor.type == OnnxJavaType.FLOAT && inputTensor.shape.contentEquals(longArrayOf(1, 3, 640, 640))) {
                "Expected FP32 [1,3,640,640] input; export yolo26m with imgsz=640, dynamic=False"
            }
            val outputTensor = session.outputInfo.values.single().info as? TensorInfo ?: error("Output must be a tensor")
            val shape = outputTensor.shape
            require(outputTensor.type == OnnxJavaType.FLOAT && shape.size == 3 && shape[0] == 1L && shape[1] > 0 && shape[2] == 6L) {
                "Expected FP32 [1,N,6] output; export yolo26m with nms=False (not the default raw head)"
            }
            input.key
    } catch (error: Throwable) {
        session.close()
        throw error
    }

    @Synchronized
    override fun detect(frame: RgbaFrame, confidenceThreshold: Float): List<VisionDetection> {
        val prepared = Yolo26mCodec.prepare(frame)
        val buffer = ByteBuffer.allocateDirect(prepared.chw.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(prepared.chw)
        buffer.rewind()
        OnnxTensor.createTensor(environment, buffer, longArrayOf(1, 3, 640, 640)).use { input ->
            session.run(mapOf(inputName to input)).use { outputs ->
                val tensor = outputs[0] as? OnnxTensor ?: error("YOLO output must be a float tensor")
                val values = tensor.floatBuffer ?: error("YOLO output must be float32")
                val rows = FloatArray(values.remaining())
                values.get(rows)
                require(rows.size % 6 == 0) { "YOLO output must have six values per detection" }
                return Yolo26mCodec.decode(rows, prepared.letterbox, confidenceThreshold)
            }
        }
    }

    @Synchronized
    override fun close() = session.close()
}
