package com.aifacemate.face.feature.aibrain

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aifacemate.face.feature.lifecenter.LifeColors
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.ui.AppDialog
import com.aifacemate.face.ui.FaceStage
import com.aifacemate.face.ui.AppInputDialog
import com.aifacemate.face.ui.AppToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardTop = Color(0xFF171C25)
private val CardBottom = Color(0xFF11161E)
private val FieldFill = Color(0xFF12161E)
private val SelectedFill = Color(0xFF292315)
private val ConnectedGreen = Color(0xFF38E66B)

/** 密钥配置里可以编辑的两项。 */
private enum class SecretEdit { BaseUrl, ApiKey }

@Composable
fun AiBrainRoute(onBack: () -> Unit, viewModel: AiBrainViewModel = viewModel()) {
    AiBrainScreen(
        state = viewModel.uiState,
        onBack = onBack,
        onProviderSelected = viewModel::selectProvider,
        onModelSelected = viewModel::selectModel,
        onThinkingModeSelected = viewModel::selectThinkingMode,
        onCapabilityToggled = viewModel::toggleCapability,
        onBaseUrlChanged = viewModel::updateBaseUrl,
        onApiKeyChanged = viewModel::updateApiKey,
        onFetchModels = viewModel::fetchModels,
        onCustomModelAdded = viewModel::addCustomModel,
        onDismissApiKeyPrompt = viewModel::dismissApiKeyPrompt,
        onSaveSecret = viewModel::saveSecret,
        onToastShown = viewModel::consumeToast,
    )
}

/**
 * AI 大脑设置页。
 *
 * 左栏是「当前大脑 + 思考模式」，右栏依次是「模型提供商 / 密钥配置 / 模型选择 / 模型接入」。
 * 两栏各自纵向滚动，横屏优先，窄屏退化为单列。
 */
