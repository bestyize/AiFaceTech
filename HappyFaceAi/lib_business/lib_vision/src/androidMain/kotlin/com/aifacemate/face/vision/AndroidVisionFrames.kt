package com.aifacemate.face.vision

import android.graphics.Bitmap
import java.io.File

/** Copies a Bitmap into the platform-neutral RGBA input contract. Camera bitmaps should be opaque. */
fun Bitmap.toVisionFrame(): RgbaFrame {
    require(width.toLong() * height <= Int.MAX_VALUE / 4) { "Bitmap is too large" }
    val argb = IntArray(width * height)
    getPixels(argb, 0, width, 0, 0, width, height)
    val rgba = ByteArray(argb.size * 4)
    for (i in argb.indices) {
        val pixel = argb[i]
        rgba[i * 4] = (pixel ushr 16).toByte()
        rgba[i * 4 + 1] = (pixel ushr 8).toByte()
        rgba[i * 4 + 2] = pixel.toByte()
        rgba[i * 4 + 3] = (pixel ushr 24).toByte()
    }
    return RgbaFrame(width, height, rgba)
}

/** Pass a private local .onnx file. For large models, prefer a path over loading a ByteArray. */
fun openYolo26mDetector(modelFile: File): VisionDetector {
    require(modelFile.isFile && modelFile.canRead()) { "YOLO26m ONNX model is not readable: ${modelFile.path}" }
    return Yolo26mOnnxDetector(modelFile.absolutePath)
}
