package com.aifacemate.face.camera

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2RGBA
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGRA2RGBA
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2RGBA
import org.bytedeco.opencv.global.opencv_imgproc.cvtColor
import org.bytedeco.opencv.global.opencv_videoio.*
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_videoio.VideoCapture
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

@Composable
actual fun rememberCameraController(): CameraController {
    val controller = remember { createDesktopCameraController() }
    DisposableEffect(controller) { onDispose { controller.close() } }
    return controller
}

/** JVM tools can explicitly own a camera without requiring a Compose window. */
fun createDesktopCameraController(): CameraController = DesktopCameraController()

internal class DesktopCameraController : CameraController {
    private val mutableState = MutableStateFlow(CameraState(usesDeviceIndex = true))
    override val state = mutableState.asStateFlow()
    private val mutableFrames = MutableStateFlow<CameraFrame?>(null)
    override val frames = mutableFrames.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private var captureJob: Job? = null
    @Volatile private var closed = false
    private var sequence = 0L
    private var discovered: List<CameraDevice>? = null

    override suspend fun start(deviceId: String?) = gate.withLock {
        if (closed) return@withLock
        stopCapture()
        val index = if (deviceId == null) 0 else deviceId.toIntOrNull()
        if (index == null || index !in 0..15) {
            mutableState.update { it.copy(status = CameraStatus.Error, error = "摄像头编号须在 0 到 15 之间") }
            return@withLock
        }
        mutableState.update { it.copy(status = CameraStatus.Starting, deviceId = index.toString(), error = null) }
        captureJob = scope.launch {
            var capture: VideoCapture? = null
            var source: Mat? = null
            var output: Mat? = null
            try {
                val camera = VideoCapture().also { capture = it }
                val backend = if (System.getProperty("os.name").lowercase().contains("windows")) CAP_DSHOW else CAP_ANY
                if (discovered == null) {
                    discovered = buildList {
                        for (candidate in 0..3) {
                            currentCoroutineContext().ensureActive()
                            VideoCapture().use { probe ->
                                try {
                                    if (runCatching { probe.open(candidate, backend) }.getOrDefault(false)) {
                                        add(CameraDevice(candidate.toString(), "摄像头 $candidate", CameraLens.External))
                                    }
                                } finally { probe.release() }
                            }
                        }
                    }
                }
                check(camera.open(index, backend)) { "无法打开摄像头 $index，请检查设备、系统相机权限或其它应用占用" }
                camera.set(CAP_PROP_FRAME_WIDTH, 640.0)
                camera.set(CAP_PROP_FRAME_HEIGHT, 480.0)
                camera.set(CAP_PROP_BUFFERSIZE, 1.0)
                val input = Mat().also { source = it }
                val rgba = Mat().also { output = it }
                currentCoroutineContext().ensureActive()
                val devices = discovered.orEmpty().let { list ->
                    if (list.any { it.id == index.toString() }) list else list + CameraDevice(index.toString(), "摄像头 $index", CameraLens.External)
                }
                mutableState.update { it.copy(status = CameraStatus.Running, devices = devices, zoomRatio = 1f, minZoomRatio = 1f, maxZoomRatio = 4f) }
                while (isActive) {
                    check(camera.read(input) && !input.empty()) { "摄像头连接中断，请重新启动" }
                    cvtColor(input, rgba, when (input.channels()) {
                        1 -> COLOR_GRAY2RGBA
                        3 -> COLOR_BGR2RGBA
                        4 -> COLOR_BGRA2RGBA
                        else -> error("不支持的相机像素格式")
                    })
                    val width = rgba.cols()
                    val height = rgba.rows()
                    require(width > 0 && height > 0 && width.toLong() * height * 4 <= Int.MAX_VALUE)
                    val pixels = ByteArray(width * height * 4)
                    if (rgba.isContinuous) rgba.data().get(pixels)
                    else for (row in 0 until height) rgba.ptr(row).get(pixels, row * width * 4, width * 4)
                    mutableFrames.value = CameraFrame(width, height, pixels, ++sequence).digitalZoom(state.value.zoomRatio)
                    delay(80)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                mutableState.update { it.copy(status = CameraStatus.Error, error = failure.message ?: "相机运行库加载失败") }
            } finally {
                runCatching { output?.close() }
                runCatching { source?.close() }
                capture?.let { camera -> try { camera.release() } finally { camera.close() } }
                mutableFrames.value = null
            }
        }
    }

    private suspend fun stopCapture() {
        captureJob?.cancelAndJoin()
        captureJob = null
        mutableFrames.value = null
        mutableState.update { it.copy(status = CameraStatus.Idle, error = null) }
    }
    override suspend fun stop() = withContext(NonCancellable) { gate.withLock { stopCapture() } }
    override suspend fun setZoom(ratio: Float) {
        if (ratio.isFinite() && state.value.status == CameraStatus.Running) mutableState.update { it.copy(zoomRatio = ratio.coerceIn(1f, 4f)) }
    }
    override fun close() {
        if (closed) return
        closed = true
        scope.launch { try { stop() } finally { scope.cancel() } }
    }
}

actual fun CameraFrame.toPreviewBitmap(): ImageBitmap =
    Image.makeRaster(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), rgba, width * 4).toComposeImageBitmap()
