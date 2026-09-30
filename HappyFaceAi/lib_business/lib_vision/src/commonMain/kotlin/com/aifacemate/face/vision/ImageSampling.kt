package com.aifacemate.face.vision

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** x' = a·x + b·y + c, y' = d·x + e·y + f. */
internal data class Affine2D(val a: Double, val b: Double, val c: Double, val d: Double, val e: Double, val f: Double) {
    fun mapX(x: Double, y: Double) = a * x + b * y + c
    fun mapY(x: Double, y: Double) = d * x + e * y + f

    /** Applies this transform first, then [next]. */
    fun then(next: Affine2D) = Affine2D(
        next.a * a + next.b * d, next.a * b + next.b * e, next.a * c + next.b * f + next.c,
        next.d * a + next.e * d, next.d * b + next.e * e, next.d * c + next.e * f + next.f,
    )

    fun inverse(): Affine2D {
        val det = a * e - b * d
        require(abs(det) > 1e-12) { "Affine transform is not invertible" }
        return Affine2D(e / det, -b / det, (b * f - c * e) / det, -d / det, a / det, (c * d - a * f) / det)
    }

    internal val isIntegerTranslation: Boolean
        get() = a == 1.0 && b == 0.0 && d == 0.0 && e == 1.0 && c == floor(c) && f == floor(f)

    companion object {
        val identity = Affine2D(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)

        fun translate(x: Double, y: Double) = Affine2D(1.0, 0.0, x, 0.0, 1.0, y)

        /** The matrix of OpenCV `getRotationMatrix2D`: positive degrees turn counter-clockwise on screen. */
        fun rotation(centerX: Double, centerY: Double, degrees: Double): Affine2D {
            val radians = degrees * PI / 180
            val alpha = cos(radians)
            val beta = sin(radians)
            return Affine2D(alpha, beta, (1 - alpha) * centerX - beta * centerY, -beta, alpha, beta * centerX + (1 - alpha) * centerY)
        }

        /** Destination→source pixel mapping of a bilinear resize, with OpenCV's half-pixel centres. */
        fun resizeToSource(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int): Affine2D {
            val sx = sourceWidth.toDouble() / width
            val sy = sourceHeight.toDouble() / height
            return Affine2D(sx, 0.0, 0.5 * sx - 0.5, 0.0, sy, 0.5 * sy - 0.5)
        }
    }
}

internal enum class TensorLayout { Nchw, Nhwc }
internal enum class ChannelOrder { Rgb, Bgr }

/** [Clamp] repeats edge pixels, as a resize does; [Zero] treats pixels outside the frame as black, as a warp does. */
internal enum class Border { Clamp, Zero }

/**
 * Fills a [width]×[height] three-channel float tensor. Output pixel (x, y) samples the frame bilinearly at
 * [toSource](x, y); pixels outside the content rectangle are black. Each value is `(pixel − mean) × scale`.
 */
