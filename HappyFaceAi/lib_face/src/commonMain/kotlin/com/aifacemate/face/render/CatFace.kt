package com.aifacemate.face.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.aifacemate.face.model.EyeParams
import com.aifacemate.face.model.EyeShape
import com.aifacemate.face.model.FaceMark
import com.aifacemate.face.model.FaceParams
import com.aifacemate.face.model.MarkParams
import com.aifacemate.face.model.MarkTint
import com.aifacemate.face.model.MouthParams
import com.aifacemate.face.model.MouthShape
import kotlin.math.PI

/**
 * 猫咪机器人的脸。
 *
 * 渲染器只认识 [FaceParams]，不认识表情编号，所以新增表情不需要改这里。
 * 所有图形都用矢量绘制，同一套参数可以渲染到任意尺寸的屏幕上。
 *
 * [surface] 决定构图：
 * - [FaceSurface.DEVICE_SCREEN]：实机屏幕内容，铺满整块屏，不画耳朵和外壳；
 * - [FaceSurface.PRODUCT_MOCKUP]：整机概念图，带发光耳朵和机身外壳。
 */
@Composable
fun CatFace(
    params: FaceParams,
    modifier: Modifier = Modifier,
    surface: FaceSurface = FaceSurface.PRODUCT_MOCKUP,
) {
    Canvas(modifier) {
        val metrics = computeFaceMetrics(size.width, size.height, surface)
        if (metrics.panelWidth <= 0f || metrics.panelHeight <= 0f) return@Canvas

        val pivot = Offset(metrics.panelCenterX, metrics.panelCenterY)
        val faceTransform: DrawTransform.() -> Unit = {
            rotate(degrees = params.tiltDegrees, pivot = pivot)
            scale(scaleX = params.scale, scaleY = params.scale, pivot = pivot)
            translate(left = 0f, top = params.offsetY * metrics.panelHeight)
        }

        if (surface == FaceSurface.PRODUCT_MOCKUP) {
            // 概念图：整机一起倾斜，包括外壳与屏幕
            withTransform(faceTransform) {
                drawEar(metrics, direction = -1f, glow = params.earGlow)
                drawEar(metrics, direction = 1f, glow = params.earGlow)
                drawScreenPanel(metrics, dim = params.panelDim, withHousing = true)
                drawFaceContent(metrics, params)
            }
        } else {
            // 实机屏幕：屏幕本身铺满且不能旋转，只有脸会倾斜、缩放、呼吸
            drawScreenPanel(
                metrics = metrics,
                dim = params.panelDim,
                withHousing = false,
            )
            withTransform(faceTransform) {
                drawFaceContent(metrics, params)
            }
        }
    }
}

/** 屏幕上真正会动的部分：腮红、眼睛、猫嘴、情绪符号。 */
private fun DrawScope.drawFaceContent(metrics: FaceMetrics, params: FaceParams) {
    drawEye(
        center = Offset(metrics.leftEyeCenterX, metrics.eyeCenterY),
        eye = params.leftEye,
        metrics = metrics,
    )
    drawEye(
        center = Offset(metrics.rightEyeCenterX, metrics.eyeCenterY),
        eye = params.rightEye,
        metrics = metrics,
    )
    // 腮红画在眼睛之后：否则会被眼睛的光晕冲淡，粉色会发灰
    drawBlush(metrics, alpha = params.blushAlpha)
    drawMouth(metrics, params.mouth)
    params.marks.forEach { drawMark(metrics, it) }
}

// ---------------------------------------------------------------- 机身与屏幕

