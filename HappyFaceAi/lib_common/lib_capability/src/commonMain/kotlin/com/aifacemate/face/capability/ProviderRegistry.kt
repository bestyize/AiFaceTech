package com.aifacemate.face.capability

/**
 * The providers packaged for this device, in preference order. Registration order is the device default;
 * a provider id saved in settings overrides it.
 */
class ProviderRegistry(providers: List<Provider<*>>, val environment: DeviceEnvironment) {
    private val all = providers.toList()

    init {
        val duplicate = all.groupBy { it.id }.entries.firstOrNull { it.value.size > 1 }?.key
        require(duplicate == null) { "Duplicate provider id: $duplicate" }
    }

    fun <C : AutoCloseable> providers(capability: CapabilityKey<C>): List<Provider<C>> {
        @Suppress("UNCHECKED_CAST")
        return all.filter { it.capability == capability } as List<Provider<C>>
    }

    /** The preferred provider if registered, otherwise the first one this device supports. */
    fun <C : AutoCloseable> select(capability: CapabilityKey<C>, preferredId: String? = null): Provider<C>? {
        val candidates = providers(capability)
        return candidates.firstOrNull { it.id == preferredId }
            ?: candidates.firstOrNull { it.availability(environment) !is Availability.Unsupported }
            ?: candidates.firstOrNull()
    }

    fun availability(provider: Provider<*>): Availability = provider.availability(environment)
}