internal fun RgbaFrame.sample(
    width: Int,
    height: Int,
    toSource: Affine2D,
    layout: TensorLayout,
    order: ChannelOrder,
    border: Border,
    mean: Float = 0f,
    scale: Float = 1f,
    contentLeft: Int = 0,
    contentTop: Int = 0,
    contentRight: Int = width,
    contentBottom: Int = height,
): FloatArray {
    val output = FloatArray(width * height * 3)
    val black = -mean * scale
    if (black != 0f) output.fill(black)
    val plane = width * height
    val rgb = order == ChannelOrder.Rgb
    val data = this.pixels
    val sourceWidth = this.width
    val maxX = this.width - 1
    val maxY = this.height - 1
    val clamp = border == Border.Clamp
    val direct = toSource.isIntegerTranslation
    for (y in contentTop until contentBottom) {
        for (x in contentLeft until contentRight) {
            val r: Float
            val g: Float
            val b: Float
            if (direct) {
                // Common case: a camera frame copied into a larger model input without scaling.
                val sx = x + toSource.c.toInt()
                val sy = y + toSource.f.toInt()
                if (sx < 0 || sy < 0 || sx > maxX || sy > maxY) continue
                val at = (sy * sourceWidth + sx) * 4
                r = (data[at].toInt() and 255).toFloat()
                g = (data[at + 1].toInt() and 255).toFloat()
                b = (data[at + 2].toInt() and 255).toFloat()
            } else {
                var sx = toSource.mapX(x.toDouble(), y.toDouble())
                var sy = toSource.mapY(x.toDouble(), y.toDouble())
                if (clamp) {
                    sx = sx.coerceIn(0.0, maxX.toDouble())
                    sy = sy.coerceIn(0.0, maxY.toDouble())
                } else if (sx <= -1 || sy <= -1 || sx >= maxX + 1 || sy >= maxY + 1) continue
                val x0 = floor(sx).toInt()
                val y0 = floor(sy).toInt()
                val fx = (sx - x0).toFloat()
                val fy = (sy - y0).toFloat()
                val x1 = if (clamp) minOf(x0 + 1, maxX) else x0 + 1
                val y1 = if (clamp) minOf(y0 + 1, maxY) else y0 + 1
                // Neighbours outside the frame get zero weight, which is black for Border.Zero.
                val w00 = if (x0 >= 0 && y0 >= 0) (1 - fx) * (1 - fy) else 0f
                val w10 = if (x1 <= maxX && y0 >= 0) fx * (1 - fy) else 0f
                val w01 = if (x0 >= 0 && y1 <= maxY) (1 - fx) * fy else 0f
                val w11 = if (x1 <= maxX && y1 <= maxY) fx * fy else 0f
                val a00 = if (w00 > 0f) (y0 * sourceWidth + x0) * 4 else 0
                val a10 = if (w10 > 0f) (y0 * sourceWidth + x1) * 4 else 0
                val a01 = if (w01 > 0f) (y1 * sourceWidth + x0) * 4 else 0
                val a11 = if (w11 > 0f) (y1 * sourceWidth + x1) * 4 else 0
                r = (data[a00].toInt() and 255) * w00 + (data[a10].toInt() and 255) * w10 +
                    (data[a01].toInt() and 255) * w01 + (data[a11].toInt() and 255) * w11
                g = (data[a00 + 1].toInt() and 255) * w00 + (data[a10 + 1].toInt() and 255) * w10 +
                    (data[a01 + 1].toInt() and 255) * w01 + (data[a11 + 1].toInt() and 255) * w11
                b = (data[a00 + 2].toInt() and 255) * w00 + (data[a10 + 2].toInt() and 255) * w10 +
                    (data[a01 + 2].toInt() and 255) * w01 + (data[a11 + 2].toInt() and 255) * w11
            }
            val first = ((if (rgb) r else b) - mean) * scale
            val second = (g - mean) * scale
            val third = ((if (rgb) b else r) - mean) * scale
            if (layout == TensorLayout.Nchw) {
                val at = y * width + x
                output[at] = first
                output[plane + at] = second
                output[2 * plane + at] = third
            } else {
                val at = (y * width + x) * 3
                output[at] = first
                output[at + 1] = second
                output[at + 2] = third
            }
        }
    }
    return output
}

/** Greedy non-maximum suppression; returns kept indices, highest score first. */
internal fun nonMaximumSuppression(
    boxes: List<FloatArray>,
    scores: List<Float>,
    iouThreshold: Float,
    limit: Int,
): List<Int> {
    val kept = ArrayList<Int>()
    for (index in scores.indices.sortedByDescending { scores[it] }) {
        if (kept.size == limit) break
        if (kept.none { intersectionOverUnion(boxes[it], boxes[index]) > iouThreshold }) kept += index
    }
    return kept
}

/** Boxes are [left, top, right, bottom]. */
internal fun intersectionOverUnion(a: FloatArray, b: FloatArray): Float {
    val width = (minOf(a[2], b[2]) - maxOf(a[0], b[0])).coerceAtLeast(0f)
    val height = (minOf(a[3], b[3]) - maxOf(a[1], b[1])).coerceAtLeast(0f)
    val intersection = width * height
    val union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - intersection
    return if (union > 0) intersection / union else 0f
}