private fun DrawScope.drawEar(metrics: FaceMetrics, direction: Float, glow: Float) {
    if (glow <= 0.01f) return

    val centerX = metrics.panelCenterX + direction * metrics.earOffsetX
    val halfWidth = metrics.earWidth / 2f
    val baseInner = Offset(centerX - direction * halfWidth, metrics.earBaseY)
    val baseOuter = Offset(centerX + direction * halfWidth, metrics.earBaseY)
    val tip = Offset(centerX + direction * halfWidth * 0.3f, metrics.earTipY)

    val amber = FacePalette.EarGlow
    val baseCenter = Offset(centerX, metrics.earBaseY)

    // 耳朵背后的暖光，让耳朵看起来是“亮着的”而不是贴上去的形状
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                amber.copy(alpha = 0.32f * glow),
                amber.copy(alpha = 0f),
            ),
            center = baseCenter,
            radius = metrics.earWidth * 1.5f,
        ),
        radius = metrics.earWidth * 1.5f,
        center = baseCenter,
    )

    // 外层：与机身同色同材质的外壳
    val shell = earTriangle(baseInner, baseOuter, tip)
    drawPath(
        path = shell,
        brush = Brush.linearGradient(
            colors = listOf(FacePalette.BezelLight, FacePalette.BezelMid, FacePalette.BezelShade),
            start = baseCenter,
            end = tip,
        ),
    )
    drawPath(
        path = shell,
        color = FacePalette.BezelLight.copy(alpha = 0.75f),
        style = Stroke(width = metrics.panelHeight * 0.006f, join = StrokeJoin.Round),
    )

    // 内层：透出发光的琥珀色内芯
    val centroid = Offset(
        x = (baseInner.x + baseOuter.x + tip.x) / 3f,
        y = (baseInner.y + baseOuter.y + tip.y) / 3f,
    )
    val inner = earTriangle(
        baseInner = lerpOffset(baseInner, centroid, 0.3f),
        baseOuter = lerpOffset(baseOuter, centroid, 0.3f),
        tip = lerpOffset(tip, centroid, 0.22f),
    )
    drawPath(
        path = inner,
        brush = Brush.linearGradient(
            colors = listOf(
                FacePalette.EarCore.copy(alpha = 0.95f * glow),
                amber.copy(alpha = 0.85f * glow),
                amber.copy(alpha = 0.45f * glow),
            ),
            start = baseCenter,
            end = tip,
        ),
    )
    drawPath(
        path = inner,
        color = amber.copy(alpha = 0.35f * glow),
        style = Stroke(width = metrics.earWidth * 0.18f, join = StrokeJoin.Round),
    )
}

private fun earTriangle(baseInner: Offset, baseOuter: Offset, tip: Offset): Path = Path().apply {
    moveTo(baseInner.x, baseInner.y)
    lineTo(tip.x, tip.y)
    lineTo(baseOuter.x, baseOuter.y)
    close()
}

private fun lerpOffset(start: Offset, end: Offset, t: Float): Offset = Offset(
    x = start.x + (end.x - start.x) * t,
    y = start.y + (end.y - start.y) * t,
)

private fun DrawScope.drawScreenPanel(metrics: FaceMetrics, dim: Float, withHousing: Boolean) {
    val bezel = metrics.panelHeight * 0.055f
    val corner = CornerRadius(if (withHousing) metrics.cornerRadius else 0f)

    if (withHousing) {
        // 暖白机身外壳，让黑屏和耳朵之间有硬件过渡
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(FacePalette.BezelLight, FacePalette.BezelMid, FacePalette.BezelShade),
                startY = metrics.panelTop - bezel,
                endY = metrics.panelBottom + bezel,
            ),
            topLeft = Offset(metrics.panelLeft - bezel, metrics.panelTop - bezel),
            size = Size(metrics.panelWidth + bezel * 2f, metrics.panelHeight + bezel * 2f),
            cornerRadius = CornerRadius(metrics.cornerRadius + bezel),
        )
    }

    // 黑玻璃屏。实机模式下这就是整块显示屏。
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(FacePalette.PanelTop, FacePalette.PanelBottom),
            startY = metrics.panelTop,
            endY = metrics.panelBottom,
        ),
        topLeft = Offset(metrics.panelLeft, metrics.panelTop),
        size = Size(metrics.panelWidth, metrics.panelHeight),
        cornerRadius = corner,
    )

    // 顶部一道玻璃高光
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
            startY = metrics.panelTop,
            endY = metrics.panelTop + metrics.panelHeight * 0.45f,
        ),
        topLeft = Offset(metrics.panelLeft, metrics.panelTop),
        size = Size(metrics.panelWidth, metrics.panelHeight),
        cornerRadius = corner,
    )

    if (dim > 0.01f) {
        drawRoundRect(
            color = Color.Black.copy(alpha = (dim * 0.55f).coerceIn(0f, 1f)),
            topLeft = Offset(metrics.panelLeft, metrics.panelTop),
            size = Size(metrics.panelWidth, metrics.panelHeight),
            cornerRadius = corner,
        )
    }
}

