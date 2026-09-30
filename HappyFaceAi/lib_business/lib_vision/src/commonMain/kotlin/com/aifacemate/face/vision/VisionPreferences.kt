package com.aifacemate.face.vision

import com.aifacemate.face.capability.CapabilityKey
import com.aifacemate.face.capability.ProviderOptions
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
    /** Capability id → provider id chosen in settings. Missing entries use the device default. */
    val providerIds: Map<String, String> = emptyMap(),
) {
    fun enabled(task: VisionTask) = when (task) {
        VisionTask.Objects -> yoloEnabled
        VisionTask.Expressions -> expressionEnabled
        VisionTask.Gestures -> gestureEnabled
    }

    fun threshold(task: VisionTask) = when (task) {
        VisionTask.Objects -> yoloConfidenceThreshold
        VisionTask.Expressions -> expressionConfidenceThreshold
        VisionTask.Gestures -> gestureConfidenceThreshold
    }

    fun modelPath(task: VisionTask) = when (task) {
        VisionTask.Objects -> yoloModelPath
        VisionTask.Expressions -> expressionModelPath
        VisionTask.Gestures -> gestureModelPath
    }

    fun update(
        task: VisionTask,
        enabled: Boolean = enabled(task),
        threshold: Float = threshold(task),
        modelPath: String = modelPath(task),
    ) = when (task) {
        VisionTask.Objects -> copy(yoloEnabled = enabled, yoloConfidenceThreshold = threshold, yoloModelPath = modelPath)
        VisionTask.Expressions -> copy(expressionEnabled = enabled, expressionConfidenceThreshold = threshold, expressionModelPath = modelPath)
        VisionTask.Gestures -> copy(gestureEnabled = enabled, gestureConfidenceThreshold = threshold, gestureModelPath = modelPath)
    }

    fun providerId(capability: CapabilityKey<*>): String? = providerIds[capability.id]

    fun withProvider(capabilityId: String, providerId: String) = copy(providerIds = providerIds + (capabilityId to providerId))

    /** The custom model path, if any, goes to whichever provider of the task accepts one. */
    fun options(task: VisionTask) = ProviderOptions(modelPath(task).trim().ifEmpty { null })

    fun normalized() = copy(
        yoloModelPath = yoloModelPath.trim(),
        expressionModelPath = expressionModelPath.trim(),
        gestureModelPath = gestureModelPath.trim(),
        yoloConfidenceThreshold = validThreshold(yoloConfidenceThreshold, 0.25f),
        expressionConfidenceThreshold = validThreshold(expressionConfidenceThreshold, 0.60f),
        gestureConfidenceThreshold = validThreshold(gestureConfidenceThreshold, 0.70f),
    )

    fun modelPathError(task: VisionTask): String? {
        val path = modelPath(task).trim()
        return if (path.isNotEmpty() && !path.endsWith(".onnx", ignoreCase = true)) "模型文件须使用 .onnx 扩展名" else null
    }
}

private fun validThreshold(value: Float, fallback: Float): Float =
    value.takeIf { it.isFinite() && it in 0f..1f } ?: fallback

/** Vision settings use their own namespace, separate from face rendering and AI capabilities. */
object VisionPreferencesStore {
    private const val PREFIX = "vision."
    private const val PROVIDER_PREFIX = "vision.provider."

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
        providerIds = capabilityIds().mapNotNull { id ->
            SettingsStore.get(PROVIDER_PREFIX + id)?.takeIf { it.isNotBlank() }?.let { id to it }
        }.toMap(),
    ).normalized()

    fun save(value: VisionPreferences) {
        val config = value.normalized()
        require(VisionTask.entries.all { config.modelPathError(it) == null }) { "Invalid vision model path" }
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
        capabilityIds().forEach { id ->
            val provider = config.providerIds[id]
            if (provider == null) SettingsStore.remove(PROVIDER_PREFIX + id) else SettingsStore.put(PROVIDER_PREFIX + id, provider)
        }
    }

    private fun capabilityIds() = VisionTask.entries.flatMap { it.capabilities }.map { it.id }.distinct()
}