@Composable
fun AiBrainScreen(
    state: AiBrainUiState,
    onBack: () -> Unit,
    onProviderSelected: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onThinkingModeSelected: (ThinkingMode) -> Unit,
    onCapabilityToggled: (AiCapability) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onFetchModels: () -> Unit,
    onCustomModelAdded: (String) -> Unit,
    onDismissApiKeyPrompt: () -> Unit,
    onSaveSecret: () -> Unit,
    onToastShown: () -> Unit,
) {
    // 页面上的输入项都只做展示，点一行才弹输入弹窗，草稿在这里暂存。
    var editingSecret by remember { mutableStateOf<SecretEdit?>(null) }
    var secretDraft by remember { mutableStateOf("") }
    // 弹窗一律挂在页面根节点上：挂在滚动里的卡片内部时，遮罩和输入条只会盖住那张卡（看着像嵌在页面里）。
    var customModelDialogVisible by remember { mutableStateOf(false) }
    fun openSecretEditor(field: SecretEdit) {
        secretDraft = when (field) {
            SecretEdit.BaseUrl -> state.config.baseUrl
            SecretEdit.ApiKey -> state.config.apiKey
        }
        editingSecret = field
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(LifeColors.Background).safeDrawingPadding()) {
        // 横屏就用左右两栏：门槛放到小型手机横屏（480dp 级）也走两栏，只有竖屏/超窄屏才退回单栏。
        val wide = maxWidth >= 480.dp && maxWidth > maxHeight * 1.2f
        val compact = wide && maxHeight < 560.dp
        val gap = if (compact) 8.dp else 14.dp
        val screenPadding = if (compact) 10.dp else 24.dp
        // 左栏只放「当前大脑 + 思考模式」，按设计稿 0.34 / 0.66；矮窗口用 0.35 / 0.65。
        // 不给小型横屏再加宽左栏：右栏的 API Base URL 是功能性信息（别处看不到），模型名在下方模型卡里还有完整显示。
        val leftWeight = if (compact) .35f else .34f

        Column(
            Modifier.fillMaxSize().padding(horizontal = screenPadding, vertical = if (compact) 4.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (wide) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(Modifier.weight(leftWeight).fillMaxHeight()) {
                        AiBrainTitleRow(compact, onBack)
                        Spacer(Modifier.height(gap))
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            CurrentBrainCard(state, compact)
                        }
                    }
                    Column(
                        Modifier.weight(1f - leftWeight).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        ProviderSection(state, compact, onProviderSelected)
                        SecretConfigSection(
                            state = state,
                            compact = compact,
                            onEditBaseUrl = { openSecretEditor(SecretEdit.BaseUrl) },
                            onEditApiKey = { openSecretEditor(SecretEdit.ApiKey) },
                            onSave = onSaveSecret,
                        )
                        ModelSection(state, compact, onModelSelected, onFetchModels) { customModelDialogVisible = true }
                        ThinkingModeCard(state.thinkingMode, compact, onThinkingModeSelected)
                        CapabilitySection(state, compact, onCapabilityToggled)
                    }
                }
            } else {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    AiBrainTitleRow(false, onBack)
                    Spacer(Modifier.height(gap))
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        CurrentBrainCard(state, compact = false)
                        ThinkingModeCard(state.thinkingMode, compact = false, onThinkingModeSelected)
                        ProviderSection(state, compact = false, onProviderSelected)
                        SecretConfigSection(
                            state = state,
                            compact = false,
                            onEditBaseUrl = { openSecretEditor(SecretEdit.BaseUrl) },
                            onEditApiKey = { openSecretEditor(SecretEdit.ApiKey) },
                            onSave = onSaveSecret,
                            stacked = true,
                        )
                        ModelSection(state, compact = false, onModelSelected, onFetchModels) { customModelDialogVisible = true }
                        CapabilitySection(state, compact = false, onCapabilityToggled)
                    }
                }
            }
        }

        when (editingSecret) {
            SecretEdit.BaseUrl -> AppInputDialog(
                title = "API Base URL",
                value = secretDraft,
                onValueChange = { secretDraft = it },
                placeholder = "https://api.deepseek.com/v1",
                keyboardType = KeyboardType.Uri,
                onDismiss = { editingSecret = null },
                onConfirm = {
                    onBaseUrlChanged(secretDraft.trim())
                    editingSecret = null
                },
            )

            SecretEdit.ApiKey -> AppInputDialog(
                title = "API Key",
                value = secretDraft,
                onValueChange = { secretDraft = it },
                placeholder = "sk-xxxxxxxxxxxxxxxx",
                secret = true,
                onDismiss = { editingSecret = null },
                onConfirm = {
                    onApiKeyChanged(secretDraft.trim())
                    editingSecret = null
                },
            )

            null -> Unit
        }

        if (state.apiKeyPromptVisible) {
            AppDialog(
                title = "还没有填 API Key",
                symbol = "✦",
                description = "获取模型列表需要先连上供应商的接口，填一个 API Key 就能拉取了。",
                confirmText = "去填写",
                dismissText = "稍后再说",
                onConfirm = {
                    onDismissApiKeyPrompt()
                    openSecretEditor(SecretEdit.ApiKey)
                },
                onDismiss = onDismissApiKeyPrompt,
            )
        }

        if (customModelDialogVisible) {
            CustomModelDialog(
                onDismiss = { customModelDialogVisible = false },
                onConfirm = { name ->
                    customModelDialogVisible = false
                    onCustomModelAdded(name)
                },
            )
        }

        // 轻提示：保存校验这类一次性反馈走它，展示完由 ViewModel 清掉。
        state.toastMessage?.let { message ->
            AppToast(message)
            LaunchedEffect(message) {
                delay(2400)
                onToastShown()
            }
        }
    }
}

// ------------------------------------------------------------------ 右栏

