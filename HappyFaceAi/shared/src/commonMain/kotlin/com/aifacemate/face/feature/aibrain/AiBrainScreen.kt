package com.aifacemate.face.feature.aibrain

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aifacemate.face.feature.lifecenter.LifeColors
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.ui.FaceStage

private val BrainCardTop = Color(0xFF171C25)
private val BrainCardBottom = Color(0xFF11161E)
private val ConnectedGreen = Color(0xFF38E66B)

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
        onModelChanged = viewModel::updateModel,
        onSave = viewModel::saveAndValidate,
    )
}

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
    onModelChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(LifeColors.Background).safeDrawingPadding()) {
        val landscape = maxWidth > maxHeight
        val wide = maxWidth >= 840.dp || (maxWidth >= 700.dp && maxWidth > maxHeight * 1.35f)
        val compact = landscape && maxHeight < 540.dp
        val gap = if (compact) 8.dp else 14.dp
        val horizontalPadding = if (compact) 12.dp else 24.dp

        Column(
            Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = if (compact) 5.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            AiBrainTopBar(compact, onBack)
            if (wide) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(
                        Modifier.weight(if (compact) .34f else .36f).fillMaxHeight().then(if (compact) Modifier else Modifier.verticalScroll(rememberScrollState())),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        CurrentBrainCard(state, compact)
                        ThinkingModeCard(state.thinkingMode, compact, onThinkingModeSelected)
                    }
                    Column(
                        Modifier.weight(if (compact) .66f else .64f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        ProviderSection(state, compact, onProviderSelected)
                        ModelSection(state, compact, onModelSelected)
                        CapabilitySection(state, compact, onCapabilityToggled)
                        SecretConfigSection(
                            state = state,
                            compact = compact,
                            onBaseUrlChanged = onBaseUrlChanged,
                            onApiKeyChanged = onApiKeyChanged,
                            onModelChanged = onModelChanged,
                            onSave = onSave,
                        )
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    CurrentBrainCard(state, compact = false)
                    ThinkingModeCard(state.thinkingMode, compact = false, onThinkingModeSelected)
                    ProviderSection(state, compact = false, onProviderSelected)
                    ModelSection(state, compact = false, onModelSelected)
                    CapabilitySection(state, compact = false, onCapabilityToggled)
                    SecretConfigSection(state, false, onBaseUrlChanged, onApiKeyChanged, onModelChanged, onSave)
                }
            }
        }
    }
}

@Composable
private fun AiBrainTopBar(compact: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(if (compact) 38.dp else 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("AI 大脑", color = Color.White, fontSize = if (compact) 20.sp else 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "为小星选择与配置思考核心",
            Modifier.weight(1f),
            color = LifeColors.Secondary,
            fontSize = if (compact) 11.sp else 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.height(if (compact) 32.dp else 40.dp),
            border = BorderStroke(1.dp, LifeColors.Amber.copy(alpha = .7f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LifeColors.Amber),
            contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 18.dp),
        ) { Text("‹  返回生命中心", fontSize = if (compact) 11.sp else 14.sp) }
    }
}

