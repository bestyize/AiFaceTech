package com.aifacemate.face.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpressionLibraryTest {

    @Test
    fun reservesAll100ExpressionSlots() {
        assertEquals(100, FaceExpression.entries.size)
        assertEquals(25, FaceExpression.basic.size)
        assertEquals(25, FaceExpression.extensionOne.size)
        assertEquals(25, FaceExpression.extensionTwo.size)
        assertEquals(25, FaceExpression.extensionThree.size)
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
    fun extensionThreeKeepsTheDesignedOrder() {
        assertEquals(
            listOf(
                FaceExpression.WELCOME_BACK,
                FaceExpression.EAGER,
                FaceExpression.ACKNOWLEDGING,
                FaceExpression.NAUGHTY,
                FaceExpression.REASSURED,
                FaceExpression.MISSING_YOU,
                FaceExpression.FAITHFUL_WAITING,
                FaceExpression.WANT_PRAISE,
                FaceExpression.REWARDED,
                FaceExpression.FLATTERED,
                FaceExpression.ATTENTIVE,
                FaceExpression.WHISPERING,
                FaceExpression.STRETCHING,
                FaceExpression.COZY,
                FaceExpression.BASHFUL_SMILE,
                FaceExpression.SPEECHLESS,
                FaceExpression.TROUBLED,
                FaceExpression.FLUSTERED,
                FaceExpression.CLINGY,
                FaceExpression.SNEAKING_A_PEEK,
                FaceExpression.HELPFUL,
                FaceExpression.HUMMING,
                FaceExpression.FULLY_CHARGED,
                FaceExpression.GOOD_NIGHT,
                FaceExpression.NEW_DAY,
            ),
            FaceExpression.entries.subList(75, 100),
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
    fun extensionThreeUsesTheNewPrimitives() {
        fun FaceExpression.params() = ExpressionLibrary.paramsFor(this)
        fun FaceExpression.markSet() = params().marks.map { it.mark }.toSet()

        assertEquals(EyeShape.SPIRAL, FaceExpression.FLUSTERED.params().leftEye.shape)
        assertEquals(EyeShape.STAR_RING, FaceExpression.REWARDED.params().leftEye.shape)
        assertEquals(EyeShape.CHEVRON_LEFT, FaceExpression.NAUGHTY.params().rightEye.shape)
        assertEquals(EyeShape.CHEVRON_RIGHT, FaceExpression.STRETCHING.params().leftEye.shape)
        assertEquals(EyeShape.CHEVRON_LEFT, FaceExpression.STRETCHING.params().rightEye.shape)
        assertEquals(EyeShape.CHEVRON_RIGHT, FaceExpression.EAGER.params().leftEye.shape)
        assertEquals(EyeShape.CHEVRON_LEFT, FaceExpression.EAGER.params().rightEye.shape)
        assertEquals(EyeShape.GLIMMER, FaceExpression.MISSING_YOU.params().leftEye.shape)
        assertEquals(EyeShape.SIDE_EYE, FaceExpression.WHISPERING.params().leftEye.shape)
        assertEquals(EyeShape.DROOPY, FaceExpression.TROUBLED.params().leftEye.shape)
        assertEquals(EyeShape.PLEADING, FaceExpression.CLINGY.params().leftEye.shape)
        assertEquals(EyeShape.CLOSED, FaceExpression.SPEECHLESS.params().leftEye.shape)

        assertEquals(MouthShape.OPEN_O, FaceExpression.STRETCHING.params().mouth.shape)

        assertTrue(FaceMark.CLOCK in FaceExpression.FAITHFUL_WAITING.markSet(), "守候需要时钟")
        assertTrue(FaceMark.ROSE in FaceExpression.TROUBLED.markSet(), "烦恼需要玫瑰花")
        assertTrue(FaceMark.MUSIC_NOTE in FaceExpression.HUMMING.markSet(), "哼唱需要音符")
        assertTrue(FaceMark.BATTERY_FULL in FaceExpression.FULLY_CHARGED.markSet(), "满电需要电池图标")
        assertTrue(FaceMark.SUN in FaceExpression.NEW_DAY.markSet(), "迎接新的一天需要太阳")
        assertTrue(FaceMark.THUMB_UP in FaceExpression.HELPFUL.markSet(), "乐于助人需要点赞手势")

        // 舒适用眯眼弧 ∩；安心 / 哼唱用下垂闭眼 ︶
        assertEquals(EyeShape.HAPPY_ARC, FaceExpression.COZY.params().leftEye.shape)
        assertEquals(EyeShape.CALM_ARC, FaceExpression.REASSURED.params().leftEye.shape)
        assertEquals(EyeShape.CALM_ARC, FaceExpression.HUMMING.params().leftEye.shape)

        // 舒适：两颊各一组红色斜线
        val cozyLines = ExpressionLibrary.paramsFor(FaceExpression.COZY).marks.count { it.mark == FaceMark.SPARK_LINES }
        assertEquals(2, cozyLines, "舒适需要两颊红色斜线")

        // 设计稿中的普通圆环眼没有实心白瞳
        listOf(
            FaceExpression.ACKNOWLEDGING,
            FaceExpression.NAUGHTY,
            FaceExpression.FAITHFUL_WAITING,
            FaceExpression.ATTENTIVE,
            FaceExpression.NEW_DAY,
        ).forEach { expression ->
            val params = expression.params()
            if (params.leftEye.shape == EyeShape.OPEN || params.leftEye.shape == EyeShape.WIDE) {
                assertEquals(0f, params.leftEye.pupilAlpha, "${expression.cnName} 左眼应是空心圆环")
            }
            if (params.rightEye.shape == EyeShape.OPEN || params.rightEye.shape == EyeShape.WIDE) {
                assertEquals(0f, params.rightEye.pupilAlpha, "${expression.cnName} 右眼应是空心圆环")
            }
        }
    }

    @Test
    fun stretchingRaisesBothPaws() {
        val paws = ExpressionLibrary.paramsFor(FaceExpression.STRETCHING).marks.filter { it.mark == FaceMark.PAW }
        assertEquals(2, paws.size, "伸懒腰需要两只爪子")
        assertTrue(paws.any { it.offsetX < 0f } && paws.any { it.offsetX > 0f }, "爪子应当分列两侧")
    }

    @Test
    fun goodNightDimsPanelAndShowsZzz() {
        val params = ExpressionLibrary.paramsFor(FaceExpression.GOOD_NIGHT)
        assertEquals(EyeShape.CALM_ARC, params.leftEye.shape)
        assertTrue(params.panelDim > 0f, "晚安时屏幕应当压暗")
        assertTrue(params.earGlow < 1f, "晚安时耳朵灯应当减弱")
        assertTrue(params.marks.any { it.mark == FaceMark.ZZZ }, "晚安需要 zzz")
    }

    @Test
    fun hugMeRaisesBothPaws() {
        val paws = ExpressionLibrary.paramsFor(FaceExpression.HUG_ME).marks.filter { it.mark == FaceMark.PAW }
        assertEquals(2, paws.size, "求抱抱需要两只爪子")
        assertTrue(paws.any { it.offsetX < 0f } && paws.any { it.offsetX > 0f }, "爪子应当分列两侧")
    }
}
