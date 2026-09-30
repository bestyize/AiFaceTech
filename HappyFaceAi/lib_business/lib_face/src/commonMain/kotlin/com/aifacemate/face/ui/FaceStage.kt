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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
 * 屏幕扩散光晕的默认强度。
 *
 * 1.0 是设计稿的发光质感，0 只保留贴着笔画的近边发光。产品定档后改这里的默认值，
 * 预览台与实机全屏会一起跟着变，不会再出现“预览关了、全屏还有”的分叉。
 */
const val DEFAULT_DIFFUSE_GLOW: Float = 1f

/**
 * 一张会呼吸、会眨眼的猫脸。这是所有宿主界面（预览页、真机 Launcher）共用的入口。
 *
 * [diffuseGlow] 控制屏幕内最外层的扩散光晕（大面积辉光 + 最宽柔边）。
 * 设成 0 时只保留贴着笔画的近边发光，眼睛和猫嘴的霓虹质感还在，但黑屏不会被铺灰。
 */
@Composable
fun FaceStage(
    expression: FaceExpression,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    surface: FaceSurface = FaceSurface.PRODUCT_MOCKUP,
    diffuseGlow: Float = DEFAULT_DIFFUSE_GLOW,
    listeningActivity: Float = 0f,
) {
    val params = rememberAnimatedFace(expression, animate)
    val activity by animateFloatAsState(
        if (listeningActivity.isFinite()) listeningActivity.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(120),
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        CatFace(
            params = params.copy(scale = params.scale * (1f + .02f * activity)),
            modifier = Modifier.fillMaxSize(),
            surface = surface,
            diffuseGlow = diffuseGlow,
        )
    }
}
