package com.aifacemate.face.tools

import com.aifacemate.face.vision.RgbaFrame
import com.aifacemate.face.vision.Yolo26mOnnxDetector
import java.io.File
import javax.imageio.ImageIO

/** Local smoke check: model path + PNG/JPEG path; no network or webcam required. */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: runYolo26m <yolo26m.onnx> <image.png>" }
    val image = ImageIO.read(File(args[1])) ?: error("Could not decode image: ${args[1]}")
    require(image.width.toLong() * image.height <= Int.MAX_VALUE / 4) { "Image is too large" }
    val argb = IntArray(image.width * image.height)
    image.getRGB(0, 0, image.width, image.height, argb, 0, image.width)
    val rgba = ByteArray(argb.size * 4)
    argb.forEachIndexed { i, pixel ->
        rgba[i * 4] = (pixel ushr 16).toByte()
        rgba[i * 4 + 1] = (pixel ushr 8).toByte()
        rgba[i * 4 + 2] = pixel.toByte()
        rgba[i * 4 + 3] = (pixel ushr 24).toByte()
    }
    Yolo26mOnnxDetector(args[0]).use { detector ->
        detector.detect(RgbaFrame(image.width, image.height, rgba)).forEach { detection ->
            println("${detection.label} ${detection.confidence} ${detection.box}")
        }
    }
}
