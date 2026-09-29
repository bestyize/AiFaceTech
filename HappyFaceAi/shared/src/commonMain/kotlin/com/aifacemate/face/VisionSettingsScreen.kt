package com.aifacemate.face

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.theme.AppColors
import com.aifacemate.face.vision.VisionPreferences
import com.aifacemate.face.vision.VisionPreferencesStore
import com.aifacemate.face.vision.Yolo26mModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun VisionSettingsScreen(onBack: () -> Unit, onOpenCamera: () -> Unit = {}) {
    val initial = remember { VisionPreferencesStore.load() }
    // Primitive saveable fields preserve unsaved edits through Android recreation.
    var yoloEnabled by rememberSaveable { mutableStateOf(initial.yoloEnabled) }
    var yoloPath by rememberSaveable { mutableStateOf(initial.yoloModelPath) }
    var yoloThreshold by rememberSaveable { mutableStateOf(initial.yoloConfidenceThreshold) }
    var expressionEnabled by rememberSaveable { mutableStateOf(initial.expressionEnabled) }
    var expressionPath by rememberSaveable { mutableStateOf(initial.expressionModelPath) }
    var expressionThreshold by rememberSaveable { mutableStateOf(initial.expressionConfidenceThreshold) }
    val scope = rememberCoroutineScope()
    var modelInstalled by remember { mutableStateOf(Yolo26mModel.installed()) }
    var modelBusy by remember { mutableStateOf(false) }
    var modelProgress by remember { mutableStateOf(0) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var modelJob by remember { mutableStateOf<Job?>(null) }
    val draft = VisionPreferences(yoloEnabled, yoloPath, yoloThreshold, expressionEnabled, expressionPath, expressionThreshold)
    VisionSettingsContent(
        preferences = draft,
        onPreferencesChange = {
            yoloEnabled = it.yoloEnabled
            yoloPath = it.yoloModelPath
            yoloThreshold = it.yoloConfidenceThreshold
            expressionEnabled = it.expressionEnabled
            expressionPath = it.expressionModelPath
            expressionThreshold = it.expressionConfidenceThreshold
        },
        onBack = onBack,
        onSave = {
            VisionPreferencesStore.save(draft)
            scope.launch { Yolo26mModel.unload(); onBack() }
        },
        onOpenCamera = {
            VisionPreferencesStore.save(draft)
            scope.launch { Yolo26mModel.unload(); onOpenCamera() }
        },
        yoloSupported = Yolo26mModel.supported(),
        yoloInstalled = modelInstalled,
        yoloBusy = modelBusy,
        yoloProgress = modelProgress,
        yoloError = modelError,
        onYoloCancel = { modelJob?.cancel() },
        onYoloModelAction = {
            modelBusy = true
            modelProgress = 0
            modelError = null
            modelJob = scope.launch {
                try {
                    if (modelInstalled) Yolo26mModel.remove()
                    else {
                        var last = -1
                        Yolo26mModel.install { downloaded, total ->
                            val percent = (downloaded * 100 / total).toInt().coerceIn(0, 99)
                            if (last != percent) {
                                last = percent
                                scope.launch { modelProgress = percent }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    modelError = error.message ?: "YOLO 模型操作失败，请重试"
                } finally {
                    modelInstalled = Yolo26mModel.installed()
                    modelBusy = false
                    modelJob = null
                }
            }
        },
    )
}

/** Pure UI, so rendering and interaction checks never access the user's saved settings. */
@Composable
fun VisionSettingsContent(
    preferences: VisionPreferences,
    onPreferencesChange: (VisionPreferences) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    yoloSupported: Boolean = false,
    yoloInstalled: Boolean = false,
    yoloBusy: Boolean = false,
    yoloProgress: Int = 0,
    yoloError: String? = null,
    onYoloModelAction: () -> Unit = {},
    onYoloCancel: () -> Unit = {},
    onOpenCamera: () -> Unit = {},
) {
    var expressionTab by rememberSaveable { mutableStateOf(false) }
    var validationAttempted by rememberSaveable { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = AppColors.Amber, onPrimary = AppColors.Background,
        background = AppColors.Background, surface = AppColors.Card,
        onSurface = Color.White, onSurfaceVariant = AppColors.Secondary, outline = AppColors.Border,
    )) {
        BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.Background).safeDrawingPadding().imePadding()) {
            val compact = maxHeight < 540.dp
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val spacious = maxWidth.value / fontScale >= 900f
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = 1100.dp).fillMaxSize()
                    .padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("vision.back")
                            .semantics { contentDescription = "返回生命中心" },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Canvas(Modifier.size(14.dp)) {
                            drawLine(AppColors.Amber, Offset(size.width * .75f, size.height * .1f), Offset(size.width * .25f, size.height * .5f), 2.dp.toPx())
                            drawLine(AppColors.Amber, Offset(size.width * .25f, size.height * .5f), Offset(size.width * .75f, size.height * .9f), 2.dp.toPx())
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("返回", fontSize = 14.sp)
                    }
                    Text("视觉", Modifier.weight(1f), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            validationAttempted = true
                            when {
                                preferences.modelPathError(false) != null -> expressionTab = false
                                preferences.modelPathError(true) != null -> expressionTab = true
                                else -> onSave()
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("vision.save"),
                        enabled = !yoloBusy,
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("保存") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(false to "YOLO 目标检测", true to "表情识别").forEach { (expression, title) ->
                        val selected = expressionTab == expression
                        Surface(
                            onClick = { expressionTab = expression },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .testTag(if (expression) "vision.tab.expression" else "vision.tab.yolo")
                                .semantics { this.selected = selected; role = Role.Tab },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) AppColors.Amber.copy(alpha = .14f) else AppColors.Card,
                            border = BorderStroke(1.dp, if (selected) AppColors.Amber else AppColors.Border),
                        ) {
                            Text(title, Modifier.padding(12.dp), color = if (selected) AppColors.Amber else AppColors.Secondary,
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
                key(expressionTab) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("vision.configuration"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val enabled = if (expressionTab) preferences.expressionEnabled else preferences.yoloEnabled
                        val path = if (expressionTab) preferences.expressionModelPath else preferences.yoloModelPath
                        val threshold = if (expressionTab) preferences.expressionConfidenceThreshold else preferences.yoloConfidenceThreshold
                        val prefix = if (expressionTab) "vision.expression" else "vision.yolo"
                        VisionPanel {
                            Text(if (expressionTab) "表情识别" else "YOLO 目标检测", color = Color.White,
                                fontSize = if (compact) 20.sp else 24.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (expressionTab) "识别人脸表情，配置独立于小星的表情与外观。" else "识别人、物体与周围环境中的目标。",
                                color = AppColors.Secondary, fontSize = 13.sp)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .toggleable(enabled, role = Role.Switch) {
                                        onPreferencesChange(if (expressionTab) preferences.copy(expressionEnabled = it) else preferences.copy(yoloEnabled = it))
                                    }.testTag("$prefix.enabled"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("启用${if (expressionTab) "表情识别" else "目标检测"}", color = Color.White)
                                    Text("保存启用偏好", color = AppColors.Secondary, fontSize = 12.sp)
                                }
                                Switch(checked = enabled, onCheckedChange = null)
                            }
                            HorizontalDivider(color = AppColors.Border)
                            Text(if (expressionTab) "本地表情模型 · ONNX" else "YOLO26m · ONNX · COCO 80 类",
                                color = AppColors.Amber, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (!expressionTab) {
                                Text(
                                    when {
                                        !yoloSupported -> "此设备暂不支持 YOLO 本地模型"
                                        yoloBusy -> "正在处理模型… $yoloProgress%"
                                        yoloInstalled -> "模型已下载 · 可离线使用"
                                        else -> "模型未下载 · 约 82 MB"
                                    },
                                    color = AppColors.Secondary, fontSize = 13.sp,
                                    modifier = Modifier.testTag("vision.yolo.modelStatus"),
                                )
                                if (yoloBusy) LinearProgressIndicator(
                                    progress = { yoloProgress / 100f }, modifier = Modifier.fillMaxWidth(),
                                )
                                Button(
                                    onClick = onYoloModelAction,
                                    enabled = yoloSupported && !yoloBusy,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("vision.yolo.modelAction"),
                                    shape = RoundedCornerShape(14.dp),
                                ) { Text(if (yoloBusy) "处理中…" else if (yoloInstalled) "删除下载模型" else "下载 YOLO26m 模型") }
                                if (yoloBusy) OutlinedButton(
                                    onClick = onYoloCancel,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("vision.yolo.cancel"),
                                ) { Text("取消") }
                                if (yoloError != null) Text(yoloError, color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp, modifier = Modifier.testTag("vision.yolo.modelError"))
                                Text("按需下载，首次识别时加载；结束识别后可释放内存。", color = AppColors.Secondary, fontSize = 12.sp)
                            }
                            val pathError = if (validationAttempted) preferences.modelPathError(expressionTab) else null
                            OutlinedTextField(
                                value = path,
                                onValueChange = {
                                    onPreferencesChange(if (expressionTab) preferences.copy(expressionModelPath = it) else preferences.copy(yoloModelPath = it))
                                },
                                label = { Text(if (expressionTab) "本地模型路径" else "自定义模型路径（可选）") },
                                placeholder = { Text(if (expressionTab) "例如 /models/expression.onnx" else "例如 /models/yolo26m.onnx") },
                                supportingText = { Text(pathError ?: if (expressionTab) "填写当前设备上可读取的 .onnx 文件路径" else "留空使用下载模型；也可填写自己的 .onnx 路径") },
                                isError = pathError != null,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("$prefix.modelPath"),
                            )
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("置信度阈值", Modifier.weight(1f), color = Color.White)
                                Text("${(threshold * 100).roundToInt()}%", color = AppColors.Amber, fontWeight = FontWeight.SemiBold)
                            }
                            Slider(
                                value = threshold, valueRange = 0f..1f, steps = 19,
                                onValueChange = {
                                    onPreferencesChange(if (expressionTab) preferences.copy(expressionConfidenceThreshold = it) else preferences.copy(yoloConfidenceThreshold = it))
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("$prefix.confidence")
                                    .semantics { contentDescription = if (expressionTab) "表情识别置信度阈值" else "YOLO 置信度阈值" },
                            )
                            Text("阈值越高，保留的识别结果越少，筛选越严格。", color = AppColors.Secondary, fontSize = 12.sp)
                        }
                        VisionPanel {
                            Text("接入状态", color = AppColors.Amber, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (expressionTab) "表情识别推理后端待接入，当前可保存模型路径和识别参数。"
                                else "Android / 桌面端支持模型按需下载与本地单帧推理；iOS 推理后端待接入。",
                                color = AppColors.Secondary, fontSize = 13.sp,
                            )
                            if (!expressionTab && spacious) Text("模型格式：FP32 [1,3,640,640] → [1,N,6]，使用 nms=False 导出。",
                                color = AppColors.Secondary, fontSize = 12.sp)
                            Text("保存配置不会启动摄像头；可在摄像头页面启动预览与本地识别。", color = AppColors.Secondary, fontSize = 12.sp)
                        }
                        VisionPanel {
                            Text("摄像头", color = AppColors.Amber, fontWeight = FontWeight.SemiBold)
                            Text("预览周围环境；启用 YOLO 后可进行实时目标检测。", color = AppColors.Secondary, fontSize = 13.sp)
                            OutlinedButton(
                                onClick = {
                                    validationAttempted = true
                                    when {
                                        preferences.modelPathError(false) != null -> expressionTab = false
                                        preferences.modelPathError(true) != null -> expressionTab = true
                                        else -> onOpenCamera()
                                    }
                                },
                                enabled = !yoloBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("vision.camera"),
                                shape = RoundedCornerShape(14.dp),
                            ) { Text("保存并打开摄像头") }
                            Text("进入后点击启动才会请求相机权限；视频帧仅在本机处理。", color = AppColors.Secondary, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VisionPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = AppColors.Card, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, AppColors.Border)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
