package com.aifacemate.face.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.feature.lifecenter.LifeColors
import kotlinx.coroutines.delay

/**
 * 应用统一弹窗。
 *
 * 和生命中心 / AI 大脑同一套视觉：深色渐变卡片、暖金描边与强调色、大圆角、柔和阴影。
 * 点击遮罩、按系统返回键、点次要按钮都走 [onDismiss]。
 * 横屏时键盘会顶起来，所以外层用 [imePadding] 把卡片抬到键盘之上。
 */
// BackHandler 在 CMP 1.12 标记为弃用（建议改用 NavigationEventHandler），
// 但 NavigationEventHandler 需要额外引入 navigation-event 依赖；这里只用「返回键关弹窗」这一件事，
// 先沿用 ui-backhandler 里的实现。
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    symbol: String = "◈",
    description: String? = null,
    confirmText: String = "确定",
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissText: String = "取消",
    content: @Composable ColumnScope.() -> Unit = {},
) {
    BackHandler(enabled = true, onBack = onDismiss)

    val shape = RoundedCornerShape(22.dp)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(AppDialogColors.Scrim)
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 横屏下键盘会吃掉大半个屏：可用高度只剩 200dp 上下，这里分两档收紧，
        // 并且只有正文滚动，标题和按钮始终贴在卡片两端。
        val cardMaxHeight = maxHeight - 20.dp
        val cramped = cardMaxHeight < 220.dp
        val tight = cardMaxHeight < 320.dp
        val gap = when {
            cramped -> 8.dp
            tight -> 10.dp
            else -> 14.dp
        }
        Column(
            Modifier.padding(horizontal = if (tight) 12.dp else 20.dp, vertical = if (cramped) 4.dp else if (tight) 6.dp else 12.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .heightIn(max = cardMaxHeight)
                .shadow(26.dp, shape, clip = false)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(AppDialogColors.CardTop, AppDialogColors.CardBottom)))
                .border(1.dp, LifeColors.Amber.copy(alpha = .26f), shape)
                // 吸收卡片上的点击，避免误触遮罩把弹窗关掉
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = if (tight) 18.dp else 22.dp, vertical = if (cramped) 12.dp else if (tight) 14.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(symbol, color = LifeColors.Amber, fontSize = 17.sp, maxLines = 1)
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 正文可滚动：空间不够时先让说明文字让位，标题和按钮不动。
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                if (description != null) {
                    Text(description, color = LifeColors.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                }
                content()
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onConfirm != null) AppDialogGhostButton(dismissText, onDismiss)
                AppDialogPrimaryButton(
                    text = if (onConfirm != null) confirmText else dismissText,
                    enabled = confirmEnabled,
                    onClick = onConfirm ?: onDismiss,
                )
            }
        }
    }
}

/**
 * 输入弹窗：AI 大脑页所有可编辑项都用它。
 *
 * 字段本身只做展示，点任意一行进这里编辑，键盘上的「完成」等同确认按钮。
 */