private fun DrawScope.drawBlush(metrics: FaceMetrics, alpha: Float) {
    if (alpha <= 0.01f) return

    val intensity = alpha.coerceIn(0f, 1f)
    listOf(metrics.leftEyeCenterX, metrics.rightEyeCenterX).forEach { centerX ->
        val center = Offset(centerX + (centerX - metrics.panelCenterX) * 0.1f, metrics.blushCenterY)

        // 用多层椭圆叠出软边，避免椭圆上直接套径向渐变产生硬边
        for (layer in 5 downTo 1) {
            val scale = 0.35f + 0.65f * (layer / 5f)
            val layerAlpha = (0.44f - 0.06f * layer) * intensity
            drawOval(
                color = FacePalette.Blush.copy(alpha = layerAlpha),
                topLeft = Offset(
                    center.x - metrics.blushRadiusX * scale,
                    center.y - metrics.blushRadiusY * scale,
                ),
                size = Size(metrics.blushRadiusX * 2f * scale, metrics.blushRadiusY * 2f * scale),
            )
        }
    }
}

// ---------------------------------------------------------------- 眼睛

private fun DrawScope.drawEye(center: Offset, eye: EyeParams, metrics: FaceMetrics) {
    val radius = metrics.eyeRadius * eye.radius
    if (radius <= 0f) return

    val glow = eye.glow.coerceIn(0f, 2f)
    val strokeWidth = metrics.eyeStrokeWidth * (0.65f + 0.35f * eye.radius)
    val color = FacePalette.Glow
    val isRingEye = eye.shape == EyeShape.OPEN || eye.shape == EyeShape.WIDE
    val haloScale = if (isRingEye) 2.6f else 1.8f
    val haloAlpha = (if (isRingEye) 0.32f else 0.15f) * glow

    if (glow > 0.02f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    FacePalette.GlowHalo.copy(alpha = haloAlpha),
                    FacePalette.GlowHalo.copy(alpha = 0f),
                ),
                center = center,
                radius = radius * haloScale,
            ),
            radius = radius * haloScale,
            center = center,
        )
    }

    rotate(degrees = eye.tiltDegrees, pivot = center) {
        when (eye.shape) {
            EyeShape.OPEN, EyeShape.WIDE -> drawRingEye(center, radius, eye, strokeWidth, color, glow)
            EyeShape.HAPPY_ARC -> drawGlowArc(
                center = center,
                radiusX = radius,
                radiusY = radius * 0.95f,
                startAngle = 197f,
                sweepAngle = 146f,
                strokeWidth = strokeWidth,
                color = color,
                glow = glow,
            )

            EyeShape.CALM_ARC -> drawGlowArc(
                center = center,
                radiusX = radius * 1.05f,
                radiusY = radius * 0.6f,
                startAngle = 22f,
                sweepAngle = 136f,
                strokeWidth = strokeWidth,
                color = color,
                glow = glow,
            )

            EyeShape.HALF_LID -> {
                drawGlowArc(
                    center = center,
                    radiusX = radius,
                    radiusY = radius * 0.95f,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    strokeWidth = strokeWidth * 0.8f,
                    color = color,
                    glow = glow * 0.5f,
                )
                drawLine(
                    color = color,
                    start = Offset(center.x - radius, center.y),
                    end = Offset(center.x + radius, center.y),
                    strokeWidth = strokeWidth * 0.8f,
                    cap = StrokeCap.Round,
                )
            }

            EyeShape.CLOSED -> drawLine(
                color = color.copy(alpha = 0.5f + 0.5f * glow.coerceAtMost(1f)),
                start = Offset(center.x - radius * 0.9f, center.y),
                end = Offset(center.x + radius * 0.9f, center.y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )

            EyeShape.HEART -> {
                drawPath(
                    path = heartPath(center, radius * 0.95f),
                    color = FacePalette.Blush.copy(alpha = 0.9f),
                )
                drawPath(
                    path = heartPath(center, radius * 0.95f),
                    color = color.copy(alpha = 0.7f * glow.coerceAtMost(1f)),
                    style = Stroke(width = strokeWidth * 0.5f, join = StrokeJoin.Round),
                )
            }
        }
    }
}

