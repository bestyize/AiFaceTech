package com.aifacemate.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.saveable.Saver
import com.aifacemate.face.capability.Availability
import com.aifacemate.face.capability.ModelStore
import com.aifacemate.face.capability.ProviderRegistry
import com.aifacemate.face.capability.formatModelSize
import com.aifacemate.face.vision.VisionPreferences
import com.aifacemate.face.vision.VisionPreferencesStore
import com.aifacemate.face.vision.VisionRuntime
import com.aifacemate.face.vision.VisionTask
import com.aifacemate.face.vision.visionProviders
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
    val task: VisionTask,
) {
    Yolo("yolo", "目标检测", "目标", "识别画面中的人和常见物体", 0.25f, VisionTask.Objects),
    Expression("expression", "表情识别", "表情", "识别人脸的可见表情，结果只代表外观线索", 0.60f, VisionTask.Expressions),
    Gesture("gesture", "手势识别", "手势", "识别点赞、手掌、握拳、V 字、指向和捏合", 0.70f, VisionTask.Gestures),
}

/** What the settings page shows for one task. Built from the provider registry, or from fixtures in UI checks. */
data class VisionTaskState(
    val modelName: String,
    val modelDetail: String,
    val supported: Boolean = true,
    val unsupportedReason: String? = null,
    val downloadable: Boolean = true,
    /** Every model is on the device, or the provider needs none. */
    val installed: Boolean = false,
    val busy: Boolean = false,
    val progress: Int = 0,
    val error: String? = null,
    /** Shown only when a capability has more than one packaged provider. */
    val choices: List<VisionProviderChoice> = emptyList(),
)

data class VisionProviderChoice(val capabilityId: String, val providerId: String, val label: String, val selected: Boolean)

fun ProviderRegistry.settingsState(task: VisionTask, preferences: VisionPreferences): VisionTaskState {
    val selected = visionProviders(task, preferences)
    val choices = task.capabilities.zip(selected).flatMap { (capability, chosen) ->
        providers(capability).takeIf { it.size > 1 }.orEmpty().map { provider ->
            VisionProviderChoice(capability.id, provider.id, provider.displayName, provider.id == chosen?.id)
        }
    }
    if (selected.any { it == null }) {
        return VisionTaskState("暂未接入", "此设备还没有可用的实现", supported = false,
            unsupportedReason = "此设备暂未接入", downloadable = false, choices = choices)
    }
    val providers = selected.filterNotNull()
    val unsupported = providers.map { availability(it) }.filterIsInstance<Availability.Unsupported>().firstOrNull()
    val models = providers.flatMap { it.models }.distinct()
    return VisionTaskState(
        modelName = providers.map { it.displayName }.distinct().joinToString(" + "),
        modelDetail = providers.joinToString(" + ") { it.description } +
            if (models.isEmpty()) "" else " · ${formatModelSize(models.sumOf { it.sizeBytes })}",
        supported = unsupported == null,
        unsupportedReason = unsupported?.reason,
        downloadable = models.isNotEmpty(),
        installed = providers.all { availability(it) == Availability.Ready },
        choices = choices,
    )
}

private val providerIdsSaver = Saver<Map<String, String>, ArrayList<String>>(
    save = { ids -> ArrayList(ids.flatMap { listOf(it.key, it.value) }) },
    restore = { saved -> saved.chunked(2) { (capability, provider) -> capability to provider }.toMap() },
)

private data class VisionStatus(val label: String, val color: Color, val active: Boolean)

