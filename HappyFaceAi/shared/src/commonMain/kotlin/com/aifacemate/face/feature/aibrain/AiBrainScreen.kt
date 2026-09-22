package com.aifacemate.face.feature.aibrain

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aifacemate.face.ui.AppDialog
import com.aifacemate.face.ui.AppInputDialog
import com.aifacemate.face.ui.AppToast
import happyfaceai.shared.generated.resources.Res
import happyfaceai.shared.generated.resources.brain_companion
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/** v3 prose takes precedence over the older six-item menus still pictured in the boards. */
enum class BrainNavSection(val title: String, internal val icon: String) {
    Brain("AI 大脑", "brain"), Secret("密钥配置", "key"),
    Capabilities("能力接入", "link"), Thinking("思考模式", "thinking"),
}
private enum class BrainField { BaseUrl, ApiKey, Model }

@Composable
fun AiBrainRoute(onBack: () -> Unit, viewModel: AiBrainViewModel = viewModel()) {
    AiBrainScreen(
        state = viewModel.uiState, onBack = onBack,
        onProviderSelected = viewModel::selectProvider, onModelSelected = viewModel::selectModel,
        onThinkingModeSelected = viewModel::selectThinkingMode, onCapabilityToggled = viewModel::toggleCapability,
        onBaseUrlChanged = viewModel::updateBaseUrl, onApiKeyChanged = viewModel::updateApiKey,
        onFetchModels = viewModel::fetchModels, onCustomModelAdded = viewModel::addCustomModel,
        onDismissApiKeyPrompt = viewModel::dismissApiKeyPrompt, onSaveSecret = viewModel::saveSecret,
        onToastShown = viewModel::consumeToast, onTestConnection = viewModel::testConnection,
        onClearSecret = viewModel::clearSecret,
    )
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
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
    onTestConnection: () -> Unit,
    onClearSecret: () -> Unit,
    initialSection: BrainNavSection = BrainNavSection.Brain,
) {
    var sectionName by rememberSaveable { mutableStateOf(initialSection.name) }
    val section = BrainNavSection.valueOf(sectionName)
    var editing by remember { mutableStateOf<BrainField?>(null) }
    var draft by remember { mutableStateOf("") }
    var clearPrompt by remember { mutableStateOf(false) }
    val busy = state.secretChecking || state.fetchStatus == FetchStatus.Loading
    fun edit(field: BrainField) {
        draft = when (field) {
            BrainField.BaseUrl -> state.config.baseUrl
            BrainField.ApiKey -> state.config.apiKey
            BrainField.Model -> state.config.modelId
        }
        editing = field
    }
    BackHandler(enabled = section != BrainNavSection.Brain && editing == null && !clearPrompt && !state.apiKeyPromptVisible) {
        sectionName = BrainNavSection.Brain.name
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(BrainStyle.Background).safeDrawingPadding()) {
        val layout = brainLayout(maxWidth.value, maxHeight.value)
        Row(Modifier.fillMaxSize()) {
            SideNav(section, layout, { sectionName = it.name }, onBack)
            Box(Modifier.weight(1f).fillMaxHeight().padding(if (layout.compact) 12.dp else 24.dp)) {
                when (section) {
                    BrainNavSection.Brain -> BrainOverview(state, layout, busy, onProviderSelected, onModelSelected,
                        onFetchModels, { edit(BrainField.Model) }, { sectionName = it.name })
                    BrainNavSection.Secret -> SecretPage(state, layout, busy, { edit(it) }, onTestConnection,
                        onSaveSecret, { clearPrompt = true })
                    BrainNavSection.Capabilities -> CapabilitiesPage(state, layout, onCapabilityToggled)
                    BrainNavSection.Thinking -> ThinkingPage(state.thinkingMode, layout, onThinkingModeSelected)
                }
            }
        }
        editing?.let { field ->
            // Reuse the app's keyboard-aware editor; configuration screens themselves are not dialogs.
            AppInputDialog(
                title = when (field) { BrainField.BaseUrl -> "API Base URL"; BrainField.ApiKey -> "API Key"; BrainField.Model -> "Model" },
                value = draft, onValueChange = { draft = it }, onDismiss = { editing = null },
                secret = field == BrainField.ApiKey,
                keyboardType = if (field == BrainField.BaseUrl) KeyboardType.Uri else KeyboardType.Text,
                allowEmpty = field != BrainField.Model,
                placeholder = when (field) { BrainField.BaseUrl -> "https://your-provider/v1"; BrainField.ApiKey -> "输入服务商密钥"; BrainField.Model -> "输入模型 ID" },
                onConfirm = {
                    when (field) {
                        BrainField.BaseUrl -> onBaseUrlChanged(draft.trim())
                        BrainField.ApiKey -> onApiKeyChanged(draft.trim())
                        BrainField.Model -> if (state.visibleModels.any { it.id == draft.trim() }) onModelSelected(draft.trim()) else onCustomModelAdded(draft.trim())
                    }
                    editing = null
                },
            )
        }
        if (clearPrompt) AppDialog(title = "清空当前服务商配置？", symbol = "—",
            description = "移除 ${state.selectedProvider.name} 已保存的地址和密钥，清空当前模型选择。其他服务商不受影响。",
            confirmText = "清空配置", onConfirm = { onClearSecret(); clearPrompt = false }, onDismiss = { clearPrompt = false })
        if (state.apiKeyPromptVisible) AppDialog(title = "请先配置连接信息", symbol = "◇",
            description = "填写服务商的 API Base URL 与 API Key 后，再获取模型列表。",
            confirmText = "配置密钥", onConfirm = { onDismissApiKeyPrompt(); sectionName = BrainNavSection.Secret.name },
            onDismiss = onDismissApiKeyPrompt)
        state.toastMessage?.let { message ->
            LaunchedEffect(message) { delay(2800); onToastShown() }
            AppToast(message)
        }
    }
}

