package com.aifacemate.face.vision

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Only fixed-size, FP32, one-to-one YOLO26 object detection exports are supported. */
object Yolo26mCodec {
    const val inputSize = 640
    private const val channels = 3
    private const val background = 114f / 255f

    data class Letterbox(
        val sourceWidth: Int,
        val sourceHeight: Int,
        val resizedWidth: Int,
        val resizedHeight: Int,
        val left: Int,
        val top: Int,
    ) {
        // Resize is rounded to integer pixels, so use the actual per-axis scale when restoring boxes.
        val scaleX: Float get() = resizedWidth.toFloat() / sourceWidth
        val scaleY: Float get() = resizedHeight.toFloat() / sourceHeight
    }

    data class Input(val chw: FloatArray, val letterbox: Letterbox)

    fun letterbox(width: Int, height: Int): Letterbox {
        require(width > 0 && height > 0)
        val scale = min(inputSize.toFloat() / width, inputSize.toFloat() / height)
        val resizedWidth = (width * scale).roundToInt().coerceIn(1, inputSize)
        val resizedHeight = (height * scale).roundToInt().coerceIn(1, inputSize)
        return Letterbox(
            width, height, resizedWidth, resizedHeight,
            (inputSize - resizedWidth) / 2, (inputSize - resizedHeight) / 2,
        )
    }

    /** Bilinear RGBA → RGB/255, CHW float32, with 114-valued letterbox padding. */
    fun prepare(frame: RgbaFrame): Input {
        val transform = letterbox(frame.width, frame.height)
        val plane = inputSize * inputSize
        val output = FloatArray(channels * plane) { background }
        for (y in 0 until transform.resizedHeight) {
            val srcY = ((y + 0.5f) / transform.scaleY - 0.5f).coerceIn(0f, (frame.height - 1).toFloat())
            val y0 = floor(srcY).toInt()
            val y1 = min(y0 + 1, frame.height - 1)
            val fy = srcY - y0
            for (x in 0 until transform.resizedWidth) {
                val srcX = ((x + 0.5f) / transform.scaleX - 0.5f).coerceIn(0f, (frame.width - 1).toFloat())
                val x0 = floor(srcX).toInt()
                val x1 = min(x0 + 1, frame.width - 1)
                val fx = srcX - x0
                val dst = (y + transform.top) * inputSize + (x + transform.left)
                val a = (y0 * frame.width + x0) * 4
                val b = (y0 * frame.width + x1) * 4
                val c = (y1 * frame.width + x0) * 4
                val d = (y1 * frame.width + x1) * 4
                for (ch in 0 until channels) {
                    val top = (frame.pixels[a + ch].toInt() and 255) * (1f - fx) +
                        (frame.pixels[b + ch].toInt() and 255) * fx
                    val bottom = (frame.pixels[c + ch].toInt() and 255) * (1f - fx) +
                        (frame.pixels[d + ch].toInt() and 255) * fx
                    output[ch * plane + dst] = (top * (1f - fy) + bottom * fy) / 255f
                }
            }
        }
        return Input(output, transform)
    }

    /** Each row is [x1,y1,x2,y2,confidence,classId], in letterboxed input pixels. No NMS. */
    fun decode(
        rows: FloatArray,
        transform: Letterbox,
        confidenceThreshold: Float = 0.25f,
    ): List<DetectedObject> {
        require(rows.size % 6 == 0) { "Expected YOLO26 nms=False output shaped [1,N,6]" }
        require(confidenceThreshold.isFinite() && confidenceThreshold in 0f..1f)
        val result = ArrayList<DetectedObject>()
        for (offset in rows.indices step 6) {
            val confidence = rows[offset + 4]
            val classValue = rows[offset + 5]
            if (!confidence.isFinite() || confidence < confidenceThreshold || confidence > 1f ||
                !classValue.isFinite() || classValue < 0f || classValue >= Coco80.names.size || classValue != classValue.toInt().toFloat()
            ) continue
            val x1 = rows[offset]
            val y1 = rows[offset + 1]
            val x2 = rows[offset + 2]
            val y2 = rows[offset + 3]
            if (!x1.isFinite() || !y1.isFinite() || !x2.isFinite() || !y2.isFinite()) continue
            val left = ((x1 - transform.left) / transform.scaleX).coerceIn(0f, transform.sourceWidth.toFloat())
            val top = ((y1 - transform.top) / transform.scaleY).coerceIn(0f, transform.sourceHeight.toFloat())
            val right = ((x2 - transform.left) / transform.scaleX).coerceIn(0f, transform.sourceWidth.toFloat())
            val bottom = ((y2 - transform.top) / transform.scaleY).coerceIn(0f, transform.sourceHeight.toFloat())
            if (right <= left || bottom <= top) continue
            val classIndex = classValue.toInt()
            result.add(DetectedObject(Coco80.names[classIndex], classIndex, NormalizedBox(
                left / transform.sourceWidth, top / transform.sourceHeight,
                right / transform.sourceWidth, bottom / transform.sourceHeight,
            ), confidence))
        }
        return result
    }
}
