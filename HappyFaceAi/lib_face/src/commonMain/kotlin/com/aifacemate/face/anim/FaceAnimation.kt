package com.aifacemate.face.anim

import com.aifacemate.face.model.FaceParams
import com.aifacemate.face.model.EyeParams
import com.aifacemate.face.model.MarkParams
import kotlin.random.Random
import kotlin.math.PI
import kotlin.math.sin

/** 线性插值，[t] 会被夹到 0..1（越界视为端点）。 */
fun lerp(start: Float, end: Float, t: Float): Float {
    val progress = t.coerceIn(0f, 1f)
    return start + (end - start) * progress
}

/** 标准缓入缓出曲线，用于表情切换。 */
fun easeInOutCubic(t: Float): Float {
    val progress = t.coerceIn(0f, 1f)
    return if (progress < 0.5f) {
        4f * progress * progress * progress
    } else {
        val inverted = -2f * progress + 2f
        1f - inverted * inverted * inverted / 2f
    }
}

/**
 * 在两组表情参数之间过渡。
 *
 * 数值参数线性混合；枚举型参数（眼型、嘴型）在 [t] 过半时切换；
 * 情绪符号按类型匹配，淡入淡出，完全淡出后从结果里移除。
 */
fun blendParams(start: FaceParams, end: FaceParams, t: Float): FaceParams {
    val progress = t.coerceIn(0f, 1f)

    return FaceParams(
        tiltDegrees = lerp(start.tiltDegrees, end.tiltDegrees, progress),
        scale = lerp(start.scale, end.scale, progress),
        offsetY = lerp(start.offsetY, end.offsetY, progress),
        leftEye = blendEyeParams(start.leftEye, end.leftEye, progress),
        rightEye = blendEyeParams(start.rightEye, end.rightEye, progress),
        mouth = start.mouth.copy(
            // 嘴型在过渡过半时切换，避免出现两套嘴型叠在一起
            shape = if (progress < 0.5f) start.mouth.shape else end.mouth.shape,
            scale = lerp(start.mouth.scale, end.mouth.scale, progress),
            openness = lerp(start.mouth.openness, end.mouth.openness, progress),
            offsetX = lerp(start.mouth.offsetX, end.mouth.offsetX, progress),
            offsetY = lerp(start.mouth.offsetY, end.mouth.offsetY, progress),
            showTongue = if (progress < 0.5f) start.mouth.showTongue else end.mouth.showTongue,
        ),
        blushAlpha = lerp(start.blushAlpha, end.blushAlpha, progress),
        panelDim = lerp(start.panelDim, end.panelDim, progress),
        earGlow = lerp(start.earGlow, end.earGlow, progress),
        marks = blendMarks(start.marks, end.marks, progress),
    )
}

private fun blendEyeParams(
    start: EyeParams,
    end: EyeParams,
    t: Float,
): EyeParams = start.copy(
    shape = if (t < 0.5f) start.shape else end.shape,
    radius = lerp(start.radius, end.radius, t),
    openness = lerp(start.openness, end.openness, t),
    offsetX = lerp(start.offsetX, end.offsetX, t),
    offsetY = lerp(start.offsetY, end.offsetY, t),
    pupilOffsetX = lerp(start.pupilOffsetX, end.pupilOffsetX, t),
    pupilOffsetY = lerp(start.pupilOffsetY, end.pupilOffsetY, t),
    pupilAlpha = lerp(start.pupilAlpha, end.pupilAlpha, t),
    glow = lerp(start.glow, end.glow, t),
    tiltDegrees = lerp(start.tiltDegrees, end.tiltDegrees, t),
)

/**
 * 情绪符号按类型匹配：两边都有的淡入淡出，只在一侧的按 [t] 淡入或淡出，
 * 完全淡出后从结果里移除，这样切换表情时不会残留上一个表情的符号。
 */
private fun blendMarks(start: List<MarkParams>, end: List<MarkParams>, t: Float): List<MarkParams> {
    val marks = (start.map { it.mark } + end.map { it.mark }).distinct()
    return marks.mapNotNull { mark ->
        val from = start.lastOrNull { it.mark == mark }
        val to = end.lastOrNull { it.mark == mark }
        val alpha = when {
            from == null -> to!!.alpha * t
            to == null -> from.alpha * (1f - t)
            else -> lerp(from.alpha, to.alpha, t)
        }
        if (alpha <= 0.001f) return@mapNotNull null

        val template = if (t < 0.5f) (from ?: to!!) else (to ?: from!!)
        template.copy(
            alpha = alpha,
            offsetX = lerp(from?.offsetX ?: template.offsetX, to?.offsetX ?: template.offsetX, t),
            offsetY = lerp(from?.offsetY ?: template.offsetY, to?.offsetY ?: template.offsetY, t),
            scale = lerp(from?.scale ?: template.scale, to?.scale ?: template.scale, t),
            tiltDegrees = lerp(
                from?.tiltDegrees ?: template.tiltDegrees,
                to?.tiltDegrees ?: template.tiltDegrees,
                t,
            ),
        )
    }
}

/**
 * 眨眼曲线：相位 0.0 与 1.0 完全睁眼，0.5 完全闭合。
 * 返回的是“睁眼程度”，可直接乘进 [com.aifacemate.face.model.EyeParams.openness]。
 */
fun blinkOpenness(phase: Float): Float {
    val clamped = phase.coerceIn(0f, 1f)
    return (1.0 - sin(clamped * PI)).toFloat()
}

/** 在下一次眨眼之前等待的毫秒数，默认 2.4s ~ 6.4s 的随机间隔。 */
fun nextBlinkDelayMillis(
    random: Random,
    minMillis: Int = 2_400,
    maxMillis: Int = 6_400,
): Int {
    val safeMin = minMillis.coerceAtLeast(0)
    val safeMax = maxMillis.coerceAtLeast(safeMin)
    return if (safeMax == safeMin) safeMin else random.nextInt(safeMin, safeMax + 1)
}

/**
 * 呼吸起伏系数。返回 `1 ± amplitude` 范围内的值，
 * 周期为 [periodMillis]，用于让待机中的机器人保持“活着”的感觉。
 */
fun breathScale(
    elapsedMillis: Long,
    periodMillis: Long = 4_600L,
    amplitude: Float = 0.012f,
): Float {
    if (periodMillis <= 0L) return 1f
    val phase = (elapsedMillis.toDouble() % periodMillis.toDouble()) / periodMillis.toDouble()
    return (1.0 + amplitude * sin(2.0 * PI * phase)).toFloat()
}
