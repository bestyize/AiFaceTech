package com.aifacemate.face.camera

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DesktopCameraControllerTest {
    @Test
    fun invalidDeviceAndClosedOwnerNeverOpenHardware() = runBlocking {
        val camera = DesktopCameraController()
        camera.start("invalid")
        assertEquals(CameraStatus.Error, camera.state.value.status)
        assertNull(camera.frames.value)
        camera.start("16")
        assertEquals(CameraStatus.Error, camera.state.value.status)
        camera.stop()
        assertEquals(CameraStatus.Idle, camera.state.value.status)
        camera.close()
        camera.start()
        assertEquals(CameraStatus.Idle, camera.state.value.status)
        assertNull(camera.frames.value)
    }
}
