package com.aifacemate.face.camera

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.AVFoundation.*
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.*
import platform.Foundation.*
import platform.QuartzCore.CACurrentMediaTime
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_queue_create
import kotlin.coroutines.resume

@Composable
actual fun rememberCameraController(): CameraController {
    val controller = remember { IosCameraController() }
    DisposableEffect(controller) { onDispose { controller.close() } }
    return controller
}

@OptIn(ExperimentalForeignApi::class)
private class IosCameraController : CameraController {
    private val mutableState = MutableStateFlow(CameraState(devices = devices().map { it.toCameraDevice() }))
    override val state = mutableState.asStateFlow()
    private val mutableFrames = MutableStateFlow<CameraFrame?>(null)
    override val frames = mutableFrames.asStateFlow()
    private val gate = Mutex()
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val closed = MutableStateFlow(false)
    private val generation = MutableStateFlow(0L)
    private val orientation = MutableStateFlow(AVCaptureVideoOrientationPortrait)
    private val frameQueue = dispatch_queue_create("com.aifacemate.camera.frames", null)
    private var session: AVCaptureSession? = null
    private var captureDevice: AVCaptureDevice? = null
    private var output: AVCaptureVideoDataOutput? = null
    private var delegate: FrameDelegate? = null
    private var sequence = 0L
    private val observers = mutableListOf<NSObjectProtocol>()

