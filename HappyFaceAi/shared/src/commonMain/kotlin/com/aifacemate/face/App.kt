package com.aifacemate.face

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.model.next
import com.aifacemate.face.ui.FaceDisplay
import com.aifacemate.face.ui.FacePreviewScreen

/**
 * 实机入口。
 *
 * 默认就是一块只显示表情的屏（4.0 英寸 16:9 铺满）。
 *
 * - 单击屏幕：切到下一个表情（原型阶段用来在真机屏上逐个走查）
 * - 长按屏幕：进入内部预览页，用来切换表情、走查 25 个表情位
 *
 * 表情状态提升到这里，预览页和实机全屏共用同一份，切来切去不会丢。
 */
@Composable
@Preview
fun App() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFFC46B),
            surface = Color(0xFF121218),
            background = Color(0xFF09090C),
        ),
    ) {
        var showPreview by remember { mutableStateOf(false) }
        var expression by remember { mutableStateOf(FaceExpression.IDLE) }

        if (showPreview) {
            FacePreviewScreen(
                modifier = Modifier.fillMaxSize(),
                expression = expression,
                onExpressionChange = { expression = it },
                onExitToDisplay = { showPreview = false },
            )
        } else {
            FaceDisplay(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { expression = expression.next() },
                            onLongPress = { showPreview = true },
                        )
                    },
                expression = expression,
            )
        }
    }
}