private fun DrawScope.drawRingEye(
    center: Offset,
    radius: Float,
    eye: EyeParams,
    strokeWidth: Float,
    color: Color,
    glow: Float,
) {
    val openness = eye.openness.coerceIn(0f, 1f)
    val radiusY = (radius * openness).coerceAtLeast(strokeWidth * 0.5f)
    val topLeft = Offset(center.x - radius, center.y - radiusY)
    val size = Size(radius * 2f, radiusY * 2f)

    // 多层描边叠加出“发光”而不是一块死白
    drawOval(
        color = color.copy(alpha = 0.12f * glow),
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth * 2.6f, cap = StrokeCap.Round),
    )
    drawOval(
        color = color.copy(alpha = 0.3f * glow),
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth * 1.6f, cap = StrokeCap.Round),
    )
    drawOval(
        color = color,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )

    if (eye.pupilAlpha > 0.01f) {
        val pupilOffset = Offset(
            x = eye.pupilOffsetX * radius * 0.55f,
            y = eye.pupilOffsetY * radius * 0.55f,
        )
        drawCircle(
            color = color.copy(alpha = eye.pupilAlpha),
            radius = radius * 0.3f,
            center = center + pupilOffset,
        )
    }
}

private fun DrawScope.drawGlowArc(
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    startAngle: Float,
    sweepAngle: Float,
    strokeWidth: Float,
    color: Color,
    glow: Float,
) {
    val topLeft = Offset(center.x - radiusX, center.y - radiusY)
    val size = Size(radiusX * 2f, radiusY * 2f)

    drawArc(
        color = color.copy(alpha = 0.12f * glow),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth * 2.6f, cap = StrokeCap.Round),
    )
    drawArc(
        color = color.copy(alpha = 0.3f * glow),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth * 1.6f, cap = StrokeCap.Round),
    )
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}

// ---------------------------------------------------------------- 猫嘴

