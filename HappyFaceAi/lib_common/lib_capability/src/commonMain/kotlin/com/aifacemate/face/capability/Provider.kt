package com.aifacemate.face.capability

/**
 * Identifies one capability contract, such as a face detector. Capability interfaces declare their key as a
 * companion object, so call sites read `registry.providers(FaceDetector)`.
 */
abstract class CapabilityKey<C : AutoCloseable>(val id: String) {
    override fun toString() = id
}

data class ProviderOptions(
    /** Replaces the provider's replaceable model with a user file. Ignored unless [Provider.acceptsCustomModel]. */
    val customModelPath: String? = null,
)

sealed interface Availability {
    data object Ready : Availability
    data class NeedsModels(val missing: List<ModelSpec>) : Availability
    data class Unsupported(val reason: String) : Availability
}

/**
 * One implementation of a capability. Each platform registers the providers it packages at compile time.
 * A provider only describes itself until [open] loads models or native resources.
 */
interface Provider<C : AutoCloseable> {
    /** Stable and persisted in settings, for example `vision.face.yunet-onnx`. */
    val id: String
    val capability: CapabilityKey<C>
    val displayName: String
    val description: String
    /** Managed files this provider needs; empty for platform built-ins such as Apple Vision. */
    val models: List<ModelSpec>
    /** Whether [ProviderOptions.customModelPath] can replace one of [models]. */
    val acceptsCustomModel: Boolean get() = false

    fun availability(environment: DeviceEnvironment, options: ProviderOptions = ProviderOptions()): Availability

    /** The caller owns the returned instance and closes it when its session ends. */
    suspend fun open(options: ProviderOptions = ProviderOptions()): C
}