@Composable
fun VisionSettingsScreen(
    onBack: () -> Unit,
    onOpenCamera: () -> Unit = {},
    registry: ProviderRegistry = VisionRuntime.registry,
    store: ModelStore = VisionRuntime.modelStore,
    initialPreferences: VisionPreferences? = null,
    persistPreferences: (VisionPreferences) -> Unit = VisionPreferencesStore::save,
) {
    val initial = remember { initialPreferences ?: VisionPreferencesStore.load() }
    // Changes are persisted synchronously; saveable fields also preserve the current UI through recreation.
    var yoloEnabled by rememberSaveable { mutableStateOf(initial.yoloEnabled) }
    var yoloThreshold by rememberSaveable { mutableStateOf(initial.yoloConfidenceThreshold) }
    var expressionEnabled by rememberSaveable { mutableStateOf(initial.expressionEnabled) }
    var expressionThreshold by rememberSaveable { mutableStateOf(initial.expressionConfidenceThreshold) }
    var gestureEnabled by rememberSaveable { mutableStateOf(initial.gestureEnabled) }
    var gestureThreshold by rememberSaveable { mutableStateOf(initial.gestureConfidenceThreshold) }
    var providerIds by rememberSaveable(stateSaver = providerIdsSaver) { mutableStateOf(initial.providerIds) }
    val scope = rememberCoroutineScope()
    var busyTask by remember { mutableStateOf<VisionTask?>(null) }
    var progress by remember { mutableStateOf(0) }
    var errors by remember { mutableStateOf(emptyMap<VisionTask, String>()) }
    var job by remember { mutableStateOf<Job?>(null) }
    val draft = VisionPreferences(yoloEnabled, yoloThreshold, expressionEnabled, expressionThreshold,
        gestureEnabled, gestureThreshold, providerIds)
    // Recomputed on every recomposition; installs and deletions end by clearing busyTask, which recomposes.
    val tasks = VisionTask.entries.associateWith { task ->
        registry.settingsState(task, draft).copy(
            busy = busyTask == task, progress = progress, error = errors[task]?.takeIf { busyTask == null },
        )
    }
    VisionSettingsContent(
        preferences = draft,
        onPreferencesChange = {
            persistPreferences(it.normalized())
            yoloEnabled = it.yoloEnabled
            yoloThreshold = it.yoloConfidenceThreshold
            expressionEnabled = it.expressionEnabled
            expressionThreshold = it.expressionConfidenceThreshold
            gestureEnabled = it.gestureEnabled
            gestureThreshold = it.gestureConfidenceThreshold
            providerIds = it.providerIds
        },
        onBack = onBack,
        onOpenCamera = onOpenCamera,
        tasks = tasks,
        onModelAction = { task ->
            val models = registry.visionProviders(task, draft).filterNotNull().flatMap { it.models }.distinct()
            val remove = tasks.getValue(task).installed
            busyTask = task
            progress = 0
            errors = errors - task
            job = scope.launch {
                try {
                    if (remove) store.remove(models)
                    else {
                        var last = -1
                        store.install(models) { downloaded, total ->
                            val percent = if (total > 0) (downloaded * 100 / total).toInt().coerceIn(0, 99) else 0
                            if (percent != last) { last = percent; scope.launch { progress = percent } }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    errors = errors + (task to (error.message ?: "模型操作失败，请重试"))
                } finally {
                    busyTask = null
                    job = null
                }
            }
        },
        onCancel = { job?.cancel() },
    )
}

/** Pure UI, so rendering and interaction checks never access the user's saved settings. */
@Composable
fun VisionSettingsContent(
    preferences: VisionPreferences,
    onPreferencesChange: (VisionPreferences) -> Unit,
    onBack: () -> Unit,
    tasks: Map<VisionTask, VisionTaskState>,
    onModelAction: (VisionTask) -> Unit = {},
    onCancel: (VisionTask) -> Unit = {},
    onOpenCamera: () -> Unit = {},
) {
    var selectedKey by rememberSaveable { mutableStateOf(VisionTab.Yolo.key) }
    val selected = VisionTab.entries.first { it.key == selectedKey }
    val anyBusy = tasks.values.any { it.busy }

    fun state(tab: VisionTab) = tasks.getValue(tab.task)
    fun enabled(tab: VisionTab) = preferences.enabled(tab.task)
    fun status(tab: VisionTab): VisionStatus {
        val state = state(tab)
        return when {
            state.busy -> VisionStatus("下载中 ${state.progress}%", VisionGold, true)
            !enabled(tab) -> VisionStatus("未开启", VisionMuted, false)
            !state.supported -> VisionStatus("设备不支持", VisionError, false)
            state.installed -> VisionStatus("已开启", VisionGreen, true)
            else -> VisionStatus("需下载模型", VisionGold, false)
        }
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
                if (!sideBySide) VisionHeader(compact, showBackLabel = showBackLabel, onBack = onBack)
                val enabledCount = VisionTab.entries.count { enabled(it) }

                @Composable
                fun Detail(modifier: Modifier, includeCamera: Boolean) {
                    key(selected) {
                        Column(
                            modifier.verticalScroll(rememberScrollState()).testTag("vision.configuration"),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
                        ) {
                            val task = selected.task
                            VisionDetail(
                                tab = selected,
                                enabled = enabled(selected),
                                state = state(selected),
                                threshold = preferences.threshold(task),
                                actionsLocked = anyBusy,
                                compact = compact,
                                showIcon = !narrowDetail,
                                onEnabledChange = { onPreferencesChange(preferences.update(task, enabled = it)) },
                                onThresholdChange = { onPreferencesChange(preferences.update(task, threshold = it)) },
                                onProviderChange = { capability, provider -> onPreferencesChange(preferences.withProvider(capability, provider)) },
                                onModelAction = { onModelAction(task) },
                                onCancel = { onCancel(task) },
                            )
                            if (includeCamera) VisionCameraAction(enabledCount, !anyBusy, onClick = onOpenCamera)
                            VisionHint("修改立即保存。打开摄像头后自动预览，画面仅在本机分析；点击拍照才保存照片。")
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
                            VisionCameraAction(enabledCount, !anyBusy, showHint = !shortRail, onClick = onOpenCamera)
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
private fun VisionHeader(compact: Boolean, showBackLabel: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VisionBackButton(showBackLabel, onClick = onBack)
        Column(Modifier.weight(1f)) {
            Text("视觉", color = VisionText, fontSize = if (compact) 18.sp else 24.sp, fontWeight = FontWeight.SemiBold)
            if (!compact) Text("修改立即保存，识别在本机完成", color = VisionMuted, fontSize = 12.sp)
        }
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
private fun VisionProviderChip(choice: VisionProviderChoice, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.heightIn(min = 48.dp).clip(shape)
            .background(if (choice.selected) Color(0xFF393329) else Color(0xFF171B1B))
            .border(1.dp, if (choice.selected) VisionGold else VisionBorder.copy(alpha = .65f), shape)
            .selectable(choice.selected, role = Role.RadioButton, onClick = onClick)
            .testTag("vision.provider.${choice.providerId}")
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(choice.label, color = if (choice.selected) VisionGold else VisionText, fontSize = 13.sp,
            fontWeight = if (choice.selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text("打开摄像头", fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
private fun VisionDetail(
    tab: VisionTab,
    enabled: Boolean,
    state: VisionTaskState,
    threshold: Float,
    actionsLocked: Boolean,
    compact: Boolean,
    showIcon: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onProviderChange: (capabilityId: String, providerId: String) -> Unit,
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
        state.choices.groupBy { it.capabilityId }.values.forEach { options ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { choice ->
                    VisionProviderChip(choice, Modifier.weight(1f)) { onProviderChange(choice.capabilityId, choice.providerId) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(state.modelName, color = VisionText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(state.modelDetail, color = VisionMuted, fontSize = 12.sp)
                val (label, color) = when {
                    !state.supported -> (state.unsupportedReason ?: "此设备暂不支持") to VisionError
                    !state.downloadable -> "系统内置 · 无需下载" to VisionGreen
                    state.busy -> "正在处理… ${state.progress}%" to VisionGold
                    state.installed -> "已下载 · 可离线使用" to VisionGreen
                    else -> "未下载" to VisionGold
                }
                Text(label, color = color, fontSize = 12.sp, modifier = Modifier.testTag("$prefix.modelStatus"))
            }
            if (state.downloadable) {
                if (state.busy) OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("$prefix.cancel"),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("取消", fontSize = 13.sp) }
                else if (state.installed) OutlinedButton(
                    onClick = onModelAction,
                    enabled = state.supported && !actionsLocked,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("$prefix.modelAction"),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("删除", fontSize = 13.sp) }
                else Button(
                    onClick = onModelAction,
                    enabled = state.supported && !actionsLocked,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("$prefix.modelAction"),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("下载", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (state.busy) LinearProgressIndicator(
            progress = { state.progress / 100f },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)),
            color = VisionGold, trackColor = Color(0xFF393A35),
        )
        state.error?.let { Text(it, color = VisionError, fontSize = 12.sp, modifier = Modifier.testTag("$prefix.modelError")) }
        if (enabled && state.supported && !state.installed && !state.busy)
            Text("下载后才能在摄像头中识别", color = VisionGold, fontSize = 12.sp)
        VisionHint(if (state.downloadable) "模型不打包进安装包，首次识别时载入，离开摄像头后释放内存。"
            else "使用系统内置模型在本机分析。")
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