@Composable
private fun SideNav(selected: BrainNavSection, layout: BrainLayout, onSelect: (BrainNavSection) -> Unit, onBack: () -> Unit) {
    val width = if (!layout.labelledRail) 56.dp else if (layout.compact) 132.dp else 172.dp
    Column(Modifier.width(width).fillMaxHeight().background(Color(0xFF0A0D0E)).padding(horizontal = 6.dp, vertical = 12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BrainNavSection.entries.forEach { item ->
                Row(Modifier.fillMaxWidth().heightIn(min = if (layout.compact) 44.dp else 56.dp)
                    .then(if (item == selected) Modifier.brainPanel(true) else Modifier.clip(BrainStyle.Shape))
                    .selectable(item == selected, role = Role.Tab, onClick = { onSelect(item) })
                    .semantics { contentDescription = item.title }.padding(horizontal = if (layout.labelledRail) 12.dp else 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrainIcon(item.icon)
                    if (layout.labelledRail) Text(item.title, color = BrainStyle.Text, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
        if (layout.labelledRail && !layout.compact) Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrainIcon("planet", Modifier.size(30.dp))
            Column { Text("星环智能", color = BrainStyle.Gold, fontSize = 13.sp); Text("AI AGENT", color = BrainStyle.Muted, fontSize = 9.sp, letterSpacing = 2.sp) }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(BrainStyle.Shape).clickable(onClick = onBack)
            .semantics { contentDescription = "返回生命中心" }.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrainIcon("back")
            if (layout.labelledRail) Text("返回", color = BrainStyle.Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String, compact: Boolean, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = BrainStyle.Text, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 15.sp else 23.sp, lineHeight = if (compact) 20.sp else 28.sp)
            if (!compact) Text(subtitle, color = BrainStyle.Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        trailing()
    }
}

@Composable
private fun ConnectionChip(state: AiBrainUiState) {
    val good = state.connectionStatus == ConnectionStatus.Connected
    val label = when { state.secretChecking -> "测试中"; good -> "已验证"; state.connectionStatus == ConnectionStatus.Invalid -> "连接失败"; else -> "未测试" }
    Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(if (good) BrainStyle.Green else BrainStyle.Muted))
        Text(label, color = if (good) BrainStyle.Gold else BrainStyle.Muted, fontSize = 10.sp)
    }
}

@Composable
private fun BrainOverview(state: AiBrainUiState, layout: BrainLayout, busy: Boolean,
                          onProvider: (String) -> Unit, onModel: (String) -> Unit, onFetch: () -> Unit,
                          onCustom: () -> Unit, onSection: (BrainNavSection) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (layout.compact) 10.dp else 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (layout.compact) 10.dp else 12.dp)) {
                PageHeading("当前 AI 大脑", "选择智能核心，让每一次对话都更有温度", layout.compact) { ConnectionChip(state) }
                Row(Modifier.fillMaxWidth().heightIn(min = if (layout.compact) 86.dp else 108.dp).brainPanel()
                    .clickable { expanded = true }.padding(if (layout.compact) 12.dp else 18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(if (layout.compact) 52.dp else 72.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF746345),Color(0xFF332D23)))), contentAlignment = Alignment.Center) {
                        ProviderBrandIcon(state.selectedProvider.id, if (layout.compact) 52.dp else 72.dp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(state.selectedProvider.name, color = BrainStyle.Text, fontSize = if (layout.compact) 17.sp else 23.sp, fontWeight = FontWeight.Medium)
                        Text(state.selectedModel.name.ifBlank { "尚未选择模型" }, color = BrainStyle.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!layout.compact) Text(state.selectedModel.summary.ifBlank { state.selectedModel.description }, color = BrainStyle.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("›", color = BrainStyle.Gold, fontSize = 28.sp)
                }
                if (!layout.compact || layout.artwork) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrainButton("配置密钥", Modifier.weight(1f), onClick = { onSection(BrainNavSection.Secret) })
                    BrainButton("能力接入", Modifier.weight(1f), onClick = { onSection(BrainNavSection.Capabilities) })
                    BrainButton("思考模式", Modifier.weight(1f), onClick = { onSection(BrainNavSection.Thinking) })
                }
            }
            if (layout.artwork) CompanionArt(Modifier.width(if (layout.compact) 160.dp else 245.dp).height(if (layout.compact) 158.dp else 238.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!layout.compact) Text("快速切换", color = BrainStyle.Text, fontSize = 14.sp)
            LazyRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(if (layout.compact) 8.dp else 12.dp), contentPadding = PaddingValues(3.dp)) {
                items(state.providers, key = { it.id }) { provider ->
                    val selected = provider.id == state.config.providerId
                    Column(Modifier.width((if (layout.compact) 59.dp else 70.dp) * fontScale).brainPanel(selected)
                        .selectable(selected, enabled = !busy, role = Role.RadioButton, onClick = { onProvider(provider.id) })
                        .padding(vertical = 8.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        ProviderBrandIcon(provider.id, if (layout.compact) 32.dp else 38.dp)
                        Text(provider.name, color = if (selected) BrainStyle.LightGold else BrainStyle.Text, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).brainPanel().clickable { expanded = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(state.config.modelId.ifBlank { "选择模型" }, Modifier.weight(1f), color = BrainStyle.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("⌄", color = BrainStyle.Gold)
                }
                DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 220.dp).background(BrainStyle.Surface)) {
                    if (state.visibleModels.isEmpty()) DropdownMenuItem(text = { Text("暂无模型，请获取或添加") }, onClick = {}, enabled = false)
                    state.visibleModels.forEach { model ->
                        DropdownMenuItem(text = { Text(model.name, color = if (model.id == state.config.modelId) BrainStyle.Gold else BrainStyle.Text) },
                            onClick = { onModel(model.id); expanded = false }, enabled = !busy)
                    }
                    DropdownMenuItem(text = { Text("＋ 自定义模型", color = BrainStyle.Gold) }, onClick = { expanded = false; onCustom() }, enabled = !busy)
                }
            }
            BrainButton(if (state.fetchStatus == FetchStatus.Loading) "获取中…" else "获取模型", enabled = !busy, onClick = onFetch)
        }
    }
}

@Composable
private fun CompanionArt(modifier: Modifier) {
    Image(painterResource(Res.drawable.brain_companion), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}

@Composable
private fun SecretPage(state: AiBrainUiState, layout: BrainLayout, busy: Boolean, onEdit: (BrainField) -> Unit,
                       onTest: () -> Unit, onSave: () -> Unit, onClear: () -> Unit) {
    var formatHelp by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (layout.compact) 8.dp else 16.dp)) {
        PageHeading("密钥配置", "配置模型服务连接信息，连接更强大的 AI 能力", layout.compact)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).brainPanel().padding(if (layout.compact) 10.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(if (layout.compact) 6.dp else 14.dp)) {
                SettingInputRow("API Base URL", state.config.baseUrl, "link", busy) { onEdit(BrainField.BaseUrl) }
                SettingInputRow("API Key", if (state.config.apiKey.isBlank()) "" else "••••••••••••", "key", busy) { onEdit(BrainField.ApiKey) }
                SettingInputRow("Model", state.config.modelId, "model", busy) { onEdit(BrainField.Model) }
                if (!layout.compact) {
                    BrainButton("OpenAI Compatible 格式说明  ⌄", Modifier.fillMaxWidth(), onClick = { formatHelp = !formatHelp })
                    if (formatHelp) Text("填写兼容服务的基础地址（通常以 /v1 结尾）、该服务的 API Key 和准确的模型 ID。测试请求访问 /models；不验证实际对话能力。非兼容接口请使用兼容网关。", color = BrainStyle.Muted, fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrainButton(if (state.secretChecking) "测试中…" else "测试连接", Modifier.weight(1f), enabled = !busy, onClick = onTest)
                    BrainButton("保存配置", Modifier.weight(1f), primary = true, enabled = !busy, onClick = onSave)
                    BrainButton("清空", Modifier.width(58.dp), enabled = !busy, onClick = onClear)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConnectionChip(state)
                    if (state.secretSaved) Text(" · 已保存", color = BrainStyle.Muted, fontSize = 10.sp)
                }
                if (state.statusMessage.isNotBlank()) Text(state.statusMessage, color = BrainStyle.Muted, fontSize = 11.sp)
            }
            if (layout.artwork) Column(Modifier.width(if (layout.compact) 160.dp else 245.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CompanionArt(Modifier.fillMaxWidth().height(if (layout.compact) 120.dp else 180.dp))
                Column(Modifier.brainPanel().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("连接提示", color = BrainStyle.LightGold, fontSize = 14.sp)
                    Text("1. 从服务商获取 API Key\n2. 使用兼容的接口地址\n3. 测试连接后保存配置", color = BrainStyle.Muted, fontSize = 11.sp, lineHeight = 19.sp)
                    Text("密钥保存在当前设备。仅向你配置的服务地址发送验证请求。", color = BrainStyle.Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingInputRow(label: String, value: String, icon: String, busy: Boolean, onClick: () -> Unit) {
    val labelWidth = 82.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BrainIcon(icon, Modifier.size(20.dp))
        Text(label, Modifier.width(labelWidth), color = BrainStyle.Text, fontSize = 11.sp, maxLines = 1)
        Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF252828))
            .clickable(enabled = !busy, onClick = onClick).semantics { contentDescription = "编辑 $label" }.padding(10.dp), contentAlignment = Alignment.CenterStart) {
            Text(value.ifBlank { "点击填写" }, color = if (value.isBlank()) BrainStyle.Muted else BrainStyle.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CapabilitiesPage(state: AiBrainUiState, layout: BrainLayout, onToggle: (AiCapability) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (layout.compact) 8.dp else 16.dp)) {
        PageHeading("能力接入", "连接更多能力，让 AI 看见世界、服务生活", layout.compact) {
            Text("已开启 ${state.capabilities.values.count { it }} 项", color = BrainStyle.Green, fontSize = 11.sp)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val columns = if (maxWidth >= 460.dp) 2 else 1
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiCapability.entries.chunked(columns).forEach { row ->
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { capability ->
                                val enabled = state.capabilities[capability] ?: false
                                Row(Modifier.weight(1f).fillMaxHeight().heightIn(min = if (layout.compact) 64.dp else 78.dp).brainPanel().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(if (layout.compact) 30.dp else 40.dp).brainPanel(true), contentAlignment = Alignment.Center) { BrainIcon(capability.icon) }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(capability.title, color = BrainStyle.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        if (!layout.compact || columns == 1) Text(capability.description, color = BrainStyle.Muted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Switch(checked = enabled, onCheckedChange = { onToggle(capability) },
                                        modifier = Modifier.semantics { contentDescription = capability.title },
                                        colors = SwitchDefaults.colors(checkedTrackColor = BrainStyle.Green, checkedThumbColor = Color.White,
                                            uncheckedTrackColor = Color(0xFF393A35), uncheckedThumbColor = Color(0xFFE6E4DA)))
                                }
                            }
                            if (row.size < columns) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text("这里保存能力偏好；实际执行需接入对应服务，开启不代表已获得系统权限。", color = BrainStyle.Muted, fontSize = 10.sp)
                }
            }
            if (layout.artwork) Column(Modifier.width(if (layout.compact) 150.dp else 235.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CompanionArt(Modifier.fillMaxWidth().height(if (layout.compact) 170.dp else 270.dp))
                Text("更强的能力\n创造更大的可能", color = BrainStyle.Gold, fontSize = 14.sp, lineHeight = 24.sp)
            }
        }
    }
}

@Composable
private fun ThinkingPage(selected: ThinkingMode, layout: BrainLayout, onSelect: (ThinkingMode) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (layout.compact) 20.dp else 38.dp)) {
        PageHeading("思考模式", "选择 AI 的思考深度，平衡速度与效果。", layout.compact)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(if (layout.compact) 10.dp else 20.dp)) {
            ThinkingMode.entries.forEach { mode ->
                val active = selected == mode
                Box(Modifier.weight(1f).height(if (layout.compact) 176.dp else 260.dp).brainPanel(active)
                    .selectable(active, role = Role.RadioButton, onClick = { onSelect(mode) }).padding(12.dp)) {
                    if (active) Text("✓", Modifier.align(Alignment.TopEnd), color = BrainStyle.LightGold, fontSize = 20.sp)
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        BrainIcon(when(mode) { ThinkingMode.Fast -> "fast"; ThinkingMode.Balanced -> "balanced"; ThinkingMode.Deep -> "deep" }, Modifier.size(if (layout.compact) 40.dp else 60.dp))
                        Text(mode.title, color = BrainStyle.Text, fontSize = if (layout.compact) 18.sp else 26.sp, fontWeight = FontWeight.SemiBold)
                        Text(when(mode) { ThinkingMode.Fast -> "响应更快"; ThinkingMode.Balanced -> "推荐默认"; ThinkingMode.Deep -> "深入推理" },
                            color = if (active) BrainStyle.Gold else BrainStyle.Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
