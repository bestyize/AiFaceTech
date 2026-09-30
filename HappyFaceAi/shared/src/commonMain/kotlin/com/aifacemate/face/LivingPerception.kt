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
internal fun LivingPerception(now: () -> Long, onEvent: (BehaviorEvent) -> Unit, onNotice: (String) -> Unit) {
    val preferences = remember { VisionPreferencesStore.load() }
    if (VisionTask.entries.none { VisionRuntime.registry.visionStatus(it, preferences) == VisionTaskStatus.Running }) return
    val controller = rememberCameraController()
    val perception = remember { PerceptionService(VisionRuntime.registry) }
    val callback = rememberUpdatedState(onEvent)
    val currentNow = rememberUpdatedState(now)
    val notice = rememberUpdatedState(onNotice)
    val cameraState by controller.state.collectAsState()
    LaunchedEffect(cameraState.status, cameraState.error) {
        when (cameraState.status) {
            CameraStatus.PermissionDenied -> notice.value("视觉互动需要相机权限，可在系统设置中开启")
            CameraStatus.Error -> notice.value(cameraState.error ?: "相机不可用，视觉互动已停止")
            else -> Unit
        }
    }
    val gate = remember { Mutex() }
    var active by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { active = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { active = false }
    LaunchedEffect(active) {
        gate.withLock {
            if (!active) { controller.stop(); callback.value(BehaviorEvent.VisionStopped); return@withLock }
            try {
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
