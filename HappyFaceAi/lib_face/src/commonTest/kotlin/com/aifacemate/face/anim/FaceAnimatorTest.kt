package com.aifacemate.face.anim

import com.aifacemate.face.model.EyeShape
import com.aifacemate.face.model.ExpressionLibrary
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.model.FaceParams
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaceAnimatorTest {

    private val tolerance = 1e-3f

    @Test
    fun settlesOnRequestedExpressionParams() {
        val animator = FaceAnimator(random = Random(1))
        val target = ExpressionLibrary.paramsFor(FaceExpression.IDLE)

        animator.update(nowMillis = 0L, expression = FaceExpression.IDLE)
        animator.update(nowMillis = 1_000L, expression = FaceExpression.IDLE)

        assertFaceParamsClose(target, animator.baseParams)
    }

    @Test
    fun reportsTransitioningOnlyWhileBlending() {
        val animator = FaceAnimator(random = Random(1), transitionDurationMillis = 400L)
        animator.update(nowMillis = 0L, expression = FaceExpression.IDLE)
        assertTrue(animator.isTransitioning, "刚切换表情时应当处于过渡中")

        animator.update(nowMillis = 500L, expression = FaceExpression.IDLE)
        assertTrue(!animator.isTransitioning, "过渡结束后应当停止报告过渡状态")
    }

    @Test
    fun transitionPassesThroughIntermediateFrame() {
        val animator = FaceAnimator(random = Random(1), transitionDurationMillis = 400L)
        val idle = ExpressionLibrary.paramsFor(FaceExpression.IDLE)
        val happy = ExpressionLibrary.paramsFor(FaceExpression.HAPPY)

        animator.update(nowMillis = 0L, expression = FaceExpression.IDLE)
        animator.update(nowMillis = 1_000L, expression = FaceExpression.IDLE)
        animator.update(nowMillis = 1_200L, expression = FaceExpression.HAPPY)
        val middle = animator.update(nowMillis = 1_400L, expression = FaceExpression.HAPPY)

        assertTrue(
            middle.blushAlpha > idle.blushAlpha + 0.01f && middle.blushAlpha < happy.blushAlpha - 0.01f,
            "过渡到一半时腮红应当介于两个表情之间，实际 ${middle.blushAlpha}",
        )
    }

    @Test
    fun eyeBlinkHappensDuringIdle() {
        val animator = FaceAnimator(random = Random(7))
        var mostClosed = 1f

        var now = 0L
        while (now <= 12_000L) {
            animator.update(now, FaceExpression.IDLE)
            mostClosed = minOf(mostClosed, animator.params.leftEye.openness)
            now += 16L
        }

        assertTrue(mostClosed < 0.2f, "12 秒内至少应当完成一次眨眼，实际最闭为 $mostClosed")
    }

    @Test
    fun closedEyeExpressionsDoNotBlink() {
        val animator = FaceAnimator(random = Random(7))
        var mostClosed = 1f

        var now = 0L
        while (now <= 12_000L) {
            animator.update(now, FaceExpression.HAPPY)
            mostClosed = minOf(mostClosed, animator.params.leftEye.openness)
            now += 16L
        }

        assertEquals(EyeShape.HAPPY_ARC, ExpressionLibrary.paramsFor(FaceExpression.HAPPY).leftEye.shape)
        assertTrue(mostClosed > 0.9f, "弧形眼不该参与眨眼，实际最闭为 $mostClosed")
    }

    @Test
    fun breathingKeepsWholeFaceAliveWhileIdle() {
        val animator = FaceAnimator(random = Random(3), breatheAmplitude = 0.02f)
        var minScale = Float.MAX_VALUE
        var maxScale = 0f

        var now = 0L
        while (now <= 6_000L) {
            animator.update(now, FaceExpression.IDLE)
            minScale = minOf(minScale, animator.params.scale)
            maxScale = maxOf(maxScale, animator.params.scale)
            now += 16L
        }

        assertTrue(maxScale > 1.005f, "呼吸应当有可见的放大，实际最大 $maxScale")
        assertTrue(minScale < 0.995f, "呼吸应当有可见的缩小，实际最小 $minScale")
        assertTrue(abs(maxScale - 1f) <= 0.02f + tolerance)
    }

    private fun assertFaceParamsClose(expected: FaceParams, actual: FaceParams) {
        assertEquals(expected.leftEye.shape, actual.leftEye.shape)
        assertEquals(expected.rightEye.shape, actual.rightEye.shape)
        assertEquals(expected.mouth.shape, actual.mouth.shape)
        assertEquals(expected.leftEye.radius, actual.leftEye.radius, tolerance)
        assertEquals(expected.rightEye.radius, actual.rightEye.radius, tolerance)
        assertEquals(expected.mouth.scale, actual.mouth.scale, tolerance)
        assertEquals(expected.blushAlpha, actual.blushAlpha, tolerance)
        assertEquals(expected.panelDim, actual.panelDim, tolerance)
        assertEquals(expected.earGlow, actual.earGlow, tolerance)
        assertEquals(expected.marks.map { it.mark }, actual.marks.map { it.mark })
    }
}
