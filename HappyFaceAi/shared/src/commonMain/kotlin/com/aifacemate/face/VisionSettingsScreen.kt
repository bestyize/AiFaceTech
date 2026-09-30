package com.aifacemate.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.vision.VisionPreferences
import com.aifacemate.face.vision.VisionPreferencesStore
import com.aifacemate.face.vision.LocalVisionAnalyzer
import com.aifacemate.face.vision.LocalVisionTask
import com.aifacemate.face.vision.Yolo26mModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val VisionGold = Color(0xFFF1C779)
private val VisionText = Color(0xFFF4F3EF)
private val VisionMuted = Color(0xFFA7AAA8)
private val VisionBackground = Color(0xFF0C1011)
private val VisionBorder = Color(0xFF48443A)
private val VisionGreen = Color(0xFF36D6AB)
private val VisionError = Color(0xFFFF8A7A)
private val VisionShape = RoundedCornerShape(16.dp)

private enum class VisionTab(
    val key: String,
    val title: String,
    val shortTitle: String,
    val summary: String,
    val defaultThreshold: Float,
    val localTask: LocalVisionTask?,
) {
    Yolo("yolo", "目标检测", "目标", "识别画面中的人和常见物体", 0.25f, null),
    Expression("expression", "表情识别", "表情", "识别七种可见表情，结果只代表外观线索", 0.60f, LocalVisionTask.FacialExpressions),
    Gesture("gesture", "手势识别", "手势", "识别点赞、手掌、握拳、V 字、指向和捏合", 0.70f, LocalVisionTask.HandGestures),
}

private data class VisionModel(
    val name: String,
    val detail: String,
    val supported: Boolean,
    val downloadable: Boolean,
    val installed: Boolean,
    val busy: Boolean,
    val progress: Int,
    val error: String?,
) {
    fun ready(customPath: Boolean) = supported && (!downloadable || customPath || installed)
}

