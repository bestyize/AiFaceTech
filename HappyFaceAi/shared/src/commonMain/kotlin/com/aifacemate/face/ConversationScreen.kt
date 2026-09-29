package com.aifacemate.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.chat.ChatMessage

private val ChatGold = Color(0xFFF1C779)
private val ChatInk = Color(0xFFF4F3EF)
private val ChatMuted = Color(0xFFA7AAA8)
private val ChatBackground = Color(0xFF0C1011)
private val ChatSurface = Color(0xFF191C1C)
private val ChatBorder = Color(0xFF48443A)

data class ConversationScreenState(
    val mode: VoiceMode,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val status: String = "可以开始对话",
    val busy: Boolean = false,
    val voiceActive: Boolean = false,
    val voiceEnabled: Boolean = true,
)

/** Presentation only: previews cannot open a microphone, contact a service or load stored keys. */
@Composable
fun ConversationScreenContent(
    state: ConversationScreenState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null,
) {
    val colors = darkColorScheme(primary = ChatGold, onPrimary = Color(0xFF221D13),
        background = ChatBackground, surface = ChatSurface, onSurface = ChatInk,
        onSurfaceVariant = ChatMuted, outline = ChatBorder)
    MaterialTheme(colorScheme = colors) {
        BoxWithConstraints(modifier.fillMaxSize().background(ChatBackground).safeDrawingPadding().imePadding(),
            contentAlignment = Alignment.TopCenter) {
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val short = maxHeight < 400.dp
            val veryShort = maxHeight < 200.dp
            val narrow = maxWidth.value / fontScale < 600f
            val small = short || narrow
            val inset = if (small) 12.dp else 24.dp
            val listState = rememberLazyListState()
            var previousCount by remember { mutableIntStateOf(0) }
            var previousStatus by remember { mutableStateOf(state.status) }
            LaunchedEffect(state.messages.size, state.status) {
                val visibleEnd = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val follow = previousCount == 0 || visibleEnd >= previousCount - 1
                if (follow && (state.messages.isNotEmpty() || state.status != previousStatus)) {
                    // The final item is the status; keep full errors reachable without shrinking history.
                    listState.scrollToItem(if (state.messages.isEmpty()) 1 else state.messages.size)
                }
                previousCount = state.messages.size
                previousStatus = state.status
            }
            Column(Modifier.widthIn(max = 1080.dp).fillMaxSize().padding(horizontal = inset),
                verticalArrangement = Arrangement.spacedBy(if (veryShort) 2.dp else if (small) 8.dp else 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = if (veryShort) 0.dp else 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onClose, modifier = Modifier.testTag("chat.close").heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(12.dp)) {
                        ConversationGlyph(Glyph.Back, ChatGold, Modifier.size(18.dp))
                        if (!narrow) { Spacer(Modifier.width(4.dp)); Text("返回", fontSize = 13.sp) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (narrow) "小星" else "和小星聊聊", color = ChatInk,
                            fontSize = if (small) 18.sp else 24.sp, fontWeight = FontWeight.SemiBold)
                        if (!veryShort) Text(when (state.mode) {
                            VoiceMode.Realtime -> "实时对话"
                            VoiceMode.Traditional -> "分段语音"
                        }, color = ChatMuted, fontSize = 12.sp)
                    }
                    if (onCollapse != null) {
                        OutlinedButton(onClick = onCollapse, modifier = Modifier.testTag("chat.collapse").heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(12.dp)) {
                            Text("收起", fontSize = 13.sp)
                        }
                    }
                }

                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().testTag("chat.history"), state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (small) 10.dp else 16.dp),
                ) {
                    if (state.messages.isEmpty()) item(key = "empty") {
                        Column(Modifier.fillMaxWidth().heightIn(min = if (short) 64.dp else 160.dp)
                            .padding(horizontal = 12.dp, vertical = if (short) 8.dp else 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            if (!short) {
                                Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF393329)),
                                    contentAlignment = Alignment.Center) {
                                    ConversationGlyph(Glyph.Mic, ChatGold, Modifier.size(26.dp))
                                }
                                Spacer(Modifier.height(14.dp))
                            }
                            Text("想聊些什么？", color = ChatInk, fontSize = if (small) 17.sp else 22.sp,
                                fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text("轻点麦克风开始说话，也可以输入文字。", color = ChatMuted, fontSize = 12.sp)
                        }
                    }
                    itemsIndexed(state.messages, key = { index, _ -> index }) { index, message ->
                        val fromUser = message.role == "user"
                        Box(Modifier.fillMaxWidth(), contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                            Column(Modifier.fillMaxWidth(if (small) .94f else .82f),
                                horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (fromUser) "我" else "小星", color = ChatMuted, fontSize = 11.sp)
                                val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp,
                                    bottomStart = if (fromUser) 16.dp else 4.dp,
                                    bottomEnd = if (fromUser) 4.dp else 16.dp)
                                SelectionContainer {
                                    Text(message.content,
                                        modifier = Modifier.testTag("chat.message.$index").clip(shape)
                                            .background(if (fromUser) Color(0xFF393329) else ChatSurface)
                                            .border(1.dp, if (fromUser) ChatGold.copy(alpha = .4f) else ChatBorder.copy(alpha = .6f), shape)
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        color = ChatInk, fontSize = if (small) 14.sp else 16.sp,
                                        lineHeight = if (small) 21.sp else 24.sp)
                                }
                            }
                        }
                    }
                    item(key = "status") {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF20221E))
                            .testTag("chat.status").padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(6.dp).background(ChatGold, RoundedCornerShape(3.dp)))
                            Text(state.status, color = ChatGold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }

                val processing = state.busy && !state.voiceActive && state.mode != VoiceMode.Realtime
                val voiceLabel = when {
                    state.voiceActive -> if (state.mode == VoiceMode.Realtime) "停止实时对话" else "结束说话"
                    processing -> "正在处理"
                    state.mode == VoiceMode.Realtime -> "开始实时对话"
                    else -> "开始说话"
                }
                Row(Modifier.fillMaxWidth().padding(bottom = if (veryShort) 0.dp else 8.dp).clip(RoundedCornerShape(18.dp))
                    .background(ChatSurface).border(1.dp, ChatBorder, RoundedCornerShape(18.dp))
                    .padding(if (veryShort) 0.dp else if (small) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = state.draft, onValueChange = onDraftChange,
                        placeholder = { Text("输入文字", fontSize = 14.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("chat.draft"),
                        shape = RoundedCornerShape(12.dp), singleLine = true)
                    FilledTonalIconButton(onClick = onSend, enabled = state.draft.isNotBlank() && !state.busy,
                        modifier = Modifier.size(48.dp).testTag("chat.send").semantics { contentDescription = "发送文字" },
                        shape = RoundedCornerShape(12.dp)) {
                        ConversationGlyph(Glyph.Send, if (state.draft.isNotBlank() && !state.busy) ChatGold else ChatMuted,
                            Modifier.size(20.dp))
                    }
                    Button(onClick = onVoice, enabled = state.voiceEnabled,
                        modifier = Modifier.heightIn(min = 56.dp).testTag("chat.voice").semantics {
                            contentDescription = voiceLabel
                            stateDescription = state.status
                        }, shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = if (small) 10.dp else 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (state.voiceActive) Color(0xFFE6A571) else ChatGold)) {
                        ConversationGlyph(if (state.voiceActive) Glyph.Stop else Glyph.Mic,
                            if (state.voiceEnabled) Color(0xFF221D13) else ChatMuted, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (small) when {
                            state.voiceActive -> "结束"
                            processing -> "处理"
                            else -> "说话"
                        } else voiceLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private enum class Glyph { Back, Send, Mic, Stop }

@Composable
private fun ConversationGlyph(glyph: Glyph, ink: Color, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 1.7.dp.toPx()
        when (glyph) {
            Glyph.Back -> {
                drawLine(ink, Offset(size.width * .7f, size.height * .15f), Offset(size.width * .3f, size.height * .5f), stroke)
                drawLine(ink, Offset(size.width * .3f, size.height * .5f), Offset(size.width * .7f, size.height * .85f), stroke)
            }
            Glyph.Send -> {
                val a = Offset(size.width * .12f, size.height * .14f)
                val b = Offset(size.width * .9f, size.height * .5f)
                val c = Offset(size.width * .12f, size.height * .86f)
                val d = Offset(size.width * .3f, size.height * .5f)
                drawLine(ink, a, b, stroke); drawLine(ink, b, c, stroke)
                drawLine(ink, a, d, stroke); drawLine(ink, d, c, stroke); drawLine(ink, d, b, stroke)
            }
            Glyph.Mic -> {
                drawRoundRect(ink, Offset(size.width * .34f, 0f), Size(size.width * .32f, size.height * .58f),
                    androidx.compose.ui.geometry.CornerRadius(size.width * .16f))
                drawArc(ink, 0f, 180f, false, Offset(size.width * .14f, size.height * .23f),
                    Size(size.width * .72f, size.height * .5f), style = Stroke(stroke))
                drawLine(ink, Offset(size.width * .5f, size.height * .74f), Offset(size.width * .5f, size.height), stroke)
            }
            Glyph.Stop -> drawRoundRect(ink, Offset(size.width * .18f, size.height * .18f),
                Size(size.width * .64f, size.height * .64f), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        }
    }
}