@Composable
fun AppInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    description: String? = null,
    label: String? = null,
    placeholder: String = "",
    hint: String? = null,
    symbol: String = "✎",
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    confirmText: String = "保存",
    allowEmpty: Boolean = false,
) {
    var revealed by remember { mutableStateOf(false) }
    AppDialog(
        title = title,
        onDismiss = onDismiss,
        symbol = symbol,
        description = description,
        confirmText = confirmText,
        onConfirm = onConfirm,
        confirmEnabled = allowEmpty || value.isNotBlank(),
    ) {
        AppDialogTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            secret = secret && !revealed,
            onToggleReveal = if (secret) ({ revealed = !revealed }) else null,
            keyboardType = keyboardType,
            onImeAction = { if (allowEmpty || value.isNotBlank()) onConfirm() },
        )
        if (hint != null) {
            Text(hint, color = LifeColors.Secondary, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

/** 弹窗里的输入框：深色填充 + 聚焦时暖金描边 + 可选的密码可见性切换。 */
@Composable
fun AppDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String = "",
    secret: Boolean = false,
    onToggleReveal: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    autoFocus: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(autoFocus) {
        if (!autoFocus) return@LaunchedEffect
        // 弹窗刚组合出来时窗口可能还没拿到输入焦点，系统会直接忽略弹键盘的请求。
        // 所以先轮询等窗口聚焦，聚焦后再请求焦点并显式弹一次键盘。
        var shown = false
        repeat(6) { attempt ->
            if (!shown && windowInfo.isWindowFocused) {
                runCatching { focusRequester.requestFocus() }
                keyboard?.show()
                shown = true
            }
            if (!shown) delay(if (attempt == 0) 60L else 170L)
        }
        if (!shown) {
            // 兜底：窗口焦点迟迟不上来（冷启动常见）时，至少把焦点交给输入框。
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    val shape = RoundedCornerShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (label != null) {
            Text(label, color = LifeColors.Secondary, fontSize = 11.sp, maxLines = 1)
        }
        Row(
            Modifier.fillMaxWidth()
                .height(46.dp)
                .clip(shape)
                .background(AppDialogColors.FieldFill)
                .border(1.dp, if (focused) LifeColors.Amber.copy(alpha = .8f) else LifeColors.Border, shape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = LifeColors.Secondary.copy(alpha = .65f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(LifeColors.Amber),
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(
                        onDone = { onImeAction() },
                        onGo = { onImeAction() },
                        onSend = { onImeAction() },
                    ),
                )
            }
            if (onToggleReveal != null) {
                Box(
                    Modifier.size(22.dp).clickable { onToggleReveal() },
                    contentAlignment = Alignment.Center,
                ) { RevealEyeIcon(hidden = secret, size = 16.dp) }
            }
        }
    }
}

/** 密码可见性图标：闭眼 / 睁眼。 */
@Composable
private fun RevealEyeIcon(hidden: Boolean, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = this.size.minDimension * .12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (hidden) {
            drawArc(
                color = LifeColors.Secondary,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * .05f, h * .2f),
                size = Size(w * .9f, h * .6f),
                style = stroke,
            )
            drawLine(
                LifeColors.Secondary,
                Offset(w * .15f, h * .9f),
                Offset(w * .85f, h * .1f),
                stroke.width,
                StrokeCap.Round,
            )
        } else {
            drawArc(
                color = LifeColors.Amber,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * .05f, h * .2f),
                size = Size(w * .9f, h * .6f),
                style = stroke,
            )
            drawArc(
                color = LifeColors.Amber,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * .05f, h * .2f),
                size = Size(w * .9f, h * .6f),
                style = stroke,
            )
            drawCircle(LifeColors.Amber, this.size.minDimension * .16f, Offset(w * .5f, h * .5f))
        }
    }
}

/** 主按钮：暖金实心胶囊，和生命中心的「进入陪伴」一致。 */
@Composable
private fun AppDialogPrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        Modifier.clip(shape)
            .background(if (enabled) LifeColors.Amber else LifeColors.Amber.copy(alpha = .22f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        color = if (enabled) Color(0xFF14181F) else LifeColors.Amber.copy(alpha = .55f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        maxLines = 1,
    )
}

/** 次要按钮：细边框胶囊。 */
@Composable
private fun AppDialogGhostButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        Modifier.clip(shape)
            .border(1.dp, LifeColors.Border, shape)
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 10.dp),
        color = LifeColors.Secondary,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        maxLines = 1,
    )
}

/** 弹窗专用配色，比页面卡片更亮一档，保证浮起来。 */
private object AppDialogColors {
    val Scrim = Color(0xB305070A)
    val CardTop = Color(0xFF1C2231)
    val CardBottom = Color(0xFF121722)
    val FieldFill = Color(0xFF0F131B)
}
