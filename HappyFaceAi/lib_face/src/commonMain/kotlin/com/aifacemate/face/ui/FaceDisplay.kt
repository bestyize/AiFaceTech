package com.aifacemate.face.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.render.FaceSurface

/**
 * 实机屏幕显示：整块屏只显示一张猫脸。
 *
 * 这是真机上要跑的形态——4.0 英寸 16:9 屏铺满，没有控件、没有标题、没有外壳，
 * 耳朵等硬件灯效由机身负责，不属于屏幕内容。
 */
@Composable
fun FaceDisplay(
    modifier: Modifier = Modifier,
    expression: FaceExpression = FaceExpression.IDLE,
    animate: Boolean = true,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        FaceStage(
            expression = expression,
            modifier = Modifier.fillMaxSize(),
            animate = animate,
            surface = FaceSurface.DEVICE_SCREEN,
        )
    }
}
