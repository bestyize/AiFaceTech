package com.aifacemate.face.camera

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Explicit device check, never part of ordinary tests; no images are displayed or saved. */
fun main(args: Array<String>) = runBlocking {
    val camera = DesktopCameraController()
    try {
        camera.start(args.firstOrNull() ?: "0")
        val frame = withTimeout(15_000) {
            while (camera.state.value.status == CameraStatus.Starting) delay(25)
            check(camera.state.value.status == CameraStatus.Running) { camera.state.value.error ?: "Camera failed to start" }
            camera.frames.first { it != null }!!
        }
        check(frame.rgba.size == frame.width * frame.height * 4)
        println("PASS actual desktop capture: ${frame.width}x${frame.height}, RGBA8888")
        camera.stop()
        check(camera.frames.value == null && camera.state.value.status == CameraStatus.Idle)
        println("PASS stop clears frames and releases the native capture")
        camera.start(args.firstOrNull() ?: "0")
        withTimeout(15_000) { camera.frames.first { it != null } }
        println("PASS device can be reopened after stop")
    } finally { camera.stop(); camera.close() }
}