    init {
        UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()
        updateOrientation()
        val center = NSNotificationCenter.defaultCenter
        observers += center.addObserverForName(UIDeviceOrientationDidChangeNotification, null, NSOperationQueue.mainQueue) { updateOrientation() }
        observers += center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) {
            cleanup.launch { stop() }
        }
        observers += center.addObserverForName(AVCaptureSessionRuntimeErrorNotification, null, NSOperationQueue.mainQueue) { notification ->
            if (notification?.`object` == session) cleanup.launch {
                stop()
                mutableState.update { it.copy(status = CameraStatus.Error, error = "相机被系统中断，请重新启动") }
            }
        }
    }

    private fun updateOrientation() {
        orientation.value = when (UIDevice.currentDevice.orientation) {
            UIDeviceOrientation.UIDeviceOrientationPortrait -> AVCaptureVideoOrientationPortrait
            UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeRight
            UIDeviceOrientation.UIDeviceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeLeft
            else -> orientation.value
        }
    }

    private fun devices(): List<AVCaptureDevice> = AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo).filterIsInstance<AVCaptureDevice>()
    private fun AVCaptureDevice.toCameraDevice(): CameraDevice {
        val lens = when (position) {
            AVCaptureDevicePositionFront -> CameraLens.Front
            AVCaptureDevicePositionBack -> CameraLens.Back
            else -> CameraLens.External
        }
        return CameraDevice(uniqueID, localizedName, lens)
    }

    private suspend fun permission(): Boolean = when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusAuthorized -> true
        AVAuthorizationStatusNotDetermined -> suspendCancellableCoroutine { continuation ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                if (continuation.isActive) continuation.resume(granted)
            }
        }
        else -> false
    }

    override suspend fun start(deviceId: String?) = withContext(Dispatchers.Default) {
        gate.withLock {
            if (closed.value) return@withLock
            stopCapture()
            mutableState.update { it.copy(status = CameraStatus.Starting, error = null) }
            try {
                if (!permission()) {
                    mutableState.update { it.copy(status = CameraStatus.PermissionDenied, error = "请在 iOS 设置中允许摄像头访问，然后重试") }
                    return@withLock
                }
                currentCoroutineContext().ensureActive()
                if (closed.value) return@withLock
                val cameras = devices()
                val selected = cameras.firstOrNull { it.uniqueID == deviceId }
                    ?: if (deviceId == null) cameras.firstOrNull { it.position == AVCaptureDevicePositionFront } ?: cameras.firstOrNull() else null
                checkNotNull(selected) { "未找到摄像头；请使用有相机的 iOS 真机" }
                check(selected.lockForConfiguration(null)) { "摄像头配置暂不可用" }
                try { selected.videoZoomFactor = 1.0 } finally { selected.unlockForConfiguration() }
                captureDevice = selected
                val capture = AVCaptureSession().also { session = it }
                capture.beginConfiguration()
                try {
                    capture.sessionPreset = if (capture.canSetSessionPreset(AVCaptureSessionPreset640x480)) AVCaptureSessionPreset640x480 else AVCaptureSessionPresetMedium
                    val input = checkNotNull(AVCaptureDeviceInput.deviceInputWithDevice(selected, null)) { "无法初始化摄像头" }
                    check(capture.canAddInput(input)) { "相机输入不可用" }
                    capture.addInput(input)
                    val video = AVCaptureVideoDataOutput().also { output = it }
                    video.videoSettings = mapOf(kCVPixelBufferPixelFormatTypeKey to NSNumber.numberWithUnsignedInt(kCVPixelFormatType_32BGRA))
                    video.alwaysDiscardsLateVideoFrames = true
                    val token = generation.value + 1
                    generation.value = token
                    val callback = FrameDelegate { sample, connection ->
                        if (closed.value || generation.value != token) return@FrameDelegate
                        if (connection.isVideoOrientationSupported()) connection.videoOrientation = orientation.value
                        val buffer = CMSampleBufferGetImageBuffer(sample) ?: return@FrameDelegate
                        if (CVPixelBufferLockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly) != kCVReturnSuccess) return@FrameDelegate
                        try {
                            val width = CVPixelBufferGetWidth(buffer).toInt()
                            val height = CVPixelBufferGetHeight(buffer).toInt()
                            val stride = CVPixelBufferGetBytesPerRow(buffer).toInt()
                            check(width > 0 && height > 0 && stride.toLong() * height <= Int.MAX_VALUE)
                            val address = CVPixelBufferGetBaseAddress(buffer)?.reinterpret<ByteVar>() ?: return@FrameDelegate
                            val bytes = address.readBytes(stride * height)
                            val frame = CameraFrame(width, height, CameraPixels.bgra(width, height, bytes, stride),
                                ++sequence, selected.position == AVCaptureDevicePositionFront)
                            if (!closed.value && generation.value == token) mutableFrames.value = frame
                        } finally { CVPixelBufferUnlockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly) }
                    }.also { delegate = it }
                    video.setSampleBufferDelegate(callback, frameQueue)
                    check(capture.canAddOutput(video)) { "相机帧输出不可用" }
                    capture.addOutput(video)
                    video.connectionWithMediaType(AVMediaTypeVideo)?.let { connection ->
                        if (connection.isVideoOrientationSupported()) connection.videoOrientation = orientation.value
                        if (connection.isVideoMirroringSupported()) {
                            connection.automaticallyAdjustsVideoMirroring = false
                            connection.videoMirrored = false
                        }
                    }
                } finally { capture.commitConfiguration() }
                currentCoroutineContext().ensureActive()
                capture.startRunning()
                check(capture.isRunning()) { "摄像头未能启动，请重试" }
                mutableState.value = CameraState(CameraStatus.Running, cameras.map { it.toCameraDevice() }, selected.uniqueID,
                    zoomRatio = selected.videoZoomFactor.toFloat(), maxZoomRatio = selected.activeFormat.videoMaxZoomFactor.toFloat().coerceIn(1f, 8f))
            } catch (cancelled: CancellationException) {
                stopCapture()
                throw cancelled
            } catch (failure: Exception) {
                stopCapture()
                mutableState.update { it.copy(status = CameraStatus.Error, error = failure.message ?: "相机启动失败") }
            }
        }
    }

    private fun stopCapture() {
        generation.value++
        output?.setSampleBufferDelegate(null, null)
        session?.stopRunning()
        output = null
        session = null
        captureDevice = null
        delegate = null
        mutableFrames.value = null
        mutableState.update { it.copy(status = CameraStatus.Idle, error = null) }
    }
    override suspend fun stop() = withContext(NonCancellable + Dispatchers.Default) { gate.withLock { stopCapture() } }
    override suspend fun setZoom(ratio: Float) = withContext(Dispatchers.Default) {
        gate.withLock {
            val device = captureDevice ?: return@withLock
            if (!ratio.isFinite()) return@withLock
            val zoom = ratio.coerceIn(state.value.minZoomRatio, state.value.maxZoomRatio)
            check(device.lockForConfiguration(null)) { "摄像头变焦暂不可用" }
            try { device.videoZoomFactor = zoom.toDouble() } finally { device.unlockForConfiguration() }
            mutableState.update { it.copy(zoomRatio = zoom) }
        }
    }
    override fun close() {
        if (closed.value) return
        closed.value = true
        cleanup.launch {
            try { stop() } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
                    observers.clear()
                    UIDevice.currentDevice.endGeneratingDeviceOrientationNotifications()
                }
                cleanup.cancel()
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class FrameDelegate(private val onFrame: (CMSampleBufferRef, AVCaptureConnection) -> Unit) :
    NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    private var last = 0.0
    @ObjCSignatureOverride
    override fun captureOutput(output: AVCaptureOutput, didOutputSampleBuffer: CMSampleBufferRef?, fromConnection: AVCaptureConnection) {
        val sample = didOutputSampleBuffer ?: return
        val now = CACurrentMediaTime()
        if (now - last < .08) return
        last = now
        // An invalid frame is dropped; native buffers never escape this callback.
        runCatching { onFrame(sample, fromConnection) }
    }
}

actual fun CameraFrame.toPreviewBitmap(): ImageBitmap =
    Image.makeRaster(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), rgba, width * 4).toComposeImageBitmap()
