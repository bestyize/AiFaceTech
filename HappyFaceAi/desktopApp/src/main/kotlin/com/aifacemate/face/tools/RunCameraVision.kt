package com.aifacemate.face.tools

import com.aifacemate.face.camera.CameraStatus
import com.aifacemate.face.camera.createDesktopCameraController
import com.aifacemate.face.vision.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.take

/** Explicit live camera-to-YOLO check; frames and detections are never saved or sent anywhere. */
fun main(args: Array<String>) = runBlocking {
    require(args.size == 2 && args[0].isNotBlank()) { "Set -PvisionModel=/absolute/path/yolo26m.onnx and optionally -PcameraDevice=0" }
    val camera = createDesktopCameraController()
    val detector = Yolo26OnnxObjectDetector(args[0])
    try {
        camera.start(args[1])
        withTimeout(30_000) {
            while (camera.state.value.status == CameraStatus.Starting) delay(25)
            check(camera.state.value.status == CameraStatus.Running) { camera.state.value.error ?: "Camera unavailable" }
            camera.frames.filterNotNull().take(3).collect { frame ->
                val found = detector.detect(RgbaFrame(frame.width, frame.height, frame.rgba), minConfidence = 0.25f)
                check(found.all { it.score.isFinite() && it.classIndex in 0..79 })
                println("PASS live camera frame ${frame.sequence}: ${frame.width}x${frame.height}, ${found.size} YOLO detections")
            }
        }
    } finally { camera.stop(); camera.close(); detector.close() }
    println("PASS camera and YOLO resources released")
}
