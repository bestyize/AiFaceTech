package com.aifacemate.face.anim

import com.aifacemate.face.model.EyeParams
import com.aifacemate.face.model.EyeShape
import com.aifacemate.face.model.FaceMark
import com.aifacemate.face.model.FaceParams
import com.aifacemate.face.model.MarkParams
import com.aifacemate.face.model.MarkTint
import com.aifacemate.face.model.MouthParams
import com.aifacemate.face.model.MouthShape
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaceAnimationTest {

    private val tolerance = 1e-4f

    @Test
    fun easingHitsBothEndsAndMidpoint() {
        assertEquals(0f, easeInOutCubic(0f), tolerance)
        assertEquals(0.5f, easeInOutCubic(0.5f), tolerance)
        assertEquals(1f, easeInOutCubic(1f), tolerance)
    }

    @Test
    fun easingIsMonotonic() {
        var previous = easeInOutCubic(0f)
        for (step in 1..100) {
            val current = easeInOutCubic(step / 100f)
            assertTrue(current >= previous - tolerance, "缓动曲线在 $step% 处回退")
            previous = current
        }
    }

    @Test
    fun interpolationClampsOutOfRangeProgress() {
        assertEquals(2f, lerp(2f, 8f, -1f), tolerance)
        assertEquals(8f, lerp(2f, 8f, 3f), tolerance)
    }

    @Test
    fun blendAtZeroReturnsStartAndAtOneReturnsEnd() {
        val start = FaceParams(leftEye = EyeParams(radius = 1f), mouth = MouthParams(scale = 1f))
        val end = FaceParams(leftEye = EyeParams(radius = 2f), mouth = MouthParams(scale = 3f))

        assertEquals(start.leftEye.radius, blendParams(start, end, 0f).leftEye.radius, tolerance)
        assertEquals(end.leftEye.radius, blendParams(start, end, 1f).leftEye.radius, tolerance)
        assertEquals(start.mouth.scale, blendParams(start, end, 0f).mouth.scale, tolerance)
        assertEquals(end.mouth.scale, blendParams(start, end, 1f).mouth.scale, tolerance)
    }

    @Test
    fun blendMovesNumericParamsByHalfAtMidpoint() {
        val start = FaceParams(leftEye = EyeParams(radius = 1f))
        val end = FaceParams(leftEye = EyeParams(radius = 2f))

        assertEquals(1.5f, blendParams(start, end, 0.5f).leftEye.radius, tolerance)
    }

    @Test
    fun blendSwitchesEnumShapesOncePastHalfway() {
        val start = FaceParams(leftEye = EyeParams(shape = EyeShape.OPEN))
        val end = FaceParams(leftEye = EyeParams(shape = EyeShape.HAPPY_ARC))

        assertEquals(EyeShape.OPEN, blendParams(start, end, 0.49f).leftEye.shape)
        assertEquals(EyeShape.HAPPY_ARC, blendParams(start, end, 0.5f).leftEye.shape)
    }

    @Test
    fun blendFadesMarksInAndDropsThemWhenGone() {
        val asleep = FaceParams(marks = listOf(MarkParams(FaceMark.ZZZ, alpha = 1f)))
        val awake = FaceParams()

        val fadingIn = blendParams(awake, asleep, 0.5f)
        val zzz = fadingIn.marks.single { it.mark == FaceMark.ZZZ }
        assertEquals(0.5f, zzz.alpha, tolerance)

        assertTrue(blendParams(asleep, awake, 1f).marks.isEmpty(), "淡出后符号应当被移除")
    }

    @Test
    fun blendKeepsBothMarksWhileCrossFading() {
        val from = FaceParams(marks = listOf(MarkParams(FaceMark.QUESTION, alpha = 1f)))
        val to = FaceParams(
            marks = listOf(
                MarkParams(FaceMark.WAVE, alpha = 1f, tint = MarkTint.GLOW),
                MarkParams(FaceMark.HEART, alpha = 1f, tint = MarkTint.PINK),
            ),
        )

        val blended = blendParams(from, to, 0.5f)

        assertEquals(setOf(FaceMark.QUESTION, FaceMark.WAVE, FaceMark.HEART), blended.marks.map { it.mark }.toSet())
        assertEquals(0.5f, blended.marks.single { it.mark == FaceMark.QUESTION }.alpha, tolerance)
        assertEquals(0.5f, blended.marks.single { it.mark == FaceMark.HEART }.alpha, tolerance)
    }

    @Test
    fun blinkIsOpenAtEndsAndShutInTheMiddle() {
        assertEquals(1f, blinkOpenness(0f), tolerance)
        assertEquals(0f, blinkOpenness(0.5f), tolerance)
        assertEquals(1f, blinkOpenness(1f), tolerance)
        assertTrue(blinkOpenness(0.25f) in 0f..1f)
    }

    @Test
    fun blinkDelayStaysWithinConfiguredRange() {
        val random = Random(20260919)
        repeat(200) {
            val delay = nextBlinkDelayMillis(random, minMillis = 2_400, maxMillis = 6_400)
            assertTrue(delay in 2_400..6_400, "眨眼间隔越界: $delay")
        }
    }

    @Test
    fun breathingStaysWithinAmplitudeAndRepeats() {
        val amplitude = 0.02f
        val period = 4_000L

        repeat(200) { step ->
            val scale = breathScale(step * 37L, period, amplitude)
            assertTrue(abs(scale - 1f) <= amplitude + tolerance, "呼吸幅度越界: $scale")
        }
        assertEquals(breathScale(0L, period, amplitude), breathScale(period, period, amplitude), tolerance)
        assertEquals(breathScale(250L, period, amplitude), breathScale(period + 250L, period, amplitude), tolerance)
    }
}
