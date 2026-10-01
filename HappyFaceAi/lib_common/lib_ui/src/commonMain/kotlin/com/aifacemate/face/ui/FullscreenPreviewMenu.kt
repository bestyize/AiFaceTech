package com.aifacemate.face.ui

import androidx.compose.foundation.Canvas
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

/** Same full-screen control surface for face and effects previews; no page title bar. */
@Composable
fun FullscreenPreviewMenu(open: Boolean, onOpen: () -> Unit, onCollapse: () -> Unit,
    prefix: String, description: String, footer: (@Composable () -> Unit)? = null,
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
            color = Color(0xF0181F22), contentColor = Color(0xFFF4F3EF)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Reserve horizontal space for the floating close control, never a header row.
                    Column(Modifier.weight(1f).fillMaxWidth().padding(end = 40.dp)
                        .verticalScroll(rememberScrollState()).testTag("$prefix.controls.scroll"),
                        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
                    footer?.invoke()
                }
                Box(Modifier.align(Alignment.TopEnd).size(32.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onCollapse).testTag("$prefix.menu.collapse")
                    .semantics { contentDescription = "收起$description" }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(14.dp)) {
                        drawLine(Color.White.copy(alpha = .8f), Offset(2f, 2f), Offset(size.width - 2f, size.height - 2f), 2f, StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = .8f), Offset(size.width - 2f, 2f), Offset(2f, size.height - 2f), 2f, StrokeCap.Round)
                    }
                }
            }
        }
    }
}
