package com.aifacemate.face.render

/**
 * 渲染表面。同一个表情要出现在两种完全不同的场合：参数相同，构图不同。
 */
enum class FaceSurface {
    /**
     * 实机屏幕：4.0 英寸 16:9 显示屏的实际内容，铺满整块屏幕。
     *
     * 屏幕里只有黑玻璃面板 + 眼睛 + 猫嘴 + 腮红 + 情绪符号；
     * 耳朵、侧灯环、机身外壳都是机身上的硬件灯效，不属于屏幕内容，
     * 因此表情参数里的 `earGlow` 在实机屏幕上不会产生任何绘制。
     */
    DEVICE_SCREEN,

    /**
     * 产品概念图：屏幕 + 发光耳朵 + 机身外壳。
     * 用于设计走查、官网、说明书这类需要看出整机形态的场合。
     */
    PRODUCT_MOCKUP,
}

/**
 * 猫脸的几何布局，全部以像素为单位，只依赖画布尺寸。
 *
 * 单独抽出来是为了让布局可测：渲染器只负责画，尺寸计算不掺在里面。
 */
data class FaceMetrics(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val panelLeft: Float,
    val panelTop: Float,
    val panelWidth: Float,
    val panelHeight: Float,
    val cornerRadius: Float,
    val eyeRadius: Float,
    val eyeStrokeWidth: Float,
    val eyeCenterY: Float,
    val eyeOffsetX: Float,
    val mouthCenterY: Float,
    val mouthHalfWidth: Float,
    val blushCenterY: Float,
    val blushRadiusX: Float,
    val blushRadiusY: Float,
    val earTipY: Float,
    val earBaseY: Float,
    val earOffsetX: Float,
    val earWidth: Float,
    val earHeight: Float,
) {
    val panelCenterX: Float get() = panelLeft + panelWidth / 2f
    val panelCenterY: Float get() = panelTop + panelHeight / 2f
    val panelRight: Float get() = panelLeft + panelWidth
    val panelBottom: Float get() = panelTop + panelHeight
    val leftEyeCenterX: Float get() = panelCenterX - eyeOffsetX
    val rightEyeCenterX: Float get() = panelCenterX + eyeOffsetX
}

/**
 * 屏幕内容宽高比：4.0 英寸 16:9。
 *
 * 设计图里的 96mm x 58mm 是屏幕模组外形，实际显示区为 88.6mm x 49.8mm，
 * 也就是标准 16:9。黑玻璃区域等于显示区，所以按 16:9 计算。
 */
const val PANEL_ASPECT_RATIO: Float = 16f / 9f

/**
 * 根据画布尺寸算出猫脸布局。
 *
 * 传入非法尺寸时返回可安全绘制的退化布局。
 * [surface] 为 [FaceSurface.DEVICE_SCREEN] 时面板铺满整个画布，不为耳朵和外壳预留位置。
 */
fun computeFaceMetrics(
    width: Float,
    height: Float,
    surface: FaceSurface = FaceSurface.PRODUCT_MOCKUP,
): FaceMetrics = when (surface) {
    FaceSurface.DEVICE_SCREEN -> computeDeviceScreenMetrics(width, height)
    FaceSurface.PRODUCT_MOCKUP -> computeProductMockupMetrics(width, height)
}

/** 实机屏幕：面板就是整块屏，脸上元素按屏幕高度等比排布，不预留任何机身位置。 */
private fun computeDeviceScreenMetrics(width: Float, height: Float): FaceMetrics {
    val canvasWidth = width.coerceAtLeast(0f)
    val canvasHeight = height.coerceAtLeast(0f)
    val eyeRadius = canvasHeight * 0.185f

    return FaceMetrics(
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        panelLeft = 0f,
        panelTop = 0f,
        panelWidth = canvasWidth,
        panelHeight = canvasHeight,
        cornerRadius = 0f,
        eyeRadius = eyeRadius,
        eyeStrokeWidth = eyeRadius * 0.4f,
        eyeCenterY = canvasHeight * 0.44f,
        eyeOffsetX = canvasWidth * 0.215f,
        mouthCenterY = canvasHeight * 0.70f,
        mouthHalfWidth = canvasHeight * 0.115f,
        blushCenterY = canvasHeight * 0.63f,
        blushRadiusX = canvasHeight * 0.115f,
        blushRadiusY = canvasHeight * 0.07f,
        earTipY = 0f,
        earBaseY = 0f,
        earOffsetX = 0f,
        earWidth = 0f,
        earHeight = 0f,
    )
}

/** 概念图：预留耳朵与机身外壳的位置。 */
private fun computeProductMockupMetrics(width: Float, height: Float): FaceMetrics {
    val canvasWidth = width.coerceAtLeast(0f)
    val canvasHeight = height.coerceAtLeast(0f)

    // 竖直方向预算：上留白 + 露出屏幕的耳朵 + 屏幕 + 下留白
    val topMargin = canvasHeight * 0.04f
    val bottomMargin = canvasHeight * 0.10f
    val earHeightRatio = 0.55f
    val earTuckRatio = 0.16f
    val verticalBudget = (canvasHeight - topMargin - bottomMargin).coerceAtLeast(0f)
    val heightLimited = verticalBudget / (1f + earHeightRatio - earTuckRatio)
    val widthLimited = (canvasWidth * 0.8f) / PANEL_ASPECT_RATIO

    val panelHeight = minOf(heightLimited, widthLimited).coerceAtLeast(0f)
    val panelWidth = panelHeight * PANEL_ASPECT_RATIO
    val panelLeft = (canvasWidth - panelWidth) / 2f

    val earHeight = panelHeight * earHeightRatio
    // 画完整个头部之后如果还有富余高度，就居中对齐；
    // 竖屏手机上不会出现“脸贴顶、下面一大片空白”的情况。
    val drawnHeight = earHeight * (1f - earTuckRatio) + panelHeight
    val slack = (canvasHeight - topMargin - bottomMargin - drawnHeight).coerceAtLeast(0f)
    val earTipY = topMargin + slack * 0.45f
    val earBaseY = earTipY + earHeight
    val panelTop = earBaseY - panelHeight * earTuckRatio

    val eyeRadius = panelHeight * 0.185f
    val eyeCenterY = panelTop + panelHeight * 0.44f
    val mouthCenterY = panelTop + panelHeight * 0.70f

    return FaceMetrics(
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        panelLeft = panelLeft,
        panelTop = panelTop,
        panelWidth = panelWidth,
        panelHeight = panelHeight,
        cornerRadius = panelHeight * 0.2f,
        eyeRadius = eyeRadius,
        eyeStrokeWidth = eyeRadius * 0.4f,
        eyeCenterY = eyeCenterY,
        eyeOffsetX = panelWidth * 0.215f,
        mouthCenterY = mouthCenterY,
        mouthHalfWidth = panelHeight * 0.115f,
        blushCenterY = panelTop + panelHeight * 0.63f,
        blushRadiusX = panelHeight * 0.115f,
        blushRadiusY = panelHeight * 0.07f,
        earTipY = earTipY,
        earBaseY = earBaseY,
        earOffsetX = panelWidth * 0.35f,
        earWidth = panelHeight * 0.42f,
        earHeight = earHeight,
    )
}
