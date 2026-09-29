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
import com.aifacemate.face.vision.LocalVisionAnalyzer
import com.aifacemate.face.vision.LocalVisionTask
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
    var gestureEnabled by rememberSaveable { mutableStateOf(initial.gestureEnabled) }
    var gesturePath by rememberSaveable { mutableStateOf(initial.gestureModelPath) }
    var gestureThreshold by rememberSaveable { mutableStateOf(initial.gestureConfidenceThreshold) }
    val scope = rememberCoroutineScope()
    var modelInstalled by remember { mutableStateOf(Yolo26mModel.installed()) }
    var modelBusy by remember { mutableStateOf(false) }
    var modelProgress by remember { mutableStateOf(0) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var modelJob by remember { mutableStateOf<Job?>(null) }
    var localTaskBusy by remember { mutableStateOf<LocalVisionTask?>(null) }
    var localProgress by remember { mutableStateOf(0) }
    var localError by remember { mutableStateOf<String?>(null) }
    var localModelJob by remember { mutableStateOf<Job?>(null) }
    var expressionsInstalled by remember { mutableStateOf(LocalVisionAnalyzer.modelsInstalled(LocalVisionTask.FacialExpressions)) }
    var gesturesInstalled by remember { mutableStateOf(LocalVisionAnalyzer.modelsInstalled(LocalVisionTask.HandGestures)) }
    val draft = VisionPreferences(yoloEnabled, yoloPath, yoloThreshold, expressionEnabled, expressionPath, expressionThreshold,
        gestureEnabled, gesturePath, gestureThreshold)
    VisionSettingsContent(
        preferences = draft,
        onPreferencesChange = {
            yoloEnabled = it.yoloEnabled
            yoloPath = it.yoloModelPath
            yoloThreshold = it.yoloConfidenceThreshold
            expressionEnabled = it.expressionEnabled
            expressionPath = it.expressionModelPath
            expressionThreshold = it.expressionConfidenceThreshold
            gestureEnabled = it.gestureEnabled
            gesturePath = it.gestureModelPath
            gestureThreshold = it.gestureConfidenceThreshold
        },
        onBack = onBack,
        onSave = {
            VisionPreferencesStore.save(draft)
            scope.launch { Yolo26mModel.unload(); LocalVisionAnalyzer.unload(); onBack() }
        },
        onOpenCamera = {
            VisionPreferencesStore.save(draft)
            scope.launch { Yolo26mModel.unload(); LocalVisionAnalyzer.unload(); onOpenCamera() }
        },
        localVisionSupported = LocalVisionAnalyzer.supported(),
        localModelsDownloadable = LocalVisionAnalyzer.modelsAreDownloadable(),
        expressionsInstalled = expressionsInstalled,
        gesturesInstalled = gesturesInstalled,
        localTaskBusy = localTaskBusy,
        localProgress = localProgress,
        localError = localError,
        onLocalModelAction = { task ->
            localTaskBusy = task
            localProgress = 0
            localError = null
            localModelJob = scope.launch {
                try {
                    val installed = LocalVisionAnalyzer.modelsInstalled(task)
                    if (installed) LocalVisionAnalyzer.removeModels(task)
                    else {
                        var last = -1
                        LocalVisionAnalyzer.downloadModels(task) { downloaded, total ->
                            val percent = (downloaded * 100 / total).toInt().coerceIn(0, 99)
                            if (percent != last) { last = percent; scope.launch { localProgress = percent } }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    localError = error.message ?: "本地视觉模型操作失败，请重试"
                } finally {
                    expressionsInstalled = LocalVisionAnalyzer.modelsInstalled(LocalVisionTask.FacialExpressions)
                    gesturesInstalled = LocalVisionAnalyzer.modelsInstalled(LocalVisionTask.HandGestures)
                    localTaskBusy = null
                    localModelJob = null
                }
            }
        },
        onLocalCancel = { localModelJob?.cancel() },
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
    localVisionSupported: Boolean = false,
    localModelsDownloadable: Boolean = false,
    expressionsInstalled: Boolean = false,
    gesturesInstalled: Boolean = false,
    localTaskBusy: LocalVisionTask? = null,
    localProgress: Int = 0,
    localError: String? = null,
    onLocalModelAction: (LocalVisionTask) -> Unit = {},
    onLocalCancel: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf("yolo") }
    var validationAttempted by rememberSaveable { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = AppColors.Amber, onPrimary = AppColors.Background,
        background = AppColors.Background, surface = AppColors.Card,
        onSurface = Color.White, onSurfaceVariant = AppColors.Secondary, outline = AppColors.Border,
    )) {
        BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.Background).safeDrawingPadding().imePadding()) {
            fun validateAnd(action: () -> Unit) {
                validationAttempted = true
                when {
                    preferences.modelPathError(false) != null -> selectedTab = "yolo"
                    preferences.modelPathError(true) != null -> selectedTab = "expression"
                    preferences.modelPathError(false, gesture = true) != null -> selectedTab = "gesture"
                    else -> action()
                }
            }
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
                            validateAnd(onSave)
                        },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("vision.save"),
                    enabled = !yoloBusy && localTaskBusy == null,
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("保存") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("yolo" to "YOLO 目标检测", "expression" to "表情识别", "gesture" to "手势识别").forEach { (tab, title) ->
                        val selected = selectedTab == tab
                        Surface(
                            onClick = { selectedTab = tab },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .testTag("vision.tab.$tab")
                                .semantics { this.selected = selected; role = Role.Tab },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) AppColors.Amber.copy(alpha = .14f) else AppColors.Card,
                            border = BorderStroke(1.dp, if (selected) AppColors.Amber else AppColors.Border),
                        ) {
                            Text(title, Modifier.padding(horizontal = 4.dp, vertical = 10.dp), color = if (selected) AppColors.Amber else AppColors.Secondary,
                                fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                key(selectedTab) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("vision.configuration"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val yoloTab = selectedTab == "yolo"
                        val expressionTab = selectedTab == "expression"
                        val enabled = when (selectedTab) { "expression" -> preferences.expressionEnabled; "gesture" -> preferences.gestureEnabled; else -> preferences.yoloEnabled }
                        val path = when (selectedTab) { "expression" -> preferences.expressionModelPath; "gesture" -> preferences.gestureModelPath; else -> preferences.yoloModelPath }
                        val threshold = when (selectedTab) { "expression" -> preferences.expressionConfidenceThreshold; "gesture" -> preferences.gestureConfidenceThreshold; else -> preferences.yoloConfidenceThreshold }
                        val prefix = "vision.$selectedTab"
                        val localTask = if (expressionTab) LocalVisionTask.FacialExpressions else LocalVisionTask.HandGestures
                        val localModelsInstalled = if (expressionTab) expressionsInstalled else gesturesInstalled
                        VisionPanel {
                            Text(when (selectedTab) { "expression" -> "人脸表情识别"; "gesture" -> "手部手势识别"; else -> "YOLO 目标检测" }, color = Color.White,
                                fontSize = if (compact) 20.sp else 24.sp, fontWeight = FontWeight.SemiBold)
                            Text(when (selectedTab) {
                                "expression" -> "人脸框 + MobileFaceNet：识别七种可见表情，结果只表示外观线索。"
                                "gesture" -> "掌心检测 + 21 点手骨架：识别点赞、手掌、握拳、V 字、指向和捏合。"
                                else -> "识别人、物体与周围环境中的目标。"
                            },
                                color = AppColors.Secondary, fontSize = 13.sp)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .toggleable(enabled, role = Role.Switch) {
                                        onPreferencesChange(when (selectedTab) {
                                            "expression" -> preferences.copy(expressionEnabled = it)
                                            "gesture" -> preferences.copy(gestureEnabled = it)
                                            else -> preferences.copy(yoloEnabled = it)
                                        })
                                    }.testTag("$prefix.enabled"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("启用${when (selectedTab) { "expression" -> "表情识别"; "gesture" -> "手势识别"; else -> "目标检测" }}", color = Color.White)
                                    Text(if (yoloTab) "YOLO 与本地模型各自运行" else "仅在相机页面本机推理", color = AppColors.Secondary, fontSize = 12.sp)
                                }
                                Switch(checked = enabled, onCheckedChange = null)
                            }
                            HorizontalDivider(color = AppColors.Border)
                            Text(when (selectedTab) { "expression" -> "人脸检测 · ONNX / 七类表情 · ONNX"; "gesture" -> "MediaPipe 掌心与手关键点 · ONNX"; else -> "YOLO26m · ONNX · COCO 80 类" },
                                color = AppColors.Amber, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (yoloTab) {
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
                                    enabled = yoloSupported && !yoloBusy && localTaskBusy == null,
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
                            if (!yoloTab) {
                                val taskBusy = localTaskBusy == localTask
                                val taskTitle = if (expressionTab) "人脸表情模型" else "手势识别模型"
                                Text(
                                    when {
                                        !localVisionSupported -> "此设备无法运行本地表情/手势识别"
                                        !localModelsDownloadable -> "使用设备系统 Vision，本地运行，无需额外模型"
                                        taskBusy -> "正在${if (localModelsInstalled) "删除" else "下载"}$taskTitle… ${localProgress}%"
                                        localModelsInstalled -> "${taskTitle}已下载 · 可离线运行"
                                        else -> "${taskTitle}未下载 · 约 ${if (expressionTab) "5.0" else "8.0"} MB"
                                    }, color = AppColors.Secondary, fontSize = 13.sp,
                                    modifier = Modifier.testTag("$prefix.modelStatus"),
                                )
                                if (localTaskBusy == localTask && localModelsDownloadable) LinearProgressIndicator(
                                    progress = { localProgress / 100f }, modifier = Modifier.fillMaxWidth(),
                                )
                                if (localModelsDownloadable) {
                                    Button(onClick = { onLocalModelAction(localTask) },
                                        enabled = localVisionSupported && localTaskBusy == null && !yoloBusy,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("$prefix.modelAction")) {
                                        Text(if (taskBusy) "处理中…" else if (localModelsInstalled) "删除模型" else "下载$taskTitle")
                                    }
                                    if (taskBusy) OutlinedButton(onClick = onLocalCancel,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("$prefix.cancel")) { Text("取消") }
                                    if (!taskBusy && localError != null) Text(localError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                                Text(
                                    if (localModelsDownloadable) "留空使用预训练模型，也可填写兼容的自定义 ONNX 文件。"
                                    else "iOS 使用系统 Vision；当前阈值用于筛选系统分析线索。",
                                    color = AppColors.Secondary, fontSize = 12.sp,
                                )
                            }
                            val pathError = if (validationAttempted) preferences.modelPathError(yoloTab, gesture = selectedTab == "gesture") else null
                            if (yoloTab || localModelsDownloadable) OutlinedTextField(
                                value = path,
                                onValueChange = {
                                    onPreferencesChange(when (selectedTab) {
                                        "expression" -> preferences.copy(expressionModelPath = it)
                                        "gesture" -> preferences.copy(gestureModelPath = it)
                                        else -> preferences.copy(yoloModelPath = it)
                                    })
                                },
                                label = { Text(when (selectedTab) { "expression" -> "自定义表情分类模型"; "gesture" -> "自定义手关键点模型"; else -> "自定义 YOLO 模型路径（可选）" }) },
                                placeholder = { Text(when (selectedTab) { "expression" -> "例如 /models/face-expression.onnx"; "gesture" -> "例如 /models/hand-pose.onnx"; else -> "例如 /models/yolo26m.onnx" }) },
                                supportingText = { Text(pathError ?: if (selectedTab == "yolo") "留空使用下载模型；也可填写自己的 .onnx 路径" else "留空使用已下载的本地模型") },
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
                                    onPreferencesChange(when (selectedTab) {
                                        "expression" -> preferences.copy(expressionConfidenceThreshold = it)
                                        "gesture" -> preferences.copy(gestureConfidenceThreshold = it)
                                        else -> preferences.copy(yoloConfidenceThreshold = it)
                                    })
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("$prefix.confidence")
                                    .semantics { contentDescription = "${if (selectedTab == "yolo") "YOLO" else if (selectedTab == "expression") "表情识别" else "手势识别"} 置信度阈值" },
                            )
                            Text("阈值越高，保留的识别结果越少，筛选越严格。", color = AppColors.Secondary, fontSize = 12.sp)
                        }
                        VisionPanel {
                            Text("接入状态", color = AppColors.Amber, fontWeight = FontWeight.SemiBold)
                            Text(
                                when (selectedTab) {
                                    "expression" -> "Android/桌面端支持本地七类表情分类；iOS 使用系统 Vision 提供的脸部关键点与可见动作线索。"
                                    "gesture" -> "三端本地分析手势；Android/桌面使用 21 点手骨架，iOS 使用系统 Vision。"
                                    else -> "Android / 桌面端支持模型按需下载与本地单帧推理；iOS YOLO 推理后端待接入。"
                                },
                                color = AppColors.Secondary, fontSize = 13.sp,
                            )
                            if (selectedTab == "yolo" && spacious) Text("模型格式：FP32 [1,3,640,640] → [1,N,6]，使用 nms=False 导出。",
                                color = AppColors.Secondary, fontSize = 12.sp)
                            Text("保存配置不会启动摄像头；可在摄像头页面启动预览与本地识别。", color = AppColors.Secondary, fontSize = 12.sp)
                        }
                        VisionPanel {
                            Text("摄像头", color = AppColors.Amber, fontWeight = FontWeight.SemiBold)
                            Text("启动预览后，可运行已启用的 YOLO、表情与手势本地分析。", color = AppColors.Secondary, fontSize = 13.sp)
                            OutlinedButton(
                                onClick = {
                                    validateAnd(onOpenCamera)
                                },
                                enabled = !yoloBusy && localTaskBusy == null,
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
