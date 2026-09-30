package com.aifacemate.face.camera

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.aifacemate.face.data.AndroidAppContext
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual suspend fun CameraFrame.savePhoto(): CapturedPhoto = withContext(Dispatchers.IO) {
    val context = checkNotNull(AndroidAppContext.context()) { "Android Context 未初始化" }
    val name = "HappyFaceAi_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
    val bitmap = toAndroidBitmap()
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/HappyFaceAi")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) { "无法创建相册照片" }
            try {
                checkNotNull(resolver.openOutputStream(uri)).use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) { "照片编码失败" }
                }
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0) { "无法完成相册照片保存" }
                CapturedPhoto("相册 / HappyFaceAi")
            } catch (failure: Throwable) {
                resolver.delete(uri, null, null)
                throw failure
            }
        } else {
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "HappyFaceAi")
            check(directory.isDirectory || directory.mkdirs()) { "无法创建照片目录" }
            val target = File(directory, name)
            try {
                target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "照片编码失败" } }
                CapturedPhoto(target.absolutePath)
            } catch (failure: Throwable) {
                target.delete()
                throw failure
            }
        }
    } finally { bitmap.recycle() }
}
