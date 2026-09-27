package com.aifacemate.face.vision

/** RGBA8888, row-major, top-left origin; each channel is an unsigned byte. */
class RgbaFrame(val width: Int, val height: Int, val pixels: ByteArray) {
    init {
        require(width > 0 && height > 0 && width.toLong() * height * 4 <= Int.MAX_VALUE) { "Invalid image dimensions" }
        require(pixels.size == width * height * 4) { "RGBA byte count must equal width * height * 4" }
    }
}

/** Box coordinates are pixels in the original frame, not in the 640×640 model input. */
data class PixelBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class VisionDetection(
    val classId: Int,
    val confidence: Float,
    val box: PixelBox,
    val label: String = Coco80.names.getOrNull(classId) ?: "#$classId",
)

/** The caller owns the detector and must close it when the camera/image session ends. */
interface VisionDetector {
    /** Inference is synchronous and can be expensive; call on a worker thread. */
    fun detect(frame: RgbaFrame, confidenceThreshold: Float = 0.25f): List<VisionDetection>
    fun close()
}

/** Names in the standard COCO 80-class order used by the official yolo26m.pt detection weights. */
object Coco80 {
    val names = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
        "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
        "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed",
        "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven",
        "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush",
    )
}
