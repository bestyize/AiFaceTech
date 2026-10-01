package com.aifacemate.face.vision

import com.aifacemate.face.capability.FileModelStore
import com.aifacemate.face.capability.ProviderRegistry
import com.aifacemate.face.capability.currentDeviceEnvironment
import com.aifacemate.face.capability.openHttpsDownload
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.time.TimeSource
import kotlinx.coroutines.runBlocking

/** MediaPipe sample photos, pinned by hash and downloaded at check time rather than committed. */
private val samples = listOf(
    Triple("thumb_up.jpg", "5d673c081ab13b8a1812269ff57047066f9c33c07db5f4178089e8cb3fdc0291", HandGesture.ThumbsUp),
    Triple("victory.jpg", "84cb8853e3df614e0cb5c93a25e3e2f38ea5e4f92fd428ee7d867ed3479d5764", HandGesture.Victory),
    Triple("fist.jpg", "43fa1cabf3f90d574accc9a56986e2ee48638ce59fc65af1846487f73bb2ef24", HandGesture.Fist),
    Triple("pointing_up.jpg", "ecf8ca2611d08fa25948a4fc10710af9120e88243a54da6356bacea17ff3e36e", HandGesture.Pointing),
)
private const val portraitSha = "a6f11efaa834706db23f275b6115058fa87fc7f14362681e6abe14e82749de3e"

/** Explicit network check: real downloads, SHA-256 verification and ONNX Runtime inference, isolated from user models. */
fun main(args: Array<String>) = runBlocking {
    require(args.size == 1) { "Pass an isolated verification directory" }
    val root = File(args.single())
    val store = FileModelStore({ File(root, "models") })
    val registry = ProviderRegistry(onnxVisionProviders(store), currentDeviceEnvironment())
    val models = Yolo26Variant.entries.map { it.model } + listOf(VisionModels.yunet, VisionModels.mobileFaceNetExpression,
        VisionModels.palmDetection, VisionModels.handLandmarks)
    var bucket = -1
    store.install(models) { done, total ->
        val percent = (done * 100 / total).toInt()
        if (percent / 20 != bucket) { bucket = percent / 20; println("Download $percent%") }
    }
    models.forEach { store.verifiedPath(it) }
    println("PASS pinned models downloaded and verified")

    val preferences = VisionPreferences(yoloEnabled = true, expressionEnabled = true, gestureEnabled = true)
    val pipeline = PerceptionPipeline(registry, preferences)
    try {
        check(VisionTask.entries.all { pipeline.status[it] == VisionTaskStatus.Running }) { "Tasks not ready: ${pipeline.status}" }
        for ((name, sha, gesture) in samples) {
            val observation = pipeline.analyze(sample(root, name, sha))
            val found = observation.hands.mapNotNull { it.gesture }
            check(gesture in found) { "$name: expected $gesture, found $found" }
            println("PASS $name → ${gesture.id}")
        }
        val portrait = pipeline.analyze(sample(root, "portrait.jpg", portraitSha))
        check(portrait.faces.any { it.expression?.expression == Expression.Happy }) { "portrait: ${portrait.faces}" }
        check(portrait.objects.any { it.label == "person" }) { "portrait: ${portrait.objects}" }
        println("PASS portrait.jpg → happy face, person")

        val camera = RgbaFrame(640, 480, ByteArray(640 * 480 * 4) { (it * 31 % 251).toByte() })
        repeat(3) { pipeline.analyze(camera) }
        val started = TimeSource.Monotonic.markNow()
        repeat(5) { pipeline.analyze(camera) }
        val detector = registry.select(ObjectDetector)!!.displayName
        println("PASS 640×480 frame with all three tasks ($detector): ${started.elapsedNow().inWholeMilliseconds / 5} ms on this machine")
        check(VisionTask.entries.all { pipeline.status[it] == VisionTaskStatus.Running }) { "A task failed: ${pipeline.status}" }
    } finally {
        pipeline.close()
    }

    // Each detector size on the same portrait and frame, to choose a default for a new device.
    val portrait = sample(root, "portrait.jpg", portraitSha)
    val camera = RgbaFrame(640, 480, ByteArray(640 * 480 * 4) { (it * 31 % 251).toByte() })
    for (provider in registry.providers(ObjectDetector)) {
        provider.open().use { detector ->
            val person = detector.detect(portrait, 0.25f).filter { it.label == "person" }.maxOfOrNull { it.score }
            check(person != null) { "${provider.displayName} missed the person in portrait.jpg" }
            repeat(3) { detector.detect(camera, 0.25f) }
            val started = TimeSource.Monotonic.markNow()
            repeat(5) { detector.detect(camera, 0.25f) }
            println("PASS ${provider.displayName}: person ${(person * 100).toInt()}%, ${started.elapsedNow().inWholeMilliseconds / 5} ms per 640×480 frame")
        }
    }
}

private fun sample(root: File, name: String, sha256: String): RgbaFrame {
    val file = File(root, "samples/$name")
    if (!file.isFile) {
        file.parentFile.mkdirs()
        openHttpsDownload("https://storage.googleapis.com/mediapipe-assets/$name").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    check(digest == sha256) { "$name changed upstream (SHA-256 $digest)" }
    val image = ImageIO.read(file)
    val argb = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    val rgba = ByteArray(argb.size * 4)
    argb.forEachIndexed { index, pixel ->
        rgba[index * 4] = (pixel ushr 16).toByte()
        rgba[index * 4 + 1] = (pixel ushr 8).toByte()
        rgba[index * 4 + 2] = pixel.toByte()
        rgba[index * 4 + 3] = -1
    }
    return RgbaFrame(image.width, image.height, rgba)
}
