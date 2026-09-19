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
import com.aifacemate.face.ui.DEFAULT_DIFFUSE_GLOW
import com.aifacemate.face.ui.FaceDisplay
import com.aifacemate.face.ui.FacePreviewScreen

/**
 * 实机入口。
 *
 * 默认就是一块只显示表情的屏（4.0 英寸 16:9 铺满）。
 *
 * - 单击屏幕：切到下一个表情（原型阶段用来在真机屏上逐个走查）
 * - 长按屏幕：进入内部预览页，用来切换表情、走查 100 个表情位
 *
 * 表情与两个渲染开关（扩散光晕、耳朵灯）的状态都提升到这里，预览页与实机全屏共用同一份，
 * 所以在调试台里关掉的扩散光晕，回到全屏依然是关的，切来切去也不会丢。
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
        var diffuseGlow by remember { mutableStateOf(DEFAULT_DIFFUSE_GLOW) }
        var earLight by remember { mutableStateOf(true) }

        if (showPreview) {
            FacePreviewScreen(
                modifier = Modifier.fillMaxSize(),
                expression = expression,
                onExpressionChange = { expression = it },
                diffuseGlow = diffuseGlow,
                onDiffuseGlowChange = { diffuseGlow = it },
                earLight = earLight,
                onEarLightChange = { earLight = it },
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
                diffuseGlow = diffuseGlow,
                earLight = earLight,
            )
        }
    }
}
