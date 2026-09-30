package com.aifacemate.face.vision

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import com.aifacemate.face.capability.DevicePlatform
import com.aifacemate.face.capability.currentDeviceEnvironment

/** One ONNX Runtime session with a single fixed-shape float input. Not thread-safe; callers serialize runs. */
internal class OnnxModel(path: String, description: String) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = OrtSession.SessionOptions().use { options ->
        if (currentDeviceEnvironment().platform == DevicePlatform.Android) {
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            options.addConfigEntry("session.intra_op.allow_spinning", "0")
            options.addConfigEntry("session.inter_op.allow_spinning", "0")
        }
        environment.createSession(path, options)
    }
    private var buffer: FloatBuffer? = null
    val inputName: String
    val inputShape: LongArray
    val outputTensors: Map<String, TensorInfo>
    /** Output name → element count; dynamic dimensions count as 1. */
    val outputSizes: Map<String, Long>

    init {
        try {
            require(session.numInputs == 1L) { "$description 应只有一个输入" }
            val (name, info) = session.inputInfo.entries.single()
            val tensor = info.info as? TensorInfo ?: error("$description 的输入不是张量")
            require(tensor.type == OnnxJavaType.FLOAT && tensor.shape.all { it > 0 }) {
                "$description 需要固定尺寸的 FP32 输入，实际为 ${tensor.type} ${tensor.shape.contentToString()}"
            }
            inputName = name
            inputShape = tensor.shape
            outputTensors = session.outputInfo.mapValues { (outputName, value) ->
                val output = value.info as? TensorInfo ?: error("$description 的输出 $outputName 不是张量")
                require(output.type == OnnxJavaType.FLOAT) { "$description 的输出 $outputName 需要 FP32，实际为 ${output.type}" }
                output
            }
            outputSizes = outputTensors.mapValues { (_, tensor) ->
                tensor.shape.fold(1L) { total, dimension -> total * maxOf(dimension, 1L) }
            }
        } catch (failure: Throwable) {
            session.close()
            throw failure
        }
    }

    fun requireInput(shape: LongArray, description: String) = require(inputShape.contentEquals(shape)) {
        "$description 输入应为 ${shape.contentToString()}，实际为 ${inputShape.contentToString()}"
    }

    /** First output with exactly [size] elements, used when exporters name outputs differently. */
    fun outputOfSize(size: Long, description: String): String =
        outputSizes.entries.firstOrNull { it.value == size }?.key ?: error("$description 缺少 $size 个值的输出")

    fun run(input: FloatArray, outputs: Set<String> = outputSizes.keys): Map<String, FloatArray> {
        val direct = buffer?.takeIf { it.capacity() == input.size }
            ?: ByteBuffer.allocateDirect(input.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
                .also { buffer = it }
        direct.clear()
        direct.put(input)
        direct.rewind()
        return OnnxTensor.createTensor(environment, direct, inputShape).use { tensor ->
            session.run(mapOf(inputName to tensor), outputs).use { result ->
                result.associate { (name, value) ->
                    val values = (value as OnnxTensor).floatBuffer ?: error("模型输出 $name 不是 FP32")
                    name to FloatArray(values.remaining()).also(values::get)
                }
            }
        }
    }

    override fun close() = session.close()
}
