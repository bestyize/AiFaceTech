package com.aifacemate.face.vision

/** RGBA8888, row-major, top-left origin; each channel is an unsigned byte. */
class RgbaFrame(val width: Int, val height: Int, val pixels: ByteArray, val sampledAtMillis: Long? = null) {
    init {
        require(width > 0 && height > 0 && width.toLong() * height * 4 <= Int.MAX_VALUE) { "Invalid image dimensions" }
        require(pixels.size == width * height * 4) { "RGBA byte count must equal width * height * 4" }
    }
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