/** 模型提供商：横向滑动卡片，选中项高亮并带金色圆点。 */
@Composable
private fun ProviderSection(state: AiBrainUiState, compact: Boolean, onSelected: (String) -> Unit) {
    BrainCard(compact) {
        SectionHeader("◇", "模型提供商", "选择 AI 模型的提供商（左右滑动查看更多）", compact)
        Carousel(
            count = state.providers.size,
            compact = compact,
            stepWidth = if (compact) 132.dp else 180.dp,
        ) { listState ->
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                items(state.providers, key = { provider -> provider.id }) { provider ->
                    val selected = provider.id == state.config.providerId
                    SelectionTile(
                        selected = selected,
                        modifier = Modifier.width(if (compact) 124.dp else 168.dp).height(if (compact) 52.dp else 66.dp),
                        compact = compact,
                        onClick = { onSelected(provider.id) },
                    ) {
                        Row(
                            Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ProviderMark(provider.id, if (compact) 26.dp else 34.dp)
                            Text(
                                provider.name,
                                Modifier.weight(1f, fill = false),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = if (compact) 11.sp else 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selected) {
                                Box(Modifier.size(if (compact) 5.dp else 7.dp).clip(CircleShape).background(LifeColors.Amber))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 密钥配置：页面只做展示，点任意一行弹输入弹窗改值，走 OpenAI Compatible 格式。
 */
@Composable
private fun SecretConfigSection(
    state: AiBrainUiState,
    compact: Boolean,
    onEditBaseUrl: () -> Unit,
    onEditApiKey: () -> Unit,
    onSave: () -> Unit,
    stacked: Boolean = compact,
) {
    BrainCard(compact) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("⚙", color = LifeColors.Amber, fontSize = if (compact) 16.sp else 21.sp, maxLines = 1)
            Text("密钥配置", color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (compact) 15.sp else 19.sp, maxLines = 1)
            Text(
                "使用 OpenAI Compatible 格式配置，兼容大多数模型服务",
                Modifier.weight(1f),
                color = LifeColors.Secondary,
                fontSize = if (compact) 9.sp else 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SecretSaveButton(
                saved = state.secretSaved,
                checking = state.secretChecking,
                compact = compact,
                onClick = onSave,
            )
        }
        // 窄屏（非宽屏布局、或横屏矮窗口）两行并排只剩一点宽度，地址和密钥都会被截断，改成上下两行。
        if (stacked) {
            ConfigFieldRow("link", "API Base URL", state.config.baseUrl, compact, onEditBaseUrl)
            ConfigFieldRow("key", "API Key", maskSecret(state.config.apiKey), compact, onEditApiKey)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConfigFieldRow("link", "API Base URL", state.config.baseUrl, compact, onEditBaseUrl, Modifier.weight(1f))
                ConfigFieldRow("key", "API Key", maskSecret(state.config.apiKey), compact, onEditApiKey, Modifier.weight(1f))
            }
        }
    }
}

/** 密钥配置右上角的保存按钮：可点时校验并保存，通过后变绿色「已保存」并禁用，直到用户改了配置。 */
@Composable
private fun SecretSaveButton(saved: Boolean, checking: Boolean, compact: Boolean, onClick: () -> Unit) {
    val tint = if (saved) ConnectedGreen else LifeColors.Amber
    OutlinedButton(
        onClick = onClick,
        enabled = !saved && !checking,
        modifier = Modifier.height(if (compact) 30.dp else 38.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = if (saved) .55f else .7f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = tint,
            disabledContentColor = if (saved) ConnectedGreen else LifeColors.Secondary,
        ),
        contentPadding = PaddingValues(horizontal = if (compact) 10.dp else 16.dp),
    ) {
        Text(
            when {
                saved -> "✓ 已保存"
                checking -> "检查中…"
                else -> "保存"
            },
            fontSize = if (compact) 10.sp else 13.sp,
            maxLines = 1,
        )
    }
}

/** API Key 在页面上只露出头尾，避免整串明文一直挂在屏幕上。 */
private fun maskSecret(value: String): String = when {
    value.isEmpty() -> ""
    value.length <= 8 -> "•".repeat(value.length)
    else -> value.take(5) + "•".repeat(6) + value.takeLast(4)
}

/** 模型选择：按供应商动态刷新，最多展示 5 个，标题行右侧提供「获取模型列表」。 */
@Composable
private fun ModelSection(
    state: AiBrainUiState,
    compact: Boolean,
    onSelected: (String) -> Unit,
    onFetchModels: () -> Unit,
    onAddCustomModel: () -> Unit,
) {
    BrainCard(compact) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("▤", color = LifeColors.Amber, fontSize = if (compact) 16.sp else 21.sp, maxLines = 1)
            Text("模型选择", color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (compact) 15.sp else 19.sp, maxLines = 1)
            Text(
                "从供应商实时拉取模型列表（左右滑动查看更多）",
                Modifier.weight(1f),
                color = LifeColors.Secondary,
                fontSize = if (compact) 9.sp else 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedButton(
                onClick = onFetchModels,
                enabled = state.fetchStatus != FetchStatus.Loading,
                modifier = Modifier.height(if (compact) 30.dp else 38.dp),
                border = BorderStroke(1.dp, LifeColors.Amber.copy(alpha = .7f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = LifeColors.Amber,
                    disabledContentColor = LifeColors.Secondary,
                ),
                contentPadding = PaddingValues(horizontal = if (compact) 10.dp else 16.dp),
            ) {
                Text(
                    if (state.fetchStatus == FetchStatus.Loading) "获取中…" else "⟳  获取模型列表",
                    fontSize = if (compact) 10.sp else 13.sp,
                    maxLines = 1,
                )
            }
        }
        if (state.statusMessage.isNotEmpty()) {
            Text(
                state.statusMessage,
                color = if (state.fetchStatus == FetchStatus.Failure) LifeColors.Pink else LifeColors.Secondary,
                fontSize = if (compact) 9.sp else 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val models = state.visibleModels
        Carousel(
            count = models.size + 1,
            compact = compact,
            stepWidth = if (compact) 194.dp else 236.dp,
        ) { listState ->
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                items(models, key = { model -> model.id }) { model ->
                    val selected = model.id == state.config.modelId
                    SelectionTile(
                        selected = selected,
                        // 两行文案的高度就够了：第一行模型名 + 右上角标签，第二行能力标签。
                        // 56/72 是实测值：再矮一点安卓的字形行盒会把第二行胶囊和标题切掉。
                        // 186 而不是 172：小米系统字体比模拟器的 Roboto 宽，172 时「模型名 + 已选择」会把名字截成
                        // 「DeepSeek-V4-…」，放宽一点刚好放得下完整模型名。
                        modifier = Modifier.width(if (compact) 186.dp else 224.dp).height(if (compact) 56.dp else 72.dp),
                        compact = compact,
                        alignStart = true,
                        onClick = { onSelected(model.id) },
                    ) {
                        val radioWidth = if (compact) 10.dp else 13.dp
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.width(radioWidth), contentAlignment = Alignment.Center) {
                                Text(
                                    if (selected) "●" else "○",
                                    color = if (selected) LifeColors.Amber else LifeColors.Secondary,
                                    fontSize = if (compact) 10.sp else 13.sp,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                model.name,
                                Modifier.weight(1f),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = if (compact) 11.sp else 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // 右上角只放一枚标签：选中时是「已选择」，没选中才显示「推荐」。
                            if (selected) {
                                SelectedTag(compact)
                            } else if (model.recommended) {
                                SmallTag("推荐", compact)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
                        ) {
                            Spacer(Modifier.width(radioWidth))
                            ModelCapabilities.of(model.id).forEach { capability -> SmallTag(capability, compact) }
                        }
                    }
                }
                item(key = "add-custom-model") {
                    SelectionTile(
                        selected = false,
                        modifier = Modifier.width(if (compact) 96.dp else 118.dp).height(if (compact) 56.dp else 72.dp),
                        compact = compact,
                        onClick = onAddCustomModel,
                    ) {
                        Text("＋", color = LifeColors.Amber, fontSize = if (compact) 16.sp else 20.sp, maxLines = 1)
                        Text("自定义模型", color = LifeColors.Secondary, fontSize = if (compact) 9.sp else 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** 自定义模型弹窗：填写供应商控制台里的模型 ID。 */
@Composable
private fun CustomModelDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AppInputDialog(
        title = "添加自定义模型",
        value = name,
        onValueChange = { name = it },
        placeholder = "deepseek-chat",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(name.trim()) },
    )
}

/** 模型接入：联网搜索 / 长期记忆 / 工具调用 / 日程与提醒。 */
@Composable
private fun CapabilitySection(state: AiBrainUiState, compact: Boolean, onToggle: (AiCapability) -> Unit) {
    BrainCard(compact) {
        SectionHeader("✚", "模型接入", "为小星扩展更多能力，让它更好地帮助你", compact)
        if (compact) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AiCapability.entries, key = { capability -> capability.name }) { capability ->
                    CapabilityTile(
                        capability = capability,
                        enabled = state.capabilities[capability] ?: false,
                        compact = true,
                        modifier = Modifier.width(186.dp),
                        onToggle = { onToggle(capability) },
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AiCapability.entries.forEach { capability ->
                    CapabilityTile(
                        capability = capability,
                        enabled = state.capabilities[capability] ?: false,
                        compact = false,
                        modifier = Modifier.weight(1f),
                        onToggle = { onToggle(capability) },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 通用组件

/** 横向卡片列表：左右箭头翻页 + 底部圆点，滚动状态由组件自己持有。 */
@Composable
private fun Carousel(
    count: Int,
    compact: Boolean,
    stepWidth: Dp,
    content: @Composable (LazyListState) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { (stepWidth + 12.dp).toPx() }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        CarouselArrow("‹", compact, enabled = listState.firstVisibleItemIndex > 0) {
            scope.launch { listState.animateScrollBy(-stepPx) }
        }
        Box(Modifier.weight(1f)) { content(listState) }
        CarouselArrow("›", compact, enabled = count > 0 && listState.firstVisibleItemIndex < count - 1) {
            scope.launch { listState.animateScrollBy(stepPx) }
        }
    }
}

@Composable
private fun CarouselArrow(symbol: String, compact: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(if (compact) 22.dp else 30.dp),
        shape = CircleShape,
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                symbol,
                color = if (enabled) LifeColors.Secondary else LifeColors.Border,
                fontSize = if (compact) 15.sp else 20.sp,
                maxLines = 1,
            )
        }
    }
}

/** 模块标题行：图标 + 标题 + 灰色说明，同一行展示。 */
@Composable
private fun SectionHeader(symbol: String, title: String, description: String, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(symbol, color = LifeColors.Amber, fontSize = if (compact) 16.sp else 21.sp, maxLines = 1)
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (compact) 15.sp else 19.sp, maxLines = 1)
        Text(
            description,
            Modifier.weight(1f),
            color = LifeColors.Secondary,
            fontSize = if (compact) 9.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 密钥配置的一行：图标 + 标签 + 可编辑值 + 后缀。 */
@Composable
private fun ConfigFieldRow(
    icon: String,
    label: String,
    value: String,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val shape = RoundedCornerShape(if (compact) 9.dp else 12.dp)
    Row(
        modifier.height(if (compact) 38.dp else 50.dp)
            .clip(shape)
            .background(FieldFill)
            .border(1.dp, LifeColors.Border, shape)
            .clickable { onClick() }
            .padding(horizontal = if (compact) 9.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
    ) {
        FieldIcon(icon, if (compact) 14.dp else 18.dp)
        Text(
            label,
            color = LifeColors.Secondary,
            fontSize = if (compact) 9.sp else 12.sp,
            maxLines = 1,
            modifier = Modifier.width(if (compact) 74.dp else 104.dp),
        )
        Text(
            value.ifEmpty { "未填写" },
            Modifier.weight(1f),
            color = if (value.isEmpty()) LifeColors.Secondary.copy(alpha = .6f) else Color.White,
            fontSize = if (compact) 10.sp else 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("✎", color = LifeColors.Amber, fontSize = if (compact) 11.sp else 13.sp, maxLines = 1)
    }
}

/** 配置项的矢量小图标：链接 / 钥匙 / 模型方块。 */
@Composable
private fun FieldIcon(kind: String, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = this.size.minDimension * .13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            "link" -> {
                drawArc(
                    color = LifeColors.Amber,
                    startAngle = 40f,
                    sweepAngle = 250f,
                    useCenter = false,
                    topLeft = Offset(w * .05f, h * .18f),
                    size = Size(w * .5f, h * .42f),
                    style = stroke,
                )
                drawArc(
                    color = LifeColors.Amber,
                    startAngle = 220f,
                    sweepAngle = 250f,
                    useCenter = false,
                    topLeft = Offset(w * .45f, h * .4f),
                    size = Size(w * .5f, h * .42f),
                    style = stroke,
                )
            }

            "key" -> {
                drawCircle(LifeColors.Amber, this.size.minDimension * .2f, Offset(w * .3f, h * .34f), style = stroke)
                drawLine(LifeColors.Amber, Offset(w * .45f, h * .5f), Offset(w * .9f, h * .88f), stroke.width, StrokeCap.Round)
                drawLine(LifeColors.Amber, Offset(w * .66f, h * .72f), Offset(w * .78f, h * .6f), stroke.width, StrokeCap.Round)
            }

            else -> {
                val cube = Path().apply {
                    moveTo(w * .5f, h * .06f)
                    lineTo(w * .94f, h * .3f)
                    lineTo(w * .94f, h * .74f)
                    lineTo(w * .5f, h * .96f)
                    lineTo(w * .06f, h * .74f)
                    lineTo(w * .06f, h * .3f)
                    close()
                }
                drawPath(cube, LifeColors.Amber, style = stroke)
            }
        }
    }
}

@Composable
private fun CapabilityTile(
    capability: AiCapability,
    enabled: Boolean,
    compact: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    Row(
        modifier.height(if (compact) 52.dp else 68.dp)
            .clip(shape)
            .background(CardTop)
            .border(1.dp, LifeColors.Border, shape)
            .clickable { onToggle() }
            .padding(horizontal = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 8.dp),
    ) {
        Box(
            Modifier.size(if (compact) 24.dp else 30.dp).clip(CircleShape).background(LifeColors.Amber.copy(alpha = .1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(capability.symbol, color = LifeColors.Amber, fontSize = if (compact) 11.sp else 13.sp, maxLines = 1)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(capability.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 10.sp else 13.sp, maxLines = 1)
            Text(
                capability.description,
                color = LifeColors.Secondary,
                fontSize = if (compact) 8.sp else 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (compact) {
            CompactSwitch(enabled)
        } else {
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(36.dp, 22.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = LifeColors.Amber,
                    uncheckedThumbColor = LifeColors.Secondary,
                    uncheckedTrackColor = LifeColors.Border,
                ),
            )
        }
    }
}

/**
 * 紧凑横屏用的自绘开关。
 *
 * Material3 的 Switch 会强制最小触控尺寸，在 375dp 高度下会把整行撑开并挤压文字；
 * 卡片本身已经是点击区域，所以这里只画视觉状态。
 */
@Composable
private fun CompactSwitch(checked: Boolean) {
    Box(
        Modifier.size(30.dp, 17.dp)
            .background(if (checked) LifeColors.Amber else LifeColors.Border, RoundedCornerShape(50)),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(2.dp).size(13.dp)
                .background(if (checked) Color.White else LifeColors.Secondary, CircleShape),
        )
    }
}

@Composable
private fun BrainCard(compact: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(if (compact) 16.dp else 22.dp)
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
            .border(1.dp, LifeColors.Border.copy(alpha = .85f), shape)
            .padding(if (compact) 9.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        content = content,
    )
}

@Composable
private fun SelectionTile(
    selected: Boolean,
    modifier: Modifier,
    compact: Boolean,
    onClick: () -> Unit,
    alignStart: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    Box(
        modifier.clip(shape)
            .background(if (selected) SelectedFill else CardTop)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) LifeColors.Amber else LifeColors.Border, shape)
            .clickable { onClick() }
            .padding(if (compact) 7.dp else 11.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
            horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun SmallTag(text: String, compact: Boolean) {
    Text(
        text,
        modifier = Modifier.background(LifeColors.Amber.copy(alpha = .1f), RoundedCornerShape(50))
            .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = if (compact) 1.dp else 2.dp),
        color = LifeColors.Amber,
        fontSize = if (compact) 8.sp else 10.sp,
        // 收紧行高：默认行盒比字高出不少，胶囊会显得又厚又空。
        lineHeight = if (compact) 10.sp else 12.sp,
        maxLines = 1,
    )
}

/** 选中标签：实心暖金 + 深色字，比「推荐」更重，放在模型卡右上角。 */
@Composable
private fun SelectedTag(compact: Boolean) {
    Text(
        "已选择",
        modifier = Modifier.background(LifeColors.Amber, RoundedCornerShape(50))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 1.dp else 2.dp),
        color = Color(0xFF14181F),
        fontWeight = FontWeight.SemiBold,
        fontSize = if (compact) 8.sp else 10.sp,
        lineHeight = if (compact) 10.sp else 12.sp,
        maxLines = 1,
    )
}

@Composable
private fun ConnectionBadge(status: ConnectionStatus, compact: Boolean) {
    val color = when (status) {
        ConnectionStatus.Connected -> ConnectedGreen
        ConnectionStatus.NotConfigured -> LifeColors.Amber
        ConnectionStatus.Invalid -> LifeColors.Pink
    }
    val text = when (status) {
        ConnectionStatus.Connected -> "已连接"
        ConnectionStatus.NotConfigured -> "待配置"
        ConnectionStatus.Invalid -> "配置有误"
    }
    Text(
        "● $text",
        modifier = Modifier.background(color.copy(alpha = .12f), RoundedCornerShape(50))
            .padding(horizontal = if (compact) 7.dp else 10.dp, vertical = 3.dp),
        color = color,
        fontSize = if (compact) 9.sp else 11.sp,
        maxLines = 1,
    )
}

/** 提供商品牌标记：用矢量近似各家 logo，不引入图片资源。 */
@Composable
private fun ProviderMark(providerId: String, size: Dp) {
    val shape = if (providerId == "kimi" || providerId == "minimax") RoundedCornerShape(size * .28f) else CircleShape
    Box(
        Modifier.size(size).clip(shape).background(brandBackground(providerId)),
        contentAlignment = Alignment.Center,
    ) {
        if (providerId == "kimi") {
            Text("K", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * .46f).sp, maxLines = 1)
        } else {
            Canvas(Modifier.size(size * .62f)) {
                val w = this.size.width
                val h = this.size.height
                val minSide = this.size.minDimension
                when (providerId) {
                    "deepseek" -> {
                        val body = Path().apply {
                            moveTo(0f, h * .78f)
                            cubicTo(w * .22f, h * .04f, w * .78f, h * .04f, w, h * .6f)
                            lineTo(w * .58f, h * .6f)
                            cubicTo(w * .34f, h * .6f, w * .16f, h * .92f, 0f, h * .78f)
                            close()
                        }
                        drawPath(body, Color(0xFF4D6BFE))
                    }

                    "zhipu" -> {
                        val r = minSide * .17f
                        drawCircle(Color(0xFF3D6BFF), r, Offset(w * .3f, h * .3f))
                        drawCircle(Color(0xFF3D6BFF), r * .8f, Offset(w * .74f, h * .44f))
                        drawCircle(Color(0xFF3D6BFF), r * .92f, Offset(w * .42f, h * .76f))
                    }

                    "minimax" -> {
                        val barWidth = w * .14f
                        listOf(.5f, 1f, .74f, .52f).forEachIndexed { index, ratio ->
                            drawRoundRect(
                                color = Color(0xFFFF4D7D),
                                topLeft = Offset(x = w * (.06f + index * .23f), y = h * (1f - ratio * .86f)),
                                size = Size(barWidth, h * ratio * .86f),
                                cornerRadius = CornerRadius(barWidth / 2f),
                            )
                        }
                    }

                    else -> {
                        val nodes = listOf(
                            Offset(w * .2f, h * .2f),
                            Offset(w * .8f, h * .2f),
                            Offset(w * .5f, h * .82f),
                        )
                        val strokeWidth = minSide * .09f
                        val outline = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        drawLine(LifeColors.Amber, nodes[0], nodes[1], strokeWidth, StrokeCap.Round)
                        drawLine(LifeColors.Amber, nodes[0], nodes[2], strokeWidth, StrokeCap.Round)
                        drawLine(LifeColors.Amber, nodes[1], nodes[2], strokeWidth, StrokeCap.Round)
                        nodes.forEach { drawCircle(Color(0xFF1B2029), minSide * .13f, it) }
                        nodes.forEach { drawCircle(LifeColors.Amber, minSide * .13f, it, style = outline) }
                    }
                }
            }
        }
    }
}

private fun brandBackground(providerId: String): Color = when (providerId) {
    "deepseek", "zhipu" -> Color(0xFF141A2E)
    "kimi", "minimax" -> Color(0xFF101216)
    else -> Color(0xFF1B2029)
}

/**
 * 标题区：设计稿里没有整条顶栏，标题连同返回箭头属于左栏顶部，且不随卡片滚动。
 */
@Composable
private fun AiBrainTitleRow(compact: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(if (compact) 34.dp else 46.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        Box(
            Modifier.size(if (compact) 26.dp else 34.dp)
                .clip(CircleShape)
                .clickable { onBack() }
                .semantics { contentDescription = "返回生命中心" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(if (compact) 12.dp else 16.dp)) {
                val chevron = Path().apply {
                    moveTo(size.width * .78f, 0f)
                    lineTo(size.width * .22f, size.height * .5f)
                    lineTo(size.width * .78f, size.height)
                }
                drawPath(
                    chevron,
                    Color.White.copy(alpha = .9f),
                    style = Stroke(
                        width = size.minDimension * .17f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
        Text(
            "AI 大脑",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 19.sp else 26.sp,
            maxLines = 1,
        )
    }
}

// ------------------------------------------------------------------ 左栏

/** 当前大脑：展示（不配置）供应商、模型、特点标签，以及小星状态。 */
@Composable
private fun CurrentBrainCard(state: AiBrainUiState, compact: Boolean) {
    val model = state.selectedModel
    val modelName = model.name.ifEmpty { "未选择模型" }
    val modelDetail = model.summary.ifEmpty { model.description }.ifEmpty {
        if (modelName == "未选择模型") {
            "点「获取模型列表」或 ＋ 添加模型"
        } else {
            "来自 ${state.selectedProvider.name} 的可用模型"
        }
    }
    BrainCard(compact) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("◉", color = LifeColors.Amber, fontSize = if (compact) 16.sp else 21.sp, maxLines = 1)
            Text("当前大脑", color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (compact) 15.sp else 19.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            ConnectionBadge(state.connectionStatus, compact)
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 14.dp),
        ) {
            ProviderMark(state.selectedProvider.id, if (compact) 34.dp else 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 5.dp)) {
                Text(
                    "${state.selectedProvider.name} · $modelName",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 11.sp else 21.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modelDetail,
                    color = LifeColors.Secondary,
                    fontSize = if (compact) 10.sp else 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.selectedModel.tags.take(3).forEach { tag -> SmallTag(tag, compact) }
        }

        Row(
            Modifier.fillMaxWidth()
                .height(if (compact) 116.dp else 196.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.radialGradient(listOf(LifeColors.Amber.copy(alpha = .10f), Color.Transparent))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(.44f).rotate(-7f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("和你一起", color = LifeColors.Amber, fontSize = if (compact) 13.sp else 19.sp, fontWeight = FontWeight.Medium)
                Text("思考更大的世界", color = LifeColors.Amber, fontSize = if (compact) 13.sp else 19.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.width(if (compact) 56.dp else 84.dp)
                        .height(2.dp)
                        .background(LifeColors.Amber.copy(alpha = .45f), RoundedCornerShape(50)),
                )
            }
            Box(Modifier.weight(.56f).fillMaxHeight()) {
                FaceStage(FaceExpression.HAPPY, Modifier.fillMaxSize(), diffuseGlow = 1f)
            }
        }
    }
}

/** 思考模式：快速 / 平衡 / 深度。 */
@Composable
private fun ThinkingModeCard(selected: ThinkingMode, compact: Boolean, onSelected: (ThinkingMode) -> Unit) {
    BrainCard(compact) {
        SectionHeader("⌁", "思考模式", "平衡速度、效果与成本，适合大多数场景", compact)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
            ThinkingMode.entries.forEach { mode ->
                SelectionTile(
                    selected = mode == selected,
                    // 只要图标 + 标题，下面的说明去掉后卡片自然矮下来。
                    modifier = Modifier.weight(1f).height(if (compact) 56.dp else 72.dp),
                    compact = compact,
                    onClick = { onSelected(mode) },
                ) {
                    Text(
                        mode.symbol,
                        color = if (mode == selected) LifeColors.Amber else Color.White,
                        fontSize = if (compact) 14.sp else 18.sp,
                        lineHeight = if (compact) 16.sp else 20.sp,
                        maxLines = 1,
                    )
                    Text(mode.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 11.sp else 14.sp, maxLines = 1)
                }
            }
        }
    }
}
