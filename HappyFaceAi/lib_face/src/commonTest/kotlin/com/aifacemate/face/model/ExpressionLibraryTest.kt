package com.aifacemate.face.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpressionLibraryTest {

    @Test
    fun reservesAll75ExpressionSlots() {
        assertEquals(75, FaceExpression.entries.size)
    }

    @Test
    fun extensionOneKeepsTheDesignedOrder() {
        assertEquals(
            listOf(
                FaceExpression.RELAXED,
                FaceExpression.CONTENT,
                FaceExpression.PLAYFUL,
                FaceExpression.WINK,
                FaceExpression.ANTICIPATING,
                FaceExpression.CALM,
                FaceExpression.FOCUSED,
                FaceExpression.DETERMINED,
                FaceExpression.INSPIRED,
                FaceExpression.REALIZED,
                FaceExpression.SMIRKING,
                FaceExpression.AFFECTIONATE,
                FaceExpression.HUG_ME,
                FaceExpression.NERVOUS,
                FaceExpression.ALERT,
                FaceExpression.ALARMED,
                FaceExpression.BORED,
                FaceExpression.AGGRIEVED,
                FaceExpression.HELPLESS,
                FaceExpression.GRATEFUL,
                FaceExpression.ENCOURAGING,
                FaceExpression.APPROVING,
                FaceExpression.REFUSING,
                FaceExpression.SECRETIVE,
                FaceExpression.DAYDREAMING,
            ),
            FaceExpression.entries.subList(25, 50),
        )
    }

    @Test
    fun extensionTwoKeepsTheDesignedOrder() {
        assertEquals(
            listOf(
                FaceExpression.CELEBRATING,
                FaceExpression.TRIUMPHANT,
                FaceExpression.MEDITATING,
                FaceExpression.DREAMING,
                FaceExpression.YAWNING,
                FaceExpression.MISCHIEVOUS,
                FaceExpression.SERIOUS,
                FaceExpression.PROTECTIVE,
                FaceExpression.OBSERVING,
                FaceExpression.DEVOTED,
                FaceExpression.APOLOGETIC,
                FaceExpression.ASKING_HELP,
                FaceExpression.COMFORTING,
                FaceExpression.CHEERING,
                FaceExpression.PEEKING,
                FaceExpression.SLY,
                FaceExpression.FROZEN,
                FaceExpression.DIZZY,
                FaceExpression.UNWELL,
                FaceExpression.GROGGY,
                FaceExpression.SCANNING,
                FaceExpression.PROCESSING,
                FaceExpression.RECORDING,
                FaceExpression.SYNCING,
                FaceExpression.UPDATING,
            ),
            FaceExpression.entries.subList(50, 75),
        )
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

    @Test
    fun extensionExpressionsUseTheNewPrimitives() {
        fun FaceExpression.params() = ExpressionLibrary.paramsFor(this)
        fun FaceExpression.markSet() = params().marks.map { it.mark }.toSet()

        // 眼睛新图元
        assertEquals(EyeShape.STAR, FaceExpression.ANTICIPATING.params().leftEye.shape)
        assertEquals(EyeShape.STAR, FaceExpression.AFFECTIONATE.params().leftEye.shape)
        assertEquals(EyeShape.SPIRAL, FaceExpression.DIZZY.params().leftEye.shape)
        assertEquals(EyeShape.CROSS, FaceExpression.REFUSING.params().leftEye.shape)

        // 符号新图元
        assertTrue(FaceMark.LIGHT_BULB in FaceExpression.INSPIRED.markSet(), "灵感需要灯泡")
        assertTrue(FaceMark.SHIELD in FaceExpression.PROTECTIVE.markSet(), "守护需要盾牌")
        assertTrue(FaceMark.LOCK in FaceExpression.SECRETIVE.markSet(), "保密需要锁")
        assertTrue(FaceMark.THUMB_UP in FaceExpression.APPROVING.markSet(), "赞同需要点赞")
        assertTrue(FaceMark.CLOUD in FaceExpression.DAYDREAMING.markSet(), "发呆需要云朵气泡")
        assertTrue(FaceMark.THERMOMETER in FaceExpression.UNWELL.markSet(), "生病需要温度计")
        assertTrue(FaceMark.SCAN_FRAME in FaceExpression.SCANNING.markSet(), "侦测需要四角扫描框")
        assertTrue(FaceMark.SPINNER in FaceExpression.PROCESSING.markSet(), "处理中需要转圈")
        assertTrue(FaceMark.REC_DOT in FaceExpression.RECORDING.markSet(), "记录需要红点")
        assertTrue(FaceMark.SYNC_ARROW in FaceExpression.SYNCING.markSet(), "同步需要循环箭头")
        assertTrue(FaceMark.ARROW_UP in FaceExpression.UPDATING.markSet(), "升级需要上升箭头")
        assertTrue(FaceMark.PROGRESS_BAR in FaceExpression.UPDATING.markSet(), "升级需要进度条")
        assertTrue(FaceMark.CONFETTI in FaceExpression.CELEBRATING.markSet(), "庆祝需要彩带")
        assertTrue(FaceMark.CROSS_OUT in FaceExpression.REFUSING.markSet(), "拒绝需要红色叉")
        assertTrue(FaceMark.ALARM in FaceExpression.ALARMED.markSet(), "受惊需要惊叹爆发线")
    }

    @Test
    fun hugMeRaisesBothPaws() {
        val paws = ExpressionLibrary.paramsFor(FaceExpression.HUG_ME).marks.filter { it.mark == FaceMark.PAW }
        assertEquals(2, paws.size, "求抱抱需要两只爪子")
        assertTrue(paws.any { it.offsetX < 0f } && paws.any { it.offsetX > 0f }, "爪子应当分列两侧")
    }
}
