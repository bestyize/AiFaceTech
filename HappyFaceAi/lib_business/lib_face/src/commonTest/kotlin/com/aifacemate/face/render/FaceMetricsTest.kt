package com.aifacemate.face.render

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaceMetricsTest {

    private val tolerance = 0.01f

    @Test
    fun deviceScreenFaceFillsTheWholeDisplay() {
        val metrics = computeFaceMetrics(1_600f, 900f, FaceSurface.DEVICE_SCREEN)

        assertEquals(0f, metrics.panelLeft, tolerance)
        assertEquals(0f, metrics.panelTop, tolerance)
        assertEquals(1_600f, metrics.panelWidth, tolerance)
        assertEquals(900f, metrics.panelHeight, tolerance)
        assertEquals(0f, metrics.cornerRadius, tolerance)
    }

    @Test
    fun deviceScreenFaceStaysInsideTheDisplay() {
        val metrics = computeFaceMetrics(1_600f, 900f, FaceSurface.DEVICE_SCREEN)

        val leftEdge = metrics.leftEyeCenterX - metrics.eyeRadius - metrics.eyeStrokeWidth
        val rightEdge = metrics.rightEyeCenterX + metrics.eyeRadius + metrics.eyeStrokeWidth
        assertTrue(leftEdge > 0f, "左眼超出屏幕: $leftEdge")
        assertTrue(rightEdge < 1_600f, "右眼超出屏幕: $rightEdge")
        assertTrue(metrics.eyeCenterY - metrics.eyeRadius > 0f, "眼睛超出屏幕上沿")
        assertTrue(metrics.mouthCenterY + metrics.mouthHalfWidth < 900f, "嘴部超出屏幕下沿")
    }

    @Test
    fun deviceScreenScalesWithScreenSize() {
        val small = computeFaceMetrics(800f, 450f, FaceSurface.DEVICE_SCREEN)
        val large = computeFaceMetrics(1_600f, 900f, FaceSurface.DEVICE_SCREEN)

        assertEquals(2f, large.eyeRadius / small.eyeRadius, tolerance)
        assertEquals(2f, large.mouthHalfWidth / small.mouthHalfWidth, tolerance)
    }

    /**
     * 产品定义：耳朵是机身上的硬件灯效，不在屏幕里。
     * 实机屏幕的布局必须完全没有耳朵几何，渲染器也就无从把耳朵画上去。
     */
    @Test
    fun deviceScreenHasNoEarGeometry() {
        val metrics = computeFaceMetrics(1_600f, 900f, FaceSurface.DEVICE_SCREEN)

        assertEquals(0f, metrics.earWidth, tolerance)
        assertEquals(0f, metrics.earHeight, tolerance)
        assertEquals(0f, metrics.earOffsetX, tolerance)
        assertEquals(metrics.earTipY, metrics.earBaseY, tolerance)
    }

    @Test
    fun panelKeepsThe16To9ScreenAspectRatio() {
        listOf(400f to 300f, 1_280f to 720f, 200f to 900f).forEach { (width, height) ->
            val metrics = computeFaceMetrics(width, height)
            val aspect = metrics.panelWidth / metrics.panelHeight
            assertTrue(
                abs(aspect - PANEL_ASPECT_RATIO) <= tolerance,
                "${width}x${height} 面板比例应为 $PANEL_ASPECT_RATIO，实际 $aspect",
            )
        }
    }

    @Test
    fun everythingFitsInsideTheCanvas() {
        listOf(320f to 200f, 800f to 600f, 1_920f to 1_080f).forEach { (width, height) ->
            val metrics = computeFaceMetrics(width, height)

            assertTrue(metrics.panelLeft >= -tolerance, "面板左侧越界: ${metrics.panelLeft}")
            assertTrue(metrics.panelRight <= width + tolerance, "面板右侧越界: ${metrics.panelRight}")
            assertTrue(metrics.panelBottom <= height + tolerance, "面板底部越界: ${metrics.panelBottom}")
            assertTrue(metrics.earTipY >= -tolerance, "耳朵顶部越界: ${metrics.earTipY}")
            val earOuterEdge = metrics.earOffsetX + metrics.earWidth / 2f
            assertTrue(earOuterEdge <= metrics.panelWidth / 2f + tolerance, "耳朵横向超出面板: $earOuterEdge")
        }
    }

    @Test
    fun earsSitAboveTheScreenPanel() {
        val metrics = computeFaceMetrics(800f, 600f)
        assertTrue(metrics.earTipY < metrics.panelTop, "耳朵尖端应当高出屏幕上沿")
        assertTrue(metrics.earBaseY > metrics.panelTop, "耳朵根部应当收在屏幕后面，避免和屏幕脱节")
        assertTrue(metrics.earHeight > 0f)
        assertTrue(metrics.earWidth > 0f)
    }

    @Test
    fun eyesAreSymmetricAroundThePanelCenter() {
        val metrics = computeFaceMetrics(800f, 600f)
        val midPoint = (metrics.leftEyeCenterX + metrics.rightEyeCenterX) / 2f
        assertTrue(abs(midPoint - metrics.panelCenterX) <= tolerance)
    }

    @Test
    fun facePartsStayInsideThePanel() {
        val metrics = computeFaceMetrics(1_280f, 720f)
        val eyeLeft = metrics.leftEyeCenterX - metrics.eyeRadius - metrics.eyeStrokeWidth
        val eyeRight = metrics.rightEyeCenterX + metrics.eyeRadius + metrics.eyeStrokeWidth

        assertTrue(eyeLeft > metrics.panelLeft, "眼睛超出面板左侧")
        assertTrue(eyeRight < metrics.panelRight, "眼睛超出面板右侧")
        assertTrue(metrics.mouthCenterY + metrics.mouthHalfWidth < metrics.panelBottom, "嘴部超出面板下沿")
    }

    @Test
    fun degenerateCanvasStillProducesADrawableLayout() {
        val metrics = computeFaceMetrics(0f, 0f)
        assertTrue(metrics.panelWidth >= 0f)
        assertTrue(metrics.panelHeight >= 0f)
        assertTrue(metrics.eyeRadius >= 0f)
    }
}
