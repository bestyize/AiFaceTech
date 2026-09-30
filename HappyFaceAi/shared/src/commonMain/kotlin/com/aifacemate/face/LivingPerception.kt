package com.aifacemate.face

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aifacemate.face.behavior.BehaviorEvent
import com.aifacemate.face.camera.*
import com.aifacemate.face.vision.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Face-page owner. Leaving the page or stopping the app releases camera and inference. */
@Composable
internal fun LivingPerception(now: () -> Long, onEvent: (BehaviorEvent) -> Unit, onNotice: (String) -> Unit, onStatus: (String) -> Unit) {
    val preferences = remember { VisionPreferencesStore.load() }
    if (VisionTask.entries.none { VisionRuntime.registry.visionStatus(it, preferences) == VisionTaskStatus.Running }) {
        SideEffect { onStatus(if (VisionTask.entries.any { preferences.enabled(it) }) "未就绪" else "未开启") }
        return
    }
    val controller = rememberCameraController()
    val perception = remember { PerceptionService(VisionRuntime.registry) }
    val callback = rememberUpdatedState(onEvent)
    val currentNow = rememberUpdatedState(now)
    val notice = rememberUpdatedState(onNotice)
    val status = rememberUpdatedState(onStatus)
    val cameraState by controller.state.collectAsState()
    var restart by remember { mutableStateOf(0) }
    var lastFrameAt by remember { mutableStateOf<Long?>(null) }
    var active by remember { mutableStateOf(true) }
    LaunchedEffect(controller) {
        controller.frames.collect { frame -> if (frame != null) lastFrameAt = currentNow.value() }
    }
    // A failed camera or a stalled stream must recover on this page, without a preview-page round trip.
    LaunchedEffect(cameraState.status, active) {
        status.value(when {
            !active -> "已暂停"
            cameraState.status == CameraStatus.Running -> "监测中"
            cameraState.status == CameraStatus.Starting -> "启动中"
            cameraState.status == CameraStatus.PermissionDenied -> "需授权"
            cameraState.status == CameraStatus.Error -> "重试中"
            else -> "启动中"
        })
        if (!active) return@LaunchedEffect
        if (cameraState.status == CameraStatus.Error) {
            delay(3000)
            restart++
        } else if (cameraState.status == CameraStatus.Running) {
            val started = currentNow.value()
            while (true) {
                delay(2000)
                if (currentNow.value() - (lastFrameAt ?: started) > 8000) {
                    status.value("重试中")
                    restart++
                    break
                }
            }
        }
    }
    LaunchedEffect(cameraState.status, cameraState.error) {
        when (cameraState.status) {
            CameraStatus.PermissionDenied -> notice.value("视觉互动需要相机权限，可在系统设置中开启")
            CameraStatus.Error -> notice.value(cameraState.error ?: "相机不可用，视觉互动已停止")
            else -> Unit
        }
    }
    val gate = remember { Mutex() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { active = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { active = false }
    LaunchedEffect(active, restart) {
        gate.withLock {
            if (!active) { controller.stop(); callback.value(BehaviorEvent.VisionStopped); return@withLock }
            try {
                lastFrameAt = null
                controller.start()
                coroutineScope {
                    var failureReported = false
                    launch(Dispatchers.Default) {
                        perception.run(controller.frames.filterNotNull()
                            .filter { controller.state.value.status == CameraStatus.Running }
                            .map { frame ->
                                RgbaFrame(frame.width, frame.height, frame.rgba, currentNow.value())
                            }, preferences)
                    }
                    perception.state.collect { state ->
                        if (!failureReported && state.statuses.values.any { it is VisionTaskStatus.Failed }) {
                            failureReported = true
                            notice.value("部分视觉识别已停止，请到视觉页面查看")
                        }
                        val observation = state.observation ?: return@collect
                        callback.value(BehaviorEvent.Vision(
                            personVisible = observation.faces.any { it.face.score >= .7f } ||
                                observation.objects.any { it.label == "person" && it.score >= .7f },
                            thumbsUp = observation.hands.any { it.gesture == HandGesture.ThumbsUp && it.hand.score >= .7f },
                            capturedAtMillis = state.sampledAtMillis ?: return@collect,
                        ))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                notice.value(failure.message ?: "视觉互动暂不可用")
            } finally {
                withContext(NonCancellable) { controller.stop() }
                callback.value(BehaviorEvent.VisionStopped)
            }
        }
    }
}
