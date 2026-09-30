package com.aifacemate.face.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.ZoomState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.CameraState as NativeCameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
actual fun rememberCameraController(): CameraController {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permissionResult by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionResult?.complete(it)
    }
    val controller = remember(context, owner) {
        AndroidCameraController(context, owner) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) true
            else {
                val result = CompletableDeferred<Boolean>()
                permissionResult = result
                try { launcher.launch(Manifest.permission.CAMERA); result.await() }
                finally { permissionResult = null }
            }
        }
    }
    DisposableEffect(controller) { onDispose { controller.close() } }
    return controller
}

private class AndroidCameraController(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val permission: suspend () -> Boolean,
) : CameraController {
    private val mutableState = MutableStateFlow(CameraState(devices = devices()))
    override val state = mutableState.asStateFlow()
    private val mutableFrames = MutableStateFlow<CameraFrame?>(null)
    override val frames = mutableFrames.asStateFlow()
    private val gate = Mutex()
    private val executor = Executors.newSingleThreadExecutor()
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var observedInfo: CameraInfo? = null
    private var stateObserver: Observer<NativeCameraState>? = null
    private var zoomObserver: Observer<ZoomState>? = null
    private var boundCamera: Camera? = null
    private var focusFlags = 0
    @Volatile private var analysisRotation = 0
    @Volatile private var closed = false
    @Volatile private var generation = 0L
    private var sequence = 0L

    private fun devices(): List<CameraDevice> = runCatching {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        manager.cameraIdList.map { id ->
            val lens = when (manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> CameraLens.Front
                CameraCharacteristics.LENS_FACING_BACK -> CameraLens.Back
                else -> CameraLens.External
            }
            CameraDevice(id, when (lens) { CameraLens.Front -> "前置摄像头"; CameraLens.Back -> "后置摄像头"; else -> "外接摄像头 $id" }, lens)
        }
    }.getOrDefault(emptyList())

    @ExperimentalCamera2Interop
    override suspend fun start(deviceId: String?) = withContext(Dispatchers.Main.immediate) {
        gate.withLock {
            if (closed) return@withLock
            stopCapture()
            mutableState.update { it.copy(status = CameraStatus.Starting, error = null) }
            try {
                if (!permission()) {
                    mutableState.update { it.copy(status = CameraStatus.PermissionDenied, error = "请在系统设置中允许摄像头访问，然后重试") }
                    return@withLock
                }
                val future = ProcessCameraProvider.getInstance(context)
                val cameraProvider = suspendCancellableCoroutine<ProcessCameraProvider> { continuation ->
                    future.addListener({
                        if (continuation.isActive) try { continuation.resume(future.get()) }
                        catch (failure: Exception) { continuation.resumeWithException(failure) }
                    }, ContextCompat.getMainExecutor(context))
                }
                currentCoroutineContext().ensureActive()
                if (closed) return@withLock
                provider = cameraProvider
                val available = cameraProvider.availableCameraInfos.map { Camera2CameraInfo.from(it).cameraId }.toSet()
                val cameras = devices().filter { it.id in available }
                val selected = cameras.firstOrNull { it.id == deviceId }
                    ?: if (deviceId == null) cameras.firstOrNull { it.lens == CameraLens.Front } ?: cameras.firstOrNull() else null
                checkNotNull(selected) { "未找到所选摄像头" }
                val characteristics = (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager).getCameraCharacteristics(selected.id)
                val modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                val hasAf = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) != 0f &&
                    modes.any { it == CameraMetadata.CONTROL_AF_MODE_AUTO }
                focusFlags = 0
                if (hasAf && (characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0) > 0) focusFlags = focusFlags or FocusMeteringAction.FLAG_AF
                if ((characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0) > 0) focusFlags = focusFlags or FocusMeteringAction.FLAG_AE
                if ((characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) ?: 0) > 0) focusFlags = focusFlags or FocusMeteringAction.FLAG_AWB
                val selector = CameraSelector.Builder().addCameraFilter { infos ->
                    infos.filter { Camera2CameraInfo.from(it).cameraId == selected.id }
                }.build()
                @Suppress("DEPRECATION")
                val useCase = ImageAnalysis.Builder().setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                val token = ++generation
                var lastFrame = 0L
                useCase.setAnalyzer(executor) { image ->
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (closed || token != generation || now - lastFrame < 80) return@setAnalyzer
                        lastFrame = now
                        analysisRotation = image.imageInfo.rotationDegrees
                        val pixels = CameraPixels.yuv420(image.width, image.height, image.plane(0), image.plane(1), image.plane(2))
                        val frame = CameraPixels.upright(image.width, image.height, pixels, image.imageInfo.rotationDegrees,
                            ++sequence, selected.lens == CameraLens.Front)
                        if (!closed && token == generation) mutableFrames.value = frame
                    } catch (failure: Exception) {
                        reportError(token, "相机帧读取失败，请重试")
                    } finally { image.close() }
                }
                analysis = useCase
                val camera = cameraProvider.bindToLifecycle(owner, selector, useCase)
                boundCamera = camera
                // CameraX restores continuous AF through this API without interop overrides that would block tap focus.
                camera.cameraControl.cancelFocusAndMetering()
                observedInfo = camera.cameraInfo
                val observer = Observer<NativeCameraState> { native ->
                    if (native.error != null) reportError(token, "摄像头连接异常，请检查设备或其它应用占用后重试")
                }.also { stateObserver = it }
                camera.cameraInfo.cameraState.observe(owner, observer)
                val zoom = camera.cameraInfo.zoomState.value
                mutableState.value = CameraState(CameraStatus.Running, cameras, selected.id,
                    zoomRatio = zoom?.zoomRatio ?: 1f, minZoomRatio = zoom?.minZoomRatio ?: 1f, maxZoomRatio = zoom?.maxZoomRatio ?: 1f)
                val zoomChanged = Observer<ZoomState> { value ->
                    if (token == generation) mutableState.update { it.copy(zoomRatio = value.zoomRatio,
                        minZoomRatio = value.minZoomRatio, maxZoomRatio = value.maxZoomRatio) }
                }.also { zoomObserver = it }
                camera.cameraInfo.zoomState.observe(owner, zoomChanged)
            } catch (cancelled: CancellationException) {
                stopCapture()
                throw cancelled
            } catch (failure: Exception) {
                stopCapture()
                mutableState.update { it.copy(status = CameraStatus.Error, error = failure.message ?: "摄像头启动失败，请重试") }
            }
        }
    }

    override suspend fun stop() = withContext(NonCancellable + Dispatchers.Main.immediate) { gate.withLock { stopCapture() } }

    override suspend fun setZoom(ratio: Float) = withContext(Dispatchers.Main.immediate) {
        gate.withLock {
            val camera = boundCamera ?: return@withLock
            if (!ratio.isFinite()) return@withLock
            val current = state.value
            camera.cameraControl.setZoomRatio(ratio.coerceIn(current.minZoomRatio, current.maxZoomRatio))
            Unit
        }
    }

    override suspend fun focus(point: CameraFocusPoint): CameraFocusResult = withContext(Dispatchers.Main.immediate) {
        val operation = gate.withLock {
            val camera = boundCamera ?: return@withLock null
            val useCase = analysis ?: return@withLock null
            if (focusFlags == 0 || frames.value == null) return@withLock null
            val surface = point.toSurface(analysisRotation)
            val metering = SurfaceOrientedMeteringPointFactory(1f, 1f, useCase).createPoint(surface.x, surface.y)
            val action = FocusMeteringAction.Builder(metering, focusFlags).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
            if (!camera.cameraInfo.isFocusMeteringSupported(action)) return@withLock null
            camera.cameraControl.startFocusAndMetering(action) to (focusFlags and FocusMeteringAction.FLAG_AF != 0)
        } ?: return@withContext CameraFocusResult.Unsupported
        val (future, hasAf) = operation
        // Do not hold the camera mutex while autofocus scans; zoom, switching and stopping must remain responsive.
        val result = suspendCancellableCoroutine<FocusMeteringResult> { continuation ->
            continuation.invokeOnCancellation { future.cancel(true) }
            future.addListener({
                if (continuation.isActive) try { continuation.resume(future.get()) }
                catch (failure: Exception) { continuation.resumeWithException(failure.cause ?: failure) }
            }, ContextCompat.getMainExecutor(context))
        }
        if (!hasAf) CameraFocusResult.ExposureAdjusted
        else if (result.isFocusSuccessful) CameraFocusResult.Focused else CameraFocusResult.Failed
    }

    private fun stopCapture() {
        generation++
        stateObserver?.let { observedInfo?.cameraState?.removeObserver(it) }
        zoomObserver?.let { observedInfo?.zoomState?.removeObserver(it) }
        zoomObserver = null
        boundCamera = null
        focusFlags = 0
        stateObserver = null
        observedInfo = null
        analysis?.let { it.clearAnalyzer(); provider?.unbind(it) }
        analysis = null
        mutableFrames.value = null
        mutableState.update { it.copy(status = CameraStatus.Idle, error = null) }
    }

    private fun reportError(token: Long, message: String) {
        cleanup.launch {
            gate.withLock {
                if (token == generation && !closed) {
                    stopCapture()
                    mutableState.update { it.copy(status = CameraStatus.Error, error = message) }
                }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        cleanup.launch { try { stop() } finally { executor.shutdown(); cleanup.cancel() } }
    }
}

private fun ImageProxy.plane(index: Int): CameraPixels.Plane {
    val plane = planes[index]
    val buffer = plane.buffer.duplicate()
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return CameraPixels.Plane(bytes, plane.rowStride, plane.pixelStride)
}

actual fun CameraFrame.toPreviewBitmap(): ImageBitmap {
    return toAndroidBitmap().asImageBitmap()
}

internal fun CameraFrame.toAndroidBitmap(): Bitmap {
    val argb = IntArray(width * height) { index ->
        val at = index * 4
        (255 shl 24) or ((rgba[at].toInt() and 255) shl 16) or
            ((rgba[at + 1].toInt() and 255) shl 8) or (rgba[at + 2].toInt() and 255)
    }
    return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
}
