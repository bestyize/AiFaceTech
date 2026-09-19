package com.aifacemate.face.anim

import com.aifacemate.face.model.FaceParams
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.model.ExpressionLibrary
import com.aifacemate.face.model.EyeShape
import kotlin.random.Random

/**
 * 表情动画状态机：把“当前该显示哪个表情”转换成“这一帧该画什么参数”。
 *
 * 时间由调用方注入（[update] 的 `nowMillis`），因此可以在测试里逐帧驱动，
 * 不需要真实的帧循环。
 */
class FaceAnimator(
    private val random: Random = Random.Default,
    private val transitionDurationMillis: Long = 420L,
    private val blinkDurationMillis: Long = 160L,
    private val blinkIntervalMillis: IntRange = 2_400..6_400,
    private val breathePeriodMillis: Long = 4_600L,
    private val breatheAmplitude: Float = 0.012f,
) {

    /** 参与眨眼的眼型：只有圆环类眼睛需要眨眼，弧形眼本身就是闭眼造型。 */
    private val blinkingShapes = setOf(EyeShape.OPEN, EyeShape.WIDE)

    /** 表情切换完成后的参数，不含眨眼与呼吸，便于测试与状态检查。 */
    var baseParams: FaceParams = FaceParams()
        private set

    /** 实际用于渲染的参数，等于 [baseParams] 叠加眨眼与呼吸。 */
    var params: FaceParams = baseParams
        private set

    /** 是否还在做表情过渡。 */
    var isTransitioning: Boolean = false
        private set

    private var activeExpression: FaceExpression? = null
    private var transitionFrom: FaceParams = FaceParams()
    private var transitionStartMillis: Long = 0L
    private var firstUpdateMillis: Long? = null
    private var nextBlinkAtMillis: Long? = null
    private var blinkStartMillis: Long? = null

    /** 推进到 [nowMillis] 时刻并返回该帧的渲染参数。 */
    fun update(nowMillis: Long, expression: FaceExpression): FaceParams {
        if (firstUpdateMillis == null) {
            firstUpdateMillis = nowMillis
        }

        if (activeExpression != expression) {
            transitionFrom = baseParams
            activeExpression = expression
            transitionStartMillis = nowMillis
            isTransitioning = true
        }

        val target = ExpressionLibrary.paramsFor(expression)
        baseParams = if (isTransitioning) {
            val elapsed = nowMillis - transitionStartMillis
            val progress = if (transitionDurationMillis <= 0L) {
                1f
            } else {
                (elapsed.toFloat() / transitionDurationMillis).coerceIn(0f, 1f)
            }
            if (progress >= 1f) {
                isTransitioning = false
                target
            } else {
                blendParams(transitionFrom, target, easeInOutCubic(progress))
            }
        } else {
            target
        }

        params = applyLifeEffects(baseParams, nowMillis)
        return params
    }

    /** 叠加眨眼与呼吸：表情本身决定“长什么样”，生命感决定“怎么动”。 */
    private fun applyLifeEffects(base: FaceParams, nowMillis: Long): FaceParams {
        val blinkOpenness = resolveBlinkOpenness(base, nowMillis)
        val breatheElapsed = nowMillis - (firstUpdateMillis ?: nowMillis)

        return base.copy(
            scale = base.scale * breathScale(breatheElapsed, breathePeriodMillis, breatheAmplitude),
            leftEye = base.leftEye.copy(
                openness = base.leftEye.openness * blinkOpenness,
            ),
            rightEye = base.rightEye.copy(
                openness = base.rightEye.openness * blinkOpenness,
            ),
        )
    }

    private fun resolveBlinkOpenness(base: FaceParams, nowMillis: Long): Float {
        val blinkable = base.leftEye.shape in blinkingShapes && base.rightEye.shape in blinkingShapes
        if (!blinkable) {
            blinkStartMillis = null
            nextBlinkAtMillis = null
            return 1f
        }

        val activeStart = blinkStartMillis
        if (activeStart == null) {
            val dueAt = nextBlinkAtMillis ?: (nowMillis + nextBlinkDelayMillis(
                random,
                blinkIntervalMillis.first,
                blinkIntervalMillis.last,
            )).also { nextBlinkAtMillis = it }
            if (nowMillis < dueAt) return 1f

            blinkStartMillis = nowMillis
            return blinkOpenness(0f)
        }

        val phase = if (blinkDurationMillis <= 0L) {
            1f
        } else {
            (nowMillis - activeStart).toFloat() / blinkDurationMillis
        }
        if (phase >= 1f) {
            blinkStartMillis = null
            nextBlinkAtMillis = nowMillis + nextBlinkDelayMillis(
                random,
                blinkIntervalMillis.first,
                blinkIntervalMillis.last,
            )
            return 1f
        }
        return blinkOpenness(phase)
    }
}
