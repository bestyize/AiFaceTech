package com.aifacemate.face.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 表情用的矢量图形。全部用路径画，不依赖任何图片资源与字体，
 * 因此同一套参数可以等比渲染到 4 英寸屏幕或任意预览窗口。
 */

/** 猫嘴 ω。 */
internal fun catMouthPath(center: Offset, halfWidth: Float, depth: Float): Path {
    val left = center.x - halfWidth
    val right = center.x + halfWidth
    val top = center.y - depth * 0.25f
    val bottom = center.y + depth * 0.75f
    val control = halfWidth * 0.55f

    return Path().apply {
        moveTo(left, top)
        cubicTo(left + control, bottom, center.x - control, bottom, center.x, top)
        cubicTo(center.x + control, bottom, right - control, bottom, right, top)
    }
}

/** 爱心。 */
internal fun heartPath(center: Offset, radius: Float): Path {
    val top = center.y - radius * 0.5f
    val bottom = center.y + radius

    return Path().apply {
        moveTo(center.x, bottom)
        cubicTo(
            center.x - radius * 1.55f, center.y - radius * 0.05f,
            center.x - radius * 0.75f, top - radius * 0.75f,
            center.x, top,
        )
        cubicTo(
            center.x + radius * 0.75f, top - radius * 0.75f,
            center.x + radius * 1.55f, center.y - radius * 0.05f,
            center.x, bottom,
        )
        close()
    }
}

/** 四角星，用于“兴奋 / 得意”。 */
internal fun sparklePath(center: Offset, radius: Float): Path {
    val waist = radius * 0.2f

    return Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + waist, center.y - waist, center.x + radius, center.y)
        quadraticTo(center.x + waist, center.y + waist, center.x, center.y + radius)
        quadraticTo(center.x - waist, center.y + waist, center.x - radius, center.y)
        quadraticTo(center.x - waist, center.y - waist, center.x, center.y - radius)
        close()
    }
}

/** 水滴，用于眼泪与冷汗。 */
internal fun dropletPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x, center.y - radius * 1.3f)
    cubicTo(
        center.x + radius * 0.85f, center.y - radius * 0.1f,
        center.x + radius, center.y + radius * 0.35f,
        center.x, center.y + radius,
    )
    cubicTo(
        center.x - radius, center.y + radius * 0.35f,
        center.x - radius * 0.85f, center.y - radius * 0.1f,
        center.x, center.y - radius * 1.3f,
    )
    close()
}

/** 闪电，用于充电。 */
internal fun boltPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x + radius * 0.35f, center.y - radius)
    lineTo(center.x - radius * 0.6f, center.y + radius * 0.15f)
    lineTo(center.x - radius * 0.05f, center.y + radius * 0.15f)
    lineTo(center.x - radius * 0.35f, center.y + radius)
    lineTo(center.x + radius * 0.6f, center.y - radius * 0.2f)
    lineTo(center.x + radius * 0.05f, center.y - radius * 0.2f)
    close()
}

/** 字母 z，用于睡眠符号。 */
internal fun zPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x - radius, center.y - radius)
    lineTo(center.x + radius, center.y - radius)
    lineTo(center.x - radius, center.y + radius)
    lineTo(center.x + radius, center.y + radius)
}

/** 波浪嘴，委屈 / 迷糊。 */
internal fun wavyMouthPath(center: Offset, halfWidth: Float, depth: Float): Path = Path().apply {
    moveTo(center.x - halfWidth, center.y)
    quadraticTo(center.x - halfWidth * 0.5f, center.y - depth, center.x, center.y)
    quadraticTo(center.x + halfWidth * 0.5f, center.y + depth, center.x + halfWidth, center.y)
}

/** 螺旋线，晕乎的螺旋眼。 */
internal fun spiralPath(center: Offset, radius: Float, turns: Float = 2.2f): Path {
    val steps = 120
    val totalAngle = turns * 2f * PI.toFloat()

    return Path().apply {
        for (step in 0..steps) {
            val progress = step / steps.toFloat()
            val angle = totalAngle * progress
            val currentRadius = radius * progress
            val x = center.x + cos(angle) * currentRadius
            val y = center.y + sin(angle) * currentRadius
            if (step == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
}

/** 盾牌，守护。 */
internal fun shieldPath(center: Offset, halfHeight: Float): Path {
    val halfWidth = halfHeight * 0.72f
    val top = center.y - halfHeight
    val bottom = center.y + halfHeight
    val shoulder = center.y + halfHeight * 0.05f

    return Path().apply {
        moveTo(center.x - halfWidth, top)
        lineTo(center.x + halfWidth, top)
        lineTo(center.x + halfWidth, shoulder)
        // 直线收拢到底部尖角，五边形盾牌轮廓
        lineTo(center.x, bottom)
        lineTo(center.x - halfWidth, shoulder)
        close()
    }
}

/** 五角星，被奖励的星星眼（设计图 84 的环内星星）。 */
internal fun fivePointStarPath(center: Offset, radius: Float, innerRatio: Float = 0.46f): Path {
    val points = 5
    val step = PI.toFloat() / points

    return Path().apply {
        repeat(points * 2) { index ->
            val currentRadius = if (index % 2 == 0) radius else radius * innerRatio
            val angle = -PI.toFloat() / 2f + index * step
            val x = center.x + cos(angle) * currentRadius
            val y = center.y + sin(angle) * currentRadius
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