private data class VisionStatus(val label: String, val color: Color, val active: Boolean)

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
    var localErrorTask by remember { mutableStateOf<LocalVisionTask?>(null) }
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
        localErrorTask = localErrorTask,
        onLocalModelAction = { task ->
            localTaskBusy = task
            localProgress = 0
            localError = null
            localErrorTask = null
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
                    localErrorTask = task
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
    localErrorTask: LocalVisionTask? = localTaskBusy,
    onLocalModelAction: (LocalVisionTask) -> Unit = {},
    onLocalCancel: () -> Unit = {},
) {
    var selectedKey by rememberSaveable { mutableStateOf(VisionTab.Yolo.key) }
    val selected = VisionTab.entries.first { it.key == selectedKey }
    var validationAttempted by rememberSaveable { mutableStateOf(false) }
    val anyBusy = yoloBusy || localTaskBusy != null

    fun model(tab: VisionTab): VisionModel = when (tab) {
        VisionTab.Yolo -> VisionModel("YOLO26m", "COCO 80 类 · 约 82 MB", yoloSupported, true,
            yoloInstalled, yoloBusy, yoloProgress, yoloError)
        else -> {
            val task = tab.localTask!!
            val expression = tab == VisionTab.Expression
            VisionModel(
                name = if (localModelsDownloadable) { if (expression) "YuNet + MobileFaceNet" else "MediaPipe 手部关键点" } else "系统 Vision",
                detail = if (localModelsDownloadable) { if (expression) "人脸检测 + 七类表情 · 约 5.0 MB" else "掌心检测 + 21 点骨架 · 约 8.0 MB" }
                    else if (expression) "脸部关键点与可见动作线索" else "系统手部关键点",
                supported = localVisionSupported,
                downloadable = localModelsDownloadable,
                installed = if (expression) expressionsInstalled else gesturesInstalled,
                busy = localTaskBusy == task,
                progress = localProgress,
                error = localError.takeIf { localErrorTask == task && localTaskBusy == null },
            )
        }
    }
    fun enabled(tab: VisionTab) = when (tab) {
        VisionTab.Yolo -> preferences.yoloEnabled
        VisionTab.Expression -> preferences.expressionEnabled
        VisionTab.Gesture -> preferences.gestureEnabled
    }
    fun path(tab: VisionTab) = when (tab) {
        VisionTab.Yolo -> preferences.yoloModelPath
        VisionTab.Expression -> preferences.expressionModelPath
        VisionTab.Gesture -> preferences.gestureModelPath
    }
    fun pathError(tab: VisionTab) =
        preferences.modelPathError(expression = tab == VisionTab.Expression, gesture = tab == VisionTab.Gesture)
    fun status(tab: VisionTab): VisionStatus {
        val model = model(tab)
        return when {
            model.busy -> VisionStatus("下载中 ${model.progress}%", VisionGold, true)
            !enabled(tab) -> VisionStatus("未开启", VisionMuted, false)
            !model.supported -> VisionStatus("设备不支持", VisionError, false)
            model.ready(path(tab).isNotBlank()) -> VisionStatus("已开启", VisionGreen, true)
            else -> VisionStatus("需下载模型", VisionGold, false)
        }
    }
    fun validateAnd(action: () -> Unit) {
        validationAttempted = true
        val invalid = VisionTab.entries.firstOrNull { pathError(it) != null }
        if (invalid != null) selectedKey = invalid.key else action()
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = VisionGold, onPrimary = Color(0xFF221D13),
        background = VisionBackground, surface = Color(0xFF191C1C),
        onSurface = VisionText, onSurfaceVariant = VisionMuted, outline = VisionBorder, error = VisionError,
    )) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(VisionBackground).safeDrawingPadding().imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val split = maxWidth.value / fontScale >= 680f && maxHeight >= 300.dp
            val compact = maxHeight < 400.dp || maxWidth < 600.dp
            val inset = if (compact) 12.dp else 20.dp
            val railWidth = (maxWidth * .3f).coerceIn(220.dp, 300.dp)
            val showBackLabel = maxWidth.value / fontScale >= 420f
            val shortRail = maxHeight < 440.dp
            val landscape = maxWidth > maxHeight
            val iconRail = maxWidth < 400.dp
            val sideRailWidth = if (iconRail) 56.dp else (88 * fontScale).dp
            val narrowDetail = !split && landscape && maxWidth - inset * 2 - sideRailWidth < 360.dp
            Column(
                Modifier.widthIn(max = 1100.dp).fillMaxSize().padding(horizontal = inset),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
            ) {
                val sideBySide = split || landscape
                if (!sideBySide) VisionHeader(compact, showBackLabel = showBackLabel, saveEnabled = !anyBusy,
                    onBack = onBack, onSave = { validateAnd(onSave) })
                val enabledCount = VisionTab.entries.count { enabled(it) }

                @Composable
                fun Detail(modifier: Modifier, includeCamera: Boolean) {
                    key(selected) {
                        Column(
                            modifier.verticalScroll(rememberScrollState()).testTag("vision.configuration"),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
                        ) {
                            VisionDetail(
                                tab = selected,
                                enabled = enabled(selected),
                                model = model(selected),
                                path = path(selected),
                                threshold = when (selected) {
                                    VisionTab.Yolo -> preferences.yoloConfidenceThreshold
                                    VisionTab.Expression -> preferences.expressionConfidenceThreshold
                                    VisionTab.Gesture -> preferences.gestureConfidenceThreshold
                                },
                                pathError = if (validationAttempted) pathError(selected) else null,
                                actionsLocked = anyBusy,
                                compact = compact,
                                showIcon = !narrowDetail,
                                onEnabledChange = {
                                    onPreferencesChange(when (selected) {
                                        VisionTab.Yolo -> preferences.copy(yoloEnabled = it)
                                        VisionTab.Expression -> preferences.copy(expressionEnabled = it)
                                        VisionTab.Gesture -> preferences.copy(gestureEnabled = it)
                                    })
                                },
                                onPathChange = {
                                    onPreferencesChange(when (selected) {
                                        VisionTab.Yolo -> preferences.copy(yoloModelPath = it)
                                        VisionTab.Expression -> preferences.copy(expressionModelPath = it)
                                        VisionTab.Gesture -> preferences.copy(gestureModelPath = it)
                                    })
                                },
                                onThresholdChange = {
                                    onPreferencesChange(when (selected) {
                                        VisionTab.Yolo -> preferences.copy(yoloConfidenceThreshold = it)
                                        VisionTab.Expression -> preferences.copy(expressionConfidenceThreshold = it)
                                        VisionTab.Gesture -> preferences.copy(gestureConfidenceThreshold = it)
                                    })
                                },
                                onModelAction = { if (selected == VisionTab.Yolo) onYoloModelAction() else onLocalModelAction(selected.localTask!!) },
                                onCancel = { if (selected == VisionTab.Yolo) onYoloCancel() else onLocalCancel() },
                            )
                            if (includeCamera) VisionCameraAction(enabledCount, !anyBusy) { validateAnd(onOpenCamera) }
                            VisionHint("保存不会启动摄像头；打开预览后，画面只在本机分析，不保存也不上传。")
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }

                if (split) {
                    Row(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.width(railWidth).fillMaxHeight().padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                VisionBackButton(showLabel = true, onClick = onBack)
                                Text("视觉", color = VisionText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!shortRail) Text("识别能力", color = VisionMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp))
                                VisionTab.entries.forEach { tab ->
                                    VisionTaskCard(tab, status(tab), selected == tab, Modifier.fillMaxWidth(), dense = shortRail) { selectedKey = tab.key }
                                }
                            }
                            VisionCameraAction(enabledCount, !anyBusy, showHint = !shortRail) { validateAnd(onOpenCamera) }
                            VisionSaveButton(!anyBusy, Modifier.fillMaxWidth()) { validateAnd(onSave) }
                        }
                        Detail(Modifier.weight(1f).fillMaxHeight(), includeCamera = false)
                    }
                } else if (landscape) {
                    Row(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.width(sideRailWidth).fillMaxHeight().padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            VisionBackButton(showLabel = !iconRail, Modifier.fillMaxWidth(), onClick = onBack)
                            Column(
                                Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("vision.rail"),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                VisionTab.entries.forEach { tab ->
                                    VisionTaskPill(tab, status(tab), selected == tab, Modifier.fillMaxWidth(), showTitle = !iconRail) { selectedKey = tab.key }
                                }
                            }
                            VisionSaveButton(!anyBusy, Modifier.fillMaxWidth(), horizontalPadding = 4.dp) { validateAnd(onSave) }
                        }
                        Detail(Modifier.weight(1f).fillMaxHeight(), includeCamera = true)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VisionTab.entries.forEach { tab ->
                            VisionTaskPill(tab, status(tab), selected == tab, Modifier.weight(1f)) { selectedKey = tab.key }
                        }
                    }
                    Detail(Modifier.weight(1f).fillMaxWidth(), includeCamera = true)
                }
            }
        }
    }
}

