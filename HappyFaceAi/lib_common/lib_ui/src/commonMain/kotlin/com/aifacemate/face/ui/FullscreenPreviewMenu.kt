package com.aifacemate.face.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Same full-screen control surface for face and effects previews; no page title bar. */
@Composable
fun FullscreenPreviewMenu(open: Boolean, onOpen: () -> Unit, onCollapse: () -> Unit,
    prefix: String, description: String, onBack: (() -> Unit)? = null, backTag: String = "$prefix.back",
    contentMaxWidth: Dp = 880.dp,
    content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (!open) IconButton(onClick = onOpen,
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp).size(48.dp)
                .testTag("$prefix.menu").semantics { contentDescription = "打开$description" }) {
            Canvas(Modifier.size(22.dp)) {
                repeat(3) { i ->
                    drawLine(Color.White.copy(alpha = .65f), Offset(2f, size.height * (.23f + i * .27f)),
                        Offset(size.width - 2f, size.height * (.23f + i * .27f)), 2f, StrokeCap.Round)
                }
            }
        }
        if (open) Surface(Modifier.fillMaxSize().testTag("$prefix.menu.panel"),
            color = Color(0xFA12191E), contentColor = Color(0xFFF4F3EF)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("$prefix.controls.scroll"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = contentMaxWidth).fillMaxWidth().testTag("$prefix.controls.content"),
                        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
                }
                Box(Modifier.align(Alignment.TopEnd).size(32.dp).clip(CircleShape)
                    .background(Color(0xEB29343C))
                    .clickable(role = Role.Button, onClick = onCollapse).testTag("$prefix.menu.collapse")
                    .semantics { contentDescription = "收起$description" }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(14.dp)) {
                        drawLine(Color.White.copy(alpha = .8f), Offset(2f, 2f), Offset(size.width - 2f, size.height - 2f), 2f, StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = .8f), Offset(size.width - 2f, 2f), Offset(2f, size.height - 2f), 2f, StrokeCap.Round)
                    }
                }
            }
        }
        if (onBack != null) Box(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(12.dp)
            .size(32.dp).clip(CircleShape).background(Color(0xEB29343C))
            .clickable(role = Role.Button, onClick = onBack).testTag(backTag)
            .semantics { contentDescription = "返回上一页" }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(14.dp)) {
                val color = Color.White.copy(alpha = .8f)
                drawLine(color, Offset(size.width - 2f, size.height / 2), Offset(2f, size.height / 2), 2f, StrokeCap.Round)
                drawLine(color, Offset(2f, size.height / 2), Offset(size.width * .45f, 2f), 2f, StrokeCap.Round)
                drawLine(color, Offset(2f, size.height / 2), Offset(size.width * .45f, size.height - 2f), 2f, StrokeCap.Round)
            }
        }
    }
}

/** Inline section at the start of the option list, independent of floating navigation. */
@Composable
fun PreviewSettingsSection(title: String, expanded: Boolean, onToggle: () -> Unit, prefix: String,
    content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(Color(0xFF202A31)).clickable(role = Role.Button, onClick = onToggle)
            .testTag("$prefix.settings.toggle").semantics {
                contentDescription = "$title，${if (expanded) "收起" else "展开"}"
            }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color(0xFFF4F3EF), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(12.dp))
            Text(if (expanded) "收起 ▴" else "展开 ▾", color = Color(0xFFB6BDC1), style = MaterialTheme.typography.labelSmall)
        }
        if (expanded) Column(Modifier.fillMaxWidth().testTag("$prefix.settings.section"),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