@Composable
private fun CurrentBrainCard(state: AiBrainUiState, compact: Boolean) {
    BrainCard(Modifier.fillMaxWidth(), compact) {
        SectionTitle("◉", "当前大脑", compact, trailing = { ConnectionBadge(state.connectionStatus, compact) })
        Row(
            Modifier.fillMaxWidth().height(if (compact) 137.dp else 220.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
        ) {
            Box(
                Modifier.weight(.42f).fillMaxHeight()
                    .background(Brush.radialGradient(listOf(LifeColors.Amber.copy(alpha = .15f), Color.Transparent))),
            ) {
                FaceStage(FaceExpression.HAPPY, Modifier.fillMaxSize(), diffuseGlow = 1f)
            }
            Column(Modifier.weight(.58f), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                Text(state.selectedProvider.name, color = LifeColors.Secondary, fontSize = if (compact) 11.sp else 14.sp)
                Text(
                    state.selectedModel.name,
                    color = Color.White,
                    fontSize = if (compact) 14.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.selectedModel.description,
                    color = LifeColors.Secondary,
                    fontSize = if (compact) 10.sp else 13.sp,
                    lineHeight = if (compact) 13.sp else 18.sp,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.selectedModel.tags.forEach { SmallTag(it, compact) }
                }
            }
        }
        Text(
            "和你一起，思考更大的世界 ♡",
            color = LifeColors.Amber,
            fontSize = if (compact) 10.sp else 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun ThinkingModeCard(selected: ThinkingMode, compact: Boolean, onSelected: (ThinkingMode) -> Unit) {
    BrainCard(Modifier.fillMaxWidth(), compact) {
        SectionTitle("⌁", "思考模式", compact, description = "控制回答的深度与思考时间")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
            ThinkingMode.entries.forEach { mode ->
                SelectionTile(
                    selected = mode == selected,
                    modifier = Modifier.weight(1f),
                    compact = compact,
                    onClick = { onSelected(mode) },
                ) {
                    Text(mode.symbol, color = if (mode == selected) LifeColors.Amber else Color.White, fontSize = if (compact) 16.sp else 20.sp)
                    Text(mode.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 11.sp else 14.sp)
                    if (!compact) Text(mode.description, color = LifeColors.Secondary, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun ProviderSection(state: AiBrainUiState, compact: Boolean, onSelected: (String) -> Unit) {
    BrainCard(Modifier.fillMaxWidth(), compact) {
        SectionTitle("◇", "模型提供商", compact, description = "选择 AI 模型的提供商，向右滑动查看更多")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)) {
            items(state.providers, key = { it.id }) { provider ->
                SelectionTile(
                    selected = provider.id == state.config.providerId,
                    modifier = Modifier.width(if (compact) 126.dp else 166.dp).height(if (compact) 54.dp else 82.dp),
                    compact = compact,
                    onClick = { onSelected(provider.id) },
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SymbolBadge(provider.symbol, compact)
                        Column(Modifier.weight(1f)) {
                            Text(provider.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 11.sp else 14.sp, maxLines = 1)
                            Text(provider.subtitle, color = LifeColors.Secondary, fontSize = if (compact) 9.sp else 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSection(state: AiBrainUiState, compact: Boolean, onSelected: (String) -> Unit) {
    BrainCard(Modifier.fillMaxWidth(), compact) {
        SectionTitle("▣", "模型选择", compact, description = "根据供应商动态更新，最多展示 5 个推荐模型")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)) {
            items(state.selectedProvider.models.take(5), key = { it.id }) { model ->
                SelectionTile(
                    selected = model.id == state.config.modelId,
                    modifier = Modifier.width(if (compact) 170.dp else 218.dp).height(if (compact) 64.dp else 96.dp),
                    compact = compact,
                    onClick = { onSelected(model.id) },
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (model.id == state.config.modelId) "●" else "○", color = if (model.id == state.config.modelId) LifeColors.Amber else LifeColors.Secondary)
                        Spacer(Modifier.width(6.dp))
                        Text(model.name, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 11.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (model.recommended) SmallTag("推荐", compact)
                    }
                    Text(
                        model.description,
                        color = LifeColors.Secondary,
                        fontSize = if (compact) 8.sp else 11.sp,
                        lineHeight = if (compact) 10.sp else 15.sp,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilitySection(state: AiBrainUiState, compact: Boolean, onToggle: (AiCapability) -> Unit) {
    BrainCard(Modifier.fillMaxWidth(), compact) {
        SectionTitle("✚", "能力接入", compact, description = "为小星扩展更多能力，让它更好地帮助你")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)) {
            items(AiCapability.entries, key = { it.name }) { capability ->
                CapabilityTile(
                    capability = capability,
                    enabled = state.capabilities[capability] ?: false,
                    compact = compact,
                    modifier = Modifier.width(if (compact) 175.dp else 240.dp),
                    onToggle = { onToggle(capability) },
                )
            }
        }
    }
}

@Composable
private fun SecretConfigSection(
    state: AiBrainUiState,
    compact: Boolean,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    BrainCard(Modifier.fillMaxWidth(), compact) {
        SectionTitle(
            "⚙",
            "密钥配置",
            compact,
            description = "使用 OpenAI Compatible 格式配置，兼容大多数模型服务",
            trailing = {
                if (compact) CompactSaveButton(onSave) else ConnectionBadge(state.connectionStatus, compact)
            },
        )
        if (compact) {
            CompactConfigField("API Base URL", state.config.baseUrl, onBaseUrlChanged, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactConfigField("API Key", state.config.apiKey, onApiKeyChanged, Modifier.weight(1f), secret = true)
                CompactConfigField("Model", state.config.modelId, onModelChanged, Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConfigField("API Base URL", state.config.baseUrl, onBaseUrlChanged, Modifier.weight(1.2f), compact)
                ConfigField("API Key", state.config.apiKey, onApiKeyChanged, Modifier.weight(1f), compact, secret = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ConfigField("Model", state.config.modelId, onModelChanged, Modifier.weight(1f), compact)
                Text(state.statusMessage, Modifier.weight(1f), color = if (state.connectionStatus == ConnectionStatus.Invalid) LifeColors.Pink else LifeColors.Secondary, fontSize = 11.sp)
            }
        }
        if (!compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = LifeColors.Amber, contentColor = LifeColors.Background),
                contentPadding = PaddingValues(horizontal = if (compact) 14.dp else 20.dp),
                modifier = Modifier.height(40.dp),
            ) { Text("保存并校验", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CompactSaveButton(onSave: () -> Unit) {
    Button(
        onClick = onSave,
        modifier = Modifier.height(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LifeColors.Amber, contentColor = LifeColors.Background),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
    ) { Text("保存校验", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

@Composable
private fun CompactConfigField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier,
    secret: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, color = LifeColors.Secondary, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onValueChanged,
            modifier = Modifier.fillMaxWidth().height(38.dp).border(1.dp, LifeColors.Border, RoundedCornerShape(9.dp)).padding(horizontal = 9.dp, vertical = 7.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.sp, lineHeight = 14.sp),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            cursorBrush = SolidColor(LifeColors.Amber),
        )
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier,
    compact: Boolean,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        modifier = modifier,
        label = { Text(label, fontSize = if (compact) 9.sp else 11.sp) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = if (compact) 10.sp else 12.sp),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LifeColors.Amber,
            unfocusedBorderColor = LifeColors.Border,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = LifeColors.Amber,
            unfocusedLabelColor = LifeColors.Secondary,
        ),
    )
}

@Composable
private fun CapabilityTile(capability: AiCapability, enabled: Boolean, compact: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        modifier = modifier.height(if (compact) 52.dp else 68.dp),
        shape = RoundedCornerShape(if (compact) 12.dp else 16.dp),
        color = Color(0xFF171C25),
        border = BorderStroke(1.dp, LifeColors.Border),
    ) {
        Row(Modifier.padding(horizontal = if (compact) 9.dp else 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SymbolBadge(capability.symbol, compact)
            Column(Modifier.weight(1f)) {
                Text(capability.title, color = Color.White, fontSize = if (compact) 10.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(capability.description, color = LifeColors.Secondary, fontSize = if (compact) 8.sp else 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (compact) CompactSwitch(enabled) else Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(42.dp, 26.dp),
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

@Composable
private fun CompactSwitch(checked: Boolean) {
    Box(
        Modifier.size(30.dp, 17.dp)
            .background(if (checked) LifeColors.Amber else LifeColors.Border, RoundedCornerShape(50)),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(2.dp).size(13.dp).background(if (checked) Color.White else LifeColors.Secondary, CircleShape))
    }
}

@Composable
private fun BrainCard(modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(BrainCardTop, BrainCardBottom)))
            .border(1.dp, LifeColors.Border.copy(alpha = .85f), shape)
            .padding(if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(
    symbol: String,
    title: String,
    compact: Boolean,
    description: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(symbol, color = LifeColors.Amber, fontSize = if (compact) 17.sp else 22.sp)
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (compact) 14.sp else 18.sp, maxLines = 1)
        if (description != null) {
            Text(
                description,
                Modifier.weight(1f),
                color = LifeColors.Secondary,
                fontSize = if (compact) 9.sp else 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        trailing?.invoke()
    }
}

@Composable
private fun SelectionTile(
    selected: Boolean,
    modifier: Modifier,
    compact: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 12.dp else 16.dp),
        color = if (selected) Color(0xFF292315) else Color(0xFF171C25),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) LifeColors.Amber else LifeColors.Border),
    ) {
        Column(Modifier.fillMaxSize().padding(if (compact) 7.dp else 11.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun SymbolBadge(symbol: String, compact: Boolean) {
    Box(
        Modifier.size(if (compact) 24.dp else 38.dp).clip(CircleShape).background(LifeColors.Amber.copy(alpha = .1f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = LifeColors.Amber, fontWeight = FontWeight.Bold, fontSize = if (compact) 10.sp else 13.sp)
    }
}

@Composable
private fun SmallTag(text: String, compact: Boolean) {
    Text(
        text,
        modifier = Modifier.background(LifeColors.Amber.copy(alpha = .1f), RoundedCornerShape(50)).padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 2.dp),
        color = LifeColors.Amber,
        fontSize = if (compact) 8.sp else 10.sp,
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
        modifier = Modifier.background(color.copy(alpha = .12f), RoundedCornerShape(50)).padding(horizontal = if (compact) 7.dp else 10.dp, vertical = 4.dp),
        color = color,
        fontSize = if (compact) 9.sp else 11.sp,
        maxLines = 1,
    )
}
