package com.aifacemate.face

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "灵伴科技 · RoboCat 脸部表情系统（Alpha 1）",
        state = rememberWindowState(
            size = DpSize(480.dp, 320.dp),
            position = WindowPosition.Aligned(Alignment.BottomEnd),
        ),
    ) {
        App()
    }
}