private fun DrawScope.drawMouth(metrics: FaceMetrics, mouth: MouthParams) {
    val center = Offset(
        x = metrics.panelCenterX + mouth.offsetX * metrics.panelWidth,
        y = metrics.mouthCenterY + mouth.offsetY * metrics.panelHeight,
    )
    val halfWidth = (metrics.mouthHalfWidth * mouth.scale).coerceAtLeast(1f)
    val color = FacePalette.Glow
    val strokeWidth = metrics.panelHeight * 0.026f

    when (mouth.shape) {
        MouthShape.CAT_W -> drawPath(
            path = catMouthPath(center, halfWidth, halfWidth * 0.5f),
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        MouthShape.SMILE_ARC -> drawGlowArc(
            center = Offset(center.x, center.y - halfWidth * 0.2f),
            radiusX = halfWidth * 1.15f,
            radiusY = halfWidth * 0.95f,
            startAngle = 25f,
            sweepAngle = 130f,
            strokeWidth = strokeWidth,
            color = color,
            glow = 1f,
        )

        MouthShape.OPEN_SMILE -> {
            val arcCenter = Offset(center.x, center.y - halfWidth * 0.25f)
            drawGlowArc(
                center = arcCenter,
                radiusX = halfWidth * 1.2f,
                radiusY = halfWidth * 1.1f,
                startAngle = 25f,
                sweepAngle = 130f,
                strokeWidth = strokeWidth,
                color = color,
                glow = 1f,
            )
            if (mouth.showTongue) {
                drawOval(
                    color = FacePalette.Tongue.copy(alpha = 0.95f),
                    topLeft = Offset(
                        center.x - halfWidth * 0.42f,
                        center.y - halfWidth * 0.1f,
                    ),
                    size = Size(halfWidth * 0.84f, halfWidth * 0.8f),
                )
            }
        }

        MouthShape.SMALL_O -> {
            drawOval(
                color = color,
                topLeft = Offset(center.x - halfWidth * 0.3f, center.y - halfWidth * 0.36f),
                size = Size(halfWidth * 0.6f, halfWidth * 0.72f),
            )
        }

        MouthShape.FLAT -> drawLine(
            color = color,
            start = Offset(center.x - halfWidth * 0.6f, center.y),
            end = Offset(center.x + halfWidth * 0.6f, center.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        MouthShape.FROWN -> drawGlowArc(
            center = Offset(center.x, center.y + halfWidth * 0.9f),
            radiusX = halfWidth * 1.1f,
            radiusY = halfWidth * 1.1f,
            startAngle = 200f,
            sweepAngle = 140f,
            strokeWidth = strokeWidth,
            color = color,
            glow = 1f,
        )

        MouthShape.TONGUE -> {
            drawPath(
                path = catMouthPath(center, halfWidth, halfWidth * 0.5f),
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawOval(
                color = FacePalette.Tongue.copy(alpha = 0.95f),
                topLeft = Offset(center.x - halfWidth * 0.3f, center.y + halfWidth * 0.15f),
                size = Size(halfWidth * 0.6f, halfWidth * 0.7f),
            )
        }
    }
}

// ---------------------------------------------------------------- 情绪符号

private fun DrawScope.drawMark(metrics: FaceMetrics, mark: MarkParams) {
    if (mark.alpha <= 0.01f) return

    val center = Offset(
        x = metrics.panelCenterX + mark.offsetX * metrics.panelWidth,
        y = metrics.panelCenterY + mark.offsetY * metrics.panelHeight,
    )
    val size = metrics.panelHeight * 0.17f * mark.scale
    if (size <= 0f) return

    val color = mark.tint.toColor().copy(alpha = mark.alpha.coerceIn(0f, 1f))
    val strokeWidth = (size * 0.2f).coerceAtLeast(1f)

    rotate(degrees = mark.tiltDegrees, pivot = center) {
        when (mark.mark) {
            FaceMark.QUESTION -> drawQuestionMark(center, size, color, strokeWidth)
            FaceMark.EXCLAMATION -> {
                drawLine(
                    color = color,
                    start = Offset(center.x, center.y - size * 0.7f),
                    end = Offset(center.x, center.y + size * 0.2f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = color, radius = strokeWidth * 0.6f, center = Offset(center.x, center.y + size * 0.6f))
            }

            FaceMark.ZZZ -> {
                repeat(3) { index ->
                    val glyphCenter = Offset(
                        x = center.x - size * 0.62f + index * size * 0.62f,
                        y = center.y + size * 0.62f - index * size * 0.62f,
                    )
                    drawPath(
                        path = zPath(glyphCenter, size * (0.22f + index * 0.09f)),
                        color = color.copy(alpha = color.alpha * (0.55f + index * 0.22f)),
                        style = Stroke(width = strokeWidth * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }

            FaceMark.WAVE -> repeat(2) { index ->
                val radius = size * (0.55f + index * 0.42f)
                drawArc(
                    color = color.copy(alpha = color.alpha * (1f - index * 0.3f)),
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            FaceMark.TEAR -> drawPath(dropletPath(center, size * 0.55f), color)
            FaceMark.SWEAT -> drawPath(dropletPath(center, size * 0.4f), color)
            FaceMark.SPARKLE -> drawPath(sparklePath(center, size * 0.75f), color)
            FaceMark.HEART -> drawPath(heartPath(center, size * 0.6f), color)
            FaceMark.BOLT -> drawPath(boltPath(center, size * 0.7f), color)

            FaceMark.ANGER -> repeat(4) { index ->
                val angle = index * PI.toFloat() / 4f + 0.4f
                val inner = size * 0.12f
                val outer = size * 0.45f
                drawLine(
                    color = color,
                    start = Offset(center.x + cosf(angle) * inner, center.y + sinf(angle) * inner),
                    end = Offset(center.x + cosf(angle) * outer, center.y + sinf(angle) * outer),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            FaceMark.DOTS -> repeat(3) { index ->
                drawCircle(
                    color = color,
                    radius = strokeWidth * 0.7f,
                    center = Offset(center.x + (index - 1) * size * 0.42f, center.y),
                )
            }

            FaceMark.SLASH_CIRCLE -> {
                val radius = size * 0.5f
                drawCircle(
                    color = color,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawLine(
                    color = color,
                    start = Offset(center.x - radius * 0.7f, center.y + radius * 0.7f),
                    end = Offset(center.x + radius * 0.7f, center.y - radius * 0.7f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.drawQuestionMark(center: Offset, size: Float, color: Color, strokeWidth: Float) {
    val hookCenter = Offset(center.x, center.y - size * 0.45f)
    val radius = size * 0.36f

    drawArc(
        color = color,
        startAngle = 165f,
        sweepAngle = 235f,
        useCenter = false,
        topLeft = Offset(hookCenter.x - radius, hookCenter.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
    drawLine(
        color = color,
        start = Offset(center.x, hookCenter.y + radius * 0.9f),
        end = Offset(center.x, center.y + size * 0.25f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = color,
        radius = strokeWidth * 0.62f,
        center = Offset(center.x, center.y + size * 0.65f),
    )
}

private fun cosf(angle: Float): Float = kotlin.math.cos(angle)
private fun sinf(angle: Float): Float = kotlin.math.sin(angle)
