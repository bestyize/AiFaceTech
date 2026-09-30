@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.aifacemate.face.camera

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Photos.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual suspend fun CameraFrame.savePhoto(): CapturedPhoto = withContext(Dispatchers.Default) {
    val authorized = suspendCancellableCoroutine<Boolean> { continuation ->
        PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
            if (continuation.isActive) continuation.resume(status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited)
        }
    }
    check(authorized) { "请在 iOS 设置中允许添加照片" }
    val image = Image.makeRaster(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), rgba, width * 4)
    val bytes: ByteArray = try {
        val encoded = checkNotNull(image.encodeToData(EncodedImageFormat.JPEG, 95)) { "照片编码失败" }
        try { encoded.bytes } finally { encoded.close() }
    } finally { image.close() }
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    suspendCancellableCoroutine { continuation ->
        PHPhotoLibrary.sharedPhotoLibrary().performChanges({
            PHAssetCreationRequest.creationRequestForAsset().addResourceWithType(PHAssetResourceTypePhoto, data, null)
        }) { success, error ->
            if (continuation.isActive) {
                if (success) continuation.resume(CapturedPhoto("系统相册"))
                else continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "照片保存失败"))
            }
        }
    }
}