@Composable
private fun VisionHeader(compact: Boolean, showBackLabel: Boolean, saveEnabled: Boolean, onBack: () -> Unit, onSave: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VisionBackButton(showBackLabel, onClick = onBack)
        Column(Modifier.weight(1f)) {
            Text("视觉", color = VisionText, fontSize = if (compact) 18.sp else 24.sp, fontWeight = FontWeight.SemiBold)
            if (!compact) Text("让小星看懂身边的人和手势，全部在本机完成", color = VisionMuted, fontSize = 12.sp)
        }
        VisionSaveButton(saveEnabled, horizontalPadding = if (compact) 14.dp else 22.dp, onClick = onSave)
    }
}

@Composable
private fun VisionBackButton(showLabel: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).testTag("vision.back").semantics { contentDescription = "返回生命中心" },
        contentPadding = PaddingValues(horizontal = if (showLabel) 12.dp else 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Canvas(Modifier.size(16.dp)) {
            drawLine(VisionGold, Offset(size.width * .75f, size.height * .12f), Offset(size.width * .3f, size.height * .5f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(VisionGold, Offset(size.width * .3f, size.height * .5f), Offset(size.width * .75f, size.height * .88f), 2.dp.toPx(), StrokeCap.Round)
        }
        if (showLabel) {
            Spacer(Modifier.width(4.dp))
            Text("返回", fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun VisionSaveButton(enabled: Boolean, modifier: Modifier = Modifier, horizontalPadding: Dp = 22.dp, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).testTag("vision.save"),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
    ) { Text("保存", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun VisionTaskCard(tab: VisionTab, status: VisionStatus, selected: Boolean, modifier: Modifier, dense: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.heightIn(min = 56.dp).clip(shape)
            .background(if (selected) Color(0xFF393329) else Color(0xFF171B1B))
            .border(1.dp, if (selected) VisionGold else VisionBorder.copy(alpha = .65f), shape)
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .semantics { stateDescription = status.label }
            .testTag("vision.tab.${tab.key}")
            .padding(horizontal = 10.dp, vertical = if (dense) 5.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VisionIconTile(tab, selected, if (dense) 32.dp else 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (dense) 0.dp else 2.dp)) {
            Text(tab.title, color = if (selected) VisionGold else VisionText, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            VisionStatusLabel(status)
        }
    }
}

@Composable
private fun VisionTaskPill(tab: VisionTab, status: VisionStatus, selected: Boolean, modifier: Modifier, showTitle: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.heightIn(min = 48.dp).clip(shape)
            .background(if (selected) Color(0xFF393329) else Color(0xFF171B1B))
            .border(1.dp, if (selected) VisionGold else VisionBorder.copy(alpha = .65f), shape)
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .semantics { stateDescription = status.label; if (!showTitle) contentDescription = tab.title }
            .testTag("vision.tab.${tab.key}")
            .padding(horizontal = if (showTitle) 8.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        VisionTaskIcon(tab, if (selected) VisionGold else VisionMuted, Modifier.size(18.dp))
        if (showTitle) Text(tab.shortTitle, color = if (selected) VisionGold else VisionText, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
        Box(Modifier.size(7.dp).background(if (status.active) status.color else VisionMuted.copy(alpha = .35f), CircleShape))
    }
}

@Composable
private fun VisionStatusLabel(status: VisionStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).background(status.color, CircleShape))
        Text(status.label, color = status.color, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun VisionCameraAction(enabledCount: Int, enabled: Boolean, showHint: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showHint) Text(if (enabledCount == 0) "尚未开启识别，摄像头仅显示预览" else "摄像头将运行已开启的 $enabledCount 项识别",
            color = VisionMuted, fontSize = 12.sp)
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("vision.camera"),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            val tint = if (enabled) VisionGold else VisionMuted
            Canvas(Modifier.size(18.dp)) {
                val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawRoundRect(tint, Offset(size.width * .06f, size.height * .26f), Size(size.width * .88f, size.height * .62f),
                    CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(tint, Offset(size.width * .34f, size.height * .26f), Offset(size.width * .42f, size.height * .12f), stroke.width)
                drawLine(tint, Offset(size.width * .42f, size.height * .12f), Offset(size.width * .58f, size.height * .12f), stroke.width)
                drawLine(tint, Offset(size.width * .58f, size.height * .12f), Offset(size.width * .66f, size.height * .26f), stroke.width)
                drawCircle(tint, size.width * .16f, Offset(size.width * .5f, size.height * .57f), style = stroke)
            }
            Spacer(Modifier.width(8.dp))
            Text("保存并打开摄像头", fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
private fun VisionDetail(
    tab: VisionTab,
    enabled: Boolean,
    model: VisionModel,
    path: String,
    threshold: Float,
    pathError: String?,
    actionsLocked: Boolean,
    compact: Boolean,
    showIcon: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onPathChange: (String) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onModelAction: () -> Unit,
    onCancel: () -> Unit,
) {
    val prefix = "vision.${tab.key}"
    val padding = if (compact) 12.dp else 16.dp
    Row(
        Modifier.fillMaxWidth().clip(VisionShape)
            .background(Brush.linearGradient(listOf(Color(0xFF2A2720), Color(0xFF141718))))
            .border(1.dp, VisionBorder, VisionShape)
            .toggleable(enabled, role = Role.Switch, onValueChange = onEnabledChange)
            .testTag("$prefix.enabled")
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showIcon) VisionIconTile(tab, true, if (compact) 40.dp else 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(tab.title, color = VisionText, fontSize = if (compact) 16.sp else 19.sp, fontWeight = FontWeight.SemiBold)
            Text(tab.summary, color = VisionMuted, fontSize = 12.sp)
        }
        Switch(
            checked = enabled, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = VisionGreen, checkedThumbColor = Color.White, checkedBorderColor = VisionGreen,
                uncheckedTrackColor = Color(0xFF393A35), uncheckedThumbColor = Color(0xFFE6E4DA), uncheckedBorderColor = Color(0xFF5A5B55),
            ),
        )
    }

    VisionPanel("模型", compact) {
        val customPath = path.isNotBlank()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(model.name, color = VisionText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(model.detail, color = VisionMuted, fontSize = 12.sp)
                val (label, color) = when {
                    !model.supported -> "此设备暂不支持" to VisionError
                    !model.downloadable -> "系统内置 · 无需下载" to VisionGreen
                    model.busy -> "正在处理… ${model.progress}%" to VisionGold
                    model.installed -> "已下载 · 可离线使用" to VisionGreen
                    customPath -> "未下载 · 当前使用自定义模型" to VisionMuted
                    else -> "未下载" to VisionGold
                }
                Text(label, color = color, fontSize = 12.sp, modifier = Modifier.testTag("$prefix.modelStatus"))
            }
            if (model.downloadable) {
                if (model.busy) OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("$prefix.cancel"),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("取消", fontSize = 13.sp) }
                else if (model.installed) OutlinedButton(
                    onClick = onModelAction,
                    enabled = model.supported && !actionsLocked,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("$prefix.modelAction"),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("删除", fontSize = 13.sp) }
                else Button(
                    onClick = onModelAction,
                    enabled = model.supported && !actionsLocked,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("$prefix.modelAction"),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("下载", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (model.busy) LinearProgressIndicator(
            progress = { model.progress / 100f },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)),
            color = VisionGold, trackColor = Color(0xFF393A35),
        )
        model.error?.let { Text(it, color = VisionError, fontSize = 12.sp, modifier = Modifier.testTag("$prefix.modelError")) }
        if (enabled && model.supported && !model.ready(customPath) && !model.busy)
            Text("下载后才能在摄像头中识别", color = VisionGold, fontSize = 12.sp)
        VisionHint(if (model.downloadable) "模型不打包进安装包，首次识别时载入，离开摄像头后释放内存。"
            else "使用设备系统 Vision 在本机分析。")
    }

    VisionPanel("识别阈值", compact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("置信度", Modifier.weight(1f), color = VisionText, fontSize = 14.sp)
            Text("${(threshold * 100).roundToInt()}%", color = VisionGold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = threshold, valueRange = 0f..1f, steps = 19,
            onValueChange = onThresholdChange,
            colors = SliderDefaults.colors(thumbColor = VisionGold, activeTrackColor = VisionGold,
                inactiveTrackColor = Color(0xFF393A35), activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("$prefix.confidence")
                .semantics { contentDescription = "${tab.title}置信度阈值" },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("结果更多", Modifier.weight(1f), color = VisionMuted, fontSize = 12.sp)
            if ((threshold * 100).roundToInt() != (tab.defaultThreshold * 100).roundToInt()) TextButton(
                onClick = { onThresholdChange(tab.defaultThreshold) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("$prefix.confidenceReset"),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text("恢复默认 ${(tab.defaultThreshold * 100).roundToInt()}%", fontSize = 12.sp) }
            Text("筛选更严格", Modifier.weight(1f), color = VisionMuted, fontSize = 12.sp,
                textAlign = TextAlign.End)
        }
    }

    if (model.downloadable) {
        var expanded by rememberSaveable(tab) { mutableStateOf(path.isNotBlank()) }
        val open = expanded || pathError != null
        VisionPanel("高级", compact) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClickLabel = if (open) "收起" else "展开") { expanded = !open }
                    .testTag("$prefix.advanced"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("自定义 ONNX 模型", color = VisionText, fontSize = 14.sp)
                    Text(if (open) "填写本机 .onnx 文件的完整路径" else if (path.isBlank()) "未设置 · 使用上方模型" else path, color = VisionMuted, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Canvas(Modifier.size(16.dp)) {
                    val y1 = size.height * if (open) .62f else .38f
                    val y2 = size.height * if (open) .38f else .62f
                    drawLine(VisionMuted, Offset(size.width * .2f, y1), Offset(size.width * .5f, y2), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(VisionMuted, Offset(size.width * .5f, y2), Offset(size.width * .8f, y1), 2.dp.toPx(), StrokeCap.Round)
                }
            }
            if (open) {
                OutlinedTextField(
                    value = path,
                    onValueChange = onPathChange,
                    label = { Text(when (tab) { VisionTab.Yolo -> "YOLO 模型路径"; VisionTab.Expression -> "表情分类模型路径"; VisionTab.Gesture -> "手关键点模型路径" }, fontSize = 13.sp) },
                    placeholder = { Text(when (tab) { VisionTab.Yolo -> "/models/yolo26m.onnx"; VisionTab.Expression -> "/models/face-expression.onnx"; VisionTab.Gesture -> "/models/hand-pose.onnx" }, fontSize = 13.sp) },
                    supportingText = { Text(pathError ?: "留空使用上方下载的模型", fontSize = 12.sp) },
                    isError = pathError != null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth().testTag("$prefix.modelPath"),
                )
                if (tab == VisionTab.Yolo) VisionHint("需为 FP32 [1,3,640,640] → [1,N,6] 输出，导出时设置 nms=False。")
            }
        }
    }
}

@Composable
private fun VisionPanel(title: String, compact: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(VisionShape)
            .background(Brush.linearGradient(listOf(Color(0xFF222525), Color(0xFF131617))))
            .border(1.dp, VisionBorder, VisionShape).padding(if (compact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(3.dp, 14.dp).background(VisionGold, RoundedCornerShape(2.dp)))
            Text(title, color = VisionText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun VisionHint(text: String) { Text(text, color = VisionMuted, fontSize = 12.sp) }

@Composable
private fun VisionIconTile(tab: VisionTab, highlighted: Boolean, size: Dp) {
    val shape = RoundedCornerShape(size * .28f)
    Box(
        Modifier.size(size).clip(shape)
            .background(if (highlighted) Color(0xFF3A3326) else Color(0xFF222525))
            .border(1.dp, if (highlighted) VisionGold.copy(alpha = .7f) else VisionBorder, shape),
        contentAlignment = Alignment.Center,
    ) { VisionTaskIcon(tab, if (highlighted) VisionGold else VisionMuted, Modifier.size(size * .52f)) }
}

@Composable
private fun VisionTaskIcon(tab: VisionTab, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tab) {
            VisionTab.Yolo -> {
                val c = w * .28f
                listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(w, 0f) to Offset(-1f, 1f),
                    Offset(0f, h) to Offset(1f, -1f), Offset(w, h) to Offset(-1f, -1f)).forEach { (corner, dir) ->
                    val inset = Offset(corner.x + dir.x * w * .06f, corner.y + dir.y * h * .06f)
                    drawLine(color, inset, Offset(inset.x + dir.x * c, inset.y), stroke.width, StrokeCap.Round)
                    drawLine(color, inset, Offset(inset.x, inset.y + dir.y * c), stroke.width, StrokeCap.Round)
                }
                drawCircle(color, w * .16f, Offset(w * .5f, h * .5f), style = stroke)
            }
            VisionTab.Expression -> {
                drawCircle(color, w * .44f, Offset(w * .5f, h * .5f), style = stroke)
                drawCircle(color, w * .055f, Offset(w * .36f, h * .42f))
                drawCircle(color, w * .055f, Offset(w * .64f, h * .42f))
                drawArc(color, 20f, 140f, false, Offset(w * .3f, h * .38f), Size(w * .4f, h * .32f), style = stroke)
            }
            VisionTab.Gesture -> {
                val palm = Path().apply {
                    moveTo(w * .26f, h * .5f)
                    lineTo(w * .26f, h * .2f)
                    moveTo(w * .42f, h * .46f); lineTo(w * .42f, h * .08f)
                    moveTo(w * .58f, h * .46f); lineTo(w * .58f, h * .12f)
                    moveTo(w * .74f, h * .5f); lineTo(w * .74f, h * .24f)
                    moveTo(w * .26f, h * .5f)
                    lineTo(w * .26f, h * .66f)
                    quadraticTo(w * .3f, h * .96f, w * .54f, h * .96f)
                    quadraticTo(w * .74f, h * .96f, w * .78f, h * .74f)
                    lineTo(w * .92f, h * .5f)
                    quadraticTo(w * .86f, h * .42f, w * .74f, h * .54f)
                    lineTo(w * .74f, h * .5f)
                }
                drawPath(palm, color, style = stroke)
            }
        }
    }
}
