package com.aifacemate.face.vision

import java.io.File
import kotlinx.coroutines.runBlocking

/** Explicit smoke check using real HTTPS, SHA-256 verification and ONNX Runtime, isolated from user models. */
fun main(args: Array<String>) = runBlocking {
    require(args.size == 1) { "Pass an isolated verification directory" }
    val directory = File(args.single())
    val model = ManagedYolo26m(directory = { directory })
    var last = -1
    model.install { received, total ->
        val percent = (received * 100 / total).toInt()
        val bucket = percent / 10
        if (bucket != last) { println("Download $percent%"); last = bucket }
    }
    check(model.installed())
    val frame = RgbaFrame(640, 640, ByteArray(640 * 640 * 4) { 127 })
    try {
        repeat(2) {
            val detections = model.detect(frame, VisionPreferences(yoloEnabled = true))
            check(detections.all { detection -> detection.confidence.isFinite() && detection.classId in 0..79 })
            println("PASS actual cached YOLO inference: frame ${it + 1}, ${detections.size} detections")
        }
        model.unload()
        check(model.installed())
        println("PASS unload keeps the downloaded model")
        model.remove()
        check(!model.installed())
        println("PASS remove deletes only the isolated cached model")
    } finally { model.unload() }
}
