package com.aifacemate.face.camera

/** Pure conversions keep native stride/rotation handling consistent and independently testable. */
object CameraPixels {
    data class Plane(val bytes: ByteArray, val rowStride: Int, val pixelStride: Int = 1)

    fun yuv420(width: Int, height: Int, y: Plane, u: Plane, v: Plane): ByteArray {
        require(width > 0 && height > 0 && width.toLong() * height * 4 <= Int.MAX_VALUE)
        val result = ByteArray(width * height * 4)
        for (row in 0 until height) for (col in 0 until width) {
            val yy = (y.bytes[row * y.rowStride + col * y.pixelStride].toInt() and 255) - 16
            val uu = (u.bytes[(row / 2) * u.rowStride + (col / 2) * u.pixelStride].toInt() and 255) - 128
            val vv = (v.bytes[(row / 2) * v.rowStride + (col / 2) * v.pixelStride].toInt() and 255) - 128
            val luminance = 298 * yy.coerceAtLeast(0)
            val at = (row * width + col) * 4
            result[at] = ((luminance + 409 * vv + 128) shr 8).coerceIn(0, 255).toByte()
            result[at + 1] = ((luminance - 100 * uu - 208 * vv + 128) shr 8).coerceIn(0, 255).toByte()
            result[at + 2] = ((luminance + 516 * uu + 128) shr 8).coerceIn(0, 255).toByte()
            result[at + 3] = -1
        }
        return result
    }

    fun bgra(width: Int, height: Int, bytes: ByteArray, rowStride: Int): ByteArray {
        require(width > 0 && height > 0 && width.toLong() * height * 4 <= Int.MAX_VALUE)
        require(rowStride >= width * 4 && bytes.size.toLong() >= rowStride.toLong() * height)
        val result = ByteArray(width * height * 4)
        for (row in 0 until height) for (col in 0 until width) {
            val input = row * rowStride + col * 4
            val output = (row * width + col) * 4
            result[output] = bytes[input + 2]
            result[output + 1] = bytes[input + 1]
            result[output + 2] = bytes[input]
            result[output + 3] = -1
        }
        return result
    }

    fun upright(width: Int, height: Int, rgba: ByteArray, rotation: Int, sequence: Long, mirrored: Boolean): CameraFrame {
        require(rotation in listOf(0, 90, 180, 270))
        require(rgba.size.toLong() == width.toLong() * height * 4)
        if (rotation == 0) return CameraFrame(width, height, rgba, sequence, mirrored)
        val rotatedWidth = if (rotation == 180) width else height
        val rotatedHeight = if (rotation == 180) height else width
        val result = ByteArray(rgba.size)
        for (y in 0 until height) for (x in 0 until width) {
            val xx = when (rotation) { 90 -> height - 1 - y; 180 -> width - 1 - x; else -> y }
            val yy = when (rotation) { 90 -> x; 180 -> height - 1 - y; else -> width - 1 - x }
            val source = (y * width + x) * 4
            val destination = (yy * rotatedWidth + xx) * 4
            rgba.copyInto(result, destination, source, source + 4)
        }
        return CameraFrame(rotatedWidth, rotatedHeight, result, sequence, mirrored)
    }
}

/** Fit-preview coordinates, including letterboxing and front-camera display mirroring. */
data class CameraPreviewTransform(val scale: Float, val left: Float, val top: Float, val sourceWidth: Int, val mirrored: Boolean) {
    fun x(value: Float): Float = left + (if (mirrored) sourceWidth - value else value) * scale
    fun y(value: Float): Float = top + value * scale
    fun focusPoint(x: Float, y: Float, sourceHeight: Int): CameraFocusPoint {
        require(scale > 0 && sourceHeight > 0 && x.isFinite() && y.isFinite())
        val normalizedX = (x - left) / scale / sourceWidth
        return CameraFocusPoint((if (mirrored) 1f - normalizedX else normalizedX).coerceIn(0f, 1f),
            ((y - top) / scale / sourceHeight).coerceIn(0f, 1f))
    }

    companion object {
        fun crop(sourceWidth: Int, sourceHeight: Int, width: Float, height: Float, mirrored: Boolean): CameraPreviewTransform {
            require(sourceWidth > 0 && sourceHeight > 0 && width >= 0 && height >= 0)
            val scale = maxOf(width / sourceWidth, height / sourceHeight)
            return CameraPreviewTransform(scale, (width - sourceWidth * scale) / 2, (height - sourceHeight * scale) / 2, sourceWidth, mirrored)
        }
        fun fit(sourceWidth: Int, sourceHeight: Int, width: Float, height: Float, mirrored: Boolean): CameraPreviewTransform {
            require(sourceWidth > 0 && sourceHeight > 0 && width >= 0 && height >= 0)
            val scale = minOf(width / sourceWidth, height / sourceHeight)
            return CameraPreviewTransform(scale, (width - sourceWidth * scale) / 2, (height - sourceHeight * scale) / 2, sourceWidth, mirrored)
        }
    }
}

/** Inverse of the clockwise rotation applied to a native surface to produce the upright frame. */
internal fun CameraFocusPoint.toSurface(rotation: Int): CameraFocusPoint = when (rotation) {
    0 -> this
    90 -> CameraFocusPoint(y, 1f - x)
    180 -> CameraFocusPoint(1f - x, 1f - y)
    270 -> CameraFocusPoint(1f - y, x)
    else -> error("Unsupported camera rotation: $rotation")
}

/** Photo pixels match the centre-cropped full-screen preview, including selfie mirroring. */
internal fun CameraFrame.photoFrame(aspectRatio: Float?): CameraFrame {
    val aspect = aspectRatio?.takeIf { it.isFinite() && it > 0 } ?: width.toFloat() / height
    val cropWidth = minOf(width, (height * aspect).toInt().coerceAtLeast(1))
    val cropHeight = minOf(height, (width / aspect).toInt().coerceAtLeast(1))
    val left = (width - cropWidth) / 2
    val top = (height - cropHeight) / 2
    val pixels = ByteArray(cropWidth * cropHeight * 4)
    for (y in 0 until cropHeight) for (x in 0 until cropWidth) {
        val sourceX = left + if (mirrored) cropWidth - 1 - x else x
        val source = ((top + y) * width + sourceX) * 4
        rgba.copyInto(pixels, (y * cropWidth + x) * 4, source, source + 4)
    }
    return CameraFrame(cropWidth, cropHeight, pixels, sequence)
}

/** Desktop fallback when a webcam exposes no portable optical-zoom control. */
internal fun CameraFrame.digitalZoom(ratio: Float): CameraFrame {
    val zoom = ratio.coerceIn(1f, 4f)
    if (zoom == 1f) return this
    val cropWidth = (width / zoom).toInt().coerceAtLeast(1)
    val cropHeight = (height / zoom).toInt().coerceAtLeast(1)
    val left = (width - cropWidth) / 2
    val top = (height - cropHeight) / 2
    val pixels = ByteArray(rgba.size)
    for (y in 0 until height) for (x in 0 until width) {
        val source = ((top + y * cropHeight / height) * width + left + x * cropWidth / width) * 4
        rgba.copyInto(pixels, (y * width + x) * 4, source, source + 4)
    }
    return CameraFrame(width, height, pixels, sequence, mirrored)
}
