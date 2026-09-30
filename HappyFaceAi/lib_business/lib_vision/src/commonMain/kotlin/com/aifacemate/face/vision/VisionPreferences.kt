package com.aifacemate.face.vision

import com.aifacemate.face.data.SettingsStore

/** Saved configuration only; enabling a preference does not start camera capture or inference. */
data class VisionPreferences(
    val yoloEnabled: Boolean = false,
    val yoloModelPath: String = "",
    val yoloConfidenceThreshold: Float = 0.25f,
    val expressionEnabled: Boolean = false,
    val expressionModelPath: String = "",
    val expressionConfidenceThreshold: Float = 0.60f,
    val gestureEnabled: Boolean = false,
    val gestureModelPath: String = "",
    val gestureConfidenceThreshold: Float = 0.70f,
) {
    fun normalized() = copy(
        yoloModelPath = yoloModelPath.trim(),
        expressionModelPath = expressionModelPath.trim(),
        gestureModelPath = gestureModelPath.trim(),
        yoloConfidenceThreshold = validThreshold(yoloConfidenceThreshold, 0.25f),
        expressionConfidenceThreshold = validThreshold(expressionConfidenceThreshold, 0.60f),
        gestureConfidenceThreshold = validThreshold(gestureConfidenceThreshold, 0.70f),
    )

    fun modelPathError(expression: Boolean, gesture: Boolean = false): String? {
        val path = when {
            gesture -> gestureModelPath.trim()
            expression -> expressionModelPath.trim()
            else -> yoloModelPath.trim()
        }
        return when {
            path.isNotEmpty() && !path.endsWith(".onnx", ignoreCase = true) -> "模型文件须使用 .onnx 扩展名"
            else -> null
        }
    }
}

private fun validThreshold(value: Float, fallback: Float): Float =
    value.takeIf { it.isFinite() && it in 0f..1f } ?: fallback

/** Vision settings use their own namespace, separate from face rendering and AI capabilities. */
object VisionPreferencesStore {
    private const val PREFIX = "vision."

    fun load() = VisionPreferences(
        yoloEnabled = SettingsStore.get(PREFIX + "yolo.enabled")?.toBooleanStrictOrNull() ?: false,
        yoloModelPath = SettingsStore.get(PREFIX + "yolo.modelPath").orEmpty(),
        yoloConfidenceThreshold = SettingsStore.get(PREFIX + "yolo.confidence")?.toFloatOrNull() ?: 0.25f,
        expressionEnabled = SettingsStore.get(PREFIX + "expression.enabled")?.toBooleanStrictOrNull() ?: false,
        expressionModelPath = SettingsStore.get(PREFIX + "expression.modelPath").orEmpty(),
        expressionConfidenceThreshold = SettingsStore.get(PREFIX + "expression.confidence")?.toFloatOrNull() ?: 0.60f,
        gestureEnabled = SettingsStore.get(PREFIX + "gesture.enabled")?.toBooleanStrictOrNull() ?: false,
        gestureModelPath = SettingsStore.get(PREFIX + "gesture.modelPath").orEmpty(),
        gestureConfidenceThreshold = SettingsStore.get(PREFIX + "gesture.confidence")?.toFloatOrNull() ?: 0.70f,
    ).normalized()

    fun save(value: VisionPreferences) {
        val config = value.normalized()
        require(config.modelPathError(false) == null && config.modelPathError(true) == null &&
            config.modelPathError(false, gesture = true) == null) {
            "Invalid vision model path"
        }
        val fields = mapOf(
            "yolo.enabled" to config.yoloEnabled.toString(),
            "yolo.modelPath" to config.yoloModelPath,
            "yolo.confidence" to config.yoloConfidenceThreshold.toString(),
            "expression.enabled" to config.expressionEnabled.toString(),
            "expression.modelPath" to config.expressionModelPath,
            "expression.confidence" to config.expressionConfidenceThreshold.toString(),
            "gesture.enabled" to config.gestureEnabled.toString(),
            "gesture.modelPath" to config.gestureModelPath,
            "gesture.confidence" to config.gestureConfidenceThreshold.toString(),
        )
        fields.forEach { (key, text) -> SettingsStore.put(PREFIX + key, text) }
    }
}
