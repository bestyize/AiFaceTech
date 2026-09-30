package com.aifacemate.face.camera

import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

internal actual suspend fun CameraFrame.savePhoto(): CapturedPhoto = withContext(Dispatchers.IO) {
    val directory = File(System.getProperty("user.home"), "Pictures/HappyFaceAi")
    check(directory.isDirectory || directory.mkdirs()) { "无法创建照片目录" }
    val target = File(directory, "HappyFaceAi_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
    try {
        Image.makeRaster(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), rgba, width * 4).use { image ->
            checkNotNull(image.encodeToData(EncodedImageFormat.JPEG, 95)) { "照片编码失败" }.use { target.writeBytes(it.bytes) }
        }
        CapturedPhoto(target.absolutePath)
    } catch (failure: Throwable) {
        target.delete()
        throw failure
    }
}
