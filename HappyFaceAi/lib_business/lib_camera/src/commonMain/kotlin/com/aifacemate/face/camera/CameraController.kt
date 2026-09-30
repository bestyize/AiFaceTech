package com.aifacemate.face.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

enum class CameraStatus { Idle, Starting, Running, PermissionDenied, Error }
enum class CameraLens { Front, Back, External }
data class CameraDevice(val id: String, val name: String, val lens: CameraLens)
data class CameraState(
    val status: CameraStatus = CameraStatus.Idle,
    val devices: List<CameraDevice> = emptyList(),
    val deviceId: String? = null,
    val usesDeviceIndex: Boolean = false,
    val error: String? = null,
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
)

data class CapturedPhoto(val location: String)

/** Owned, upright RGBA8888 pixels. Only the displayed preview is mirrored for front cameras. */
class CameraFrame(val width: Int, val height: Int, val rgba: ByteArray, val sequence: Long, val mirrored: Boolean = false) {
    init {
        require(width > 0 && height > 0 && width.toLong() * height * 4 <= Int.MAX_VALUE)
        require(rgba.size == width * height * 4)
    }
}

interface CameraController {
    val state: StateFlow<CameraState>
    /** A single latest frame is retained; slow consumers never accumulate a frame queue. */
    val frames: StateFlow<CameraFrame?>
    suspend fun start(deviceId: String? = null)
    suspend fun stop()
    /** Zooms the camera stream, so preview, perception and photos use the same field of view. */
    suspend fun setZoom(ratio: Float) {}
    /** Saves the current upright frame, cropped and mirrored to match the preview. */
    suspend fun capturePhoto(aspectRatio: Float? = null): CapturedPhoto {
        check(state.value.status == CameraStatus.Running) { "摄像头尚未就绪" }
        return checkNotNull(frames.value) { "等待相机画面后再拍照" }.photoFrame(aspectRatio).savePhoto()
    }
    /** Nonblocking, idempotent owner disposal; releases all native resources. */
    fun close()
}

@Composable
expect fun rememberCameraController(): CameraController
expect fun CameraFrame.toPreviewBitmap(): ImageBitmap
internal expect suspend fun CameraFrame.savePhoto(): CapturedPhoto
