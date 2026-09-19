package com.aifacemate.face.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aifacemate.face.anim.FaceAnimator
import com.aifacemate.face.model.ExpressionLibrary
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.model.FaceParams
import com.aifacemate.face.render.CatFace
import com.aifacemate.face.render.FaceSurface

/**
 * 逐帧推进表情动画，返回当前帧该渲染的参数。
 *
 * [animate] 为 false 时直接返回静态参数，方便截图对比与设计走查。
 */
@Composable
fun rememberAnimatedFace(expression: FaceExpression, animate: Boolean = true): FaceParams {
    val animator = remember { FaceAnimator() }
    var params by remember { mutableStateOf(animator.params) }

    LaunchedEffect(expression, animate) {
        if (!animate) {
            params = ExpressionLibrary.paramsFor(expression)
            return@LaunchedEffect
        }
        while (true) {
            val nowMillis = withFrameNanos { it } / 1_000_000L
            params = animator.update(nowMillis, expression)
        }
    }

    return params
}

/**
 * 一张会呼吸、会眨眼的猫脸。这是所有宿主界面（预览页、真机 Launcher）共用的入口。
 */
@Composable
fun FaceStage(
    expression: FaceExpression,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    surface: FaceSurface = FaceSurface.PRODUCT_MOCKUP,
) {
    val params = rememberAnimatedFace(expression, animate)

    Box(modifier, contentAlignment = Alignment.Center) {
        CatFace(
            params = params,
            modifier = Modifier.fillMaxSize(),
            surface = surface,
        )
    }
}
