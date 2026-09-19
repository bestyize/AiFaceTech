package com.aifacemate.face.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpressionLibraryTest {

    @Test
    fun reservesAll25ExpressionSlots() {
        assertEquals(25, FaceExpression.entries.size)
    }

    @Test
    fun alphaOneMatchesPrdPriorityList() {
        assertEquals(
            listOf(
                FaceExpression.IDLE,
                FaceExpression.HAPPY,
                FaceExpression.CURIOUS,
                FaceExpression.SLEEPING,
                FaceExpression.GREETING,
            ),
            FaceExpression.alphaOne,
        )
    }

    @Test
    fun everyExpressionHasValidParams() {
        FaceExpression.entries.forEach { expression ->
            val problems = ExpressionLibrary.paramsFor(expression).validate()
            assertTrue(problems.isEmpty(), "${expression.enName} 参数越界: $problems")
        }
    }

    @Test
    fun idleUsesRingEyesAndCatMouth() {
        val params = ExpressionLibrary.paramsFor(FaceExpression.IDLE)
        assertEquals(EyeShape.OPEN, params.leftEye.shape)
        assertEquals(EyeShape.OPEN, params.rightEye.shape)
        assertEquals(MouthShape.CAT_W, params.mouth.shape)
        assertEquals(0f, params.blushAlpha)
    }

    @Test
    fun happySquintsWithArcEyesAndBlushes() {
        val params = ExpressionLibrary.paramsFor(FaceExpression.HAPPY)
        assertEquals(EyeShape.HAPPY_ARC, params.leftEye.shape)
        assertEquals(EyeShape.HAPPY_ARC, params.rightEye.shape)
        assertEquals(MouthShape.SMILE_ARC, params.mouth.shape)
        assertTrue(params.blushAlpha > 0f, "开心的腮红应当可见")
    }

    @Test
    fun curiousIsAsymmetricAndAsksAQuestion() {
        val params = ExpressionLibrary.paramsFor(FaceExpression.CURIOUS)
        assertTrue(
            params.leftEye.radius != params.rightEye.radius,
            "好奇表情需要一大一小的眼睛",
        )
        assertTrue(params.marks.any { it.mark == FaceMark.QUESTION && it.alpha > 0f })
    }

    @Test
    fun sleepingClosesEyesDimsPanelAndShowsZzz() {
        val params = ExpressionLibrary.paramsFor(FaceExpression.SLEEPING)
        assertEquals(EyeShape.CALM_ARC, params.leftEye.shape)
        assertEquals(EyeShape.CALM_ARC, params.rightEye.shape)
        assertTrue(params.panelDim > 0f, "睡眠时屏幕应当压暗")
        assertTrue(params.earGlow < 1f, "睡眠时耳朵灯应当减弱")
        assertTrue(params.marks.any { it.mark == FaceMark.ZZZ && it.alpha > 0f })
    }

    @Test
    fun greetingWavesAndSmiles() {
        val params = ExpressionLibrary.paramsFor(FaceExpression.GREETING)
        assertTrue(
            params.marks.any { it.mark == FaceMark.WAVE && it.alpha > 0f },
            "打招呼需要挥手符号",
        )
        assertTrue(
            params.mouth.shape == MouthShape.SMILE_ARC || params.mouth.shape == MouthShape.OPEN_SMILE,
            "打招呼应当带笑容，当前为 ${params.mouth.shape}",
        )
    }
}
