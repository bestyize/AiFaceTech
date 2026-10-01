package com.aifacemate.face.capability

/**
 * The providers packaged for this device, in preference order. A provider id saved in settings wins; otherwise
 * a provider that is ready (models already downloaded) is used before one that would need a download, and
 * registration order breaks ties, so the first registered provider is the device default for new installs.
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

    fun <C : AutoCloseable> select(capability: CapabilityKey<C>, preferredId: String? = null): Provider<C>? {
        val candidates = providers(capability)
        return candidates.firstOrNull { it.id == preferredId }
            ?: candidates.firstOrNull { it.availability(environment) == Availability.Ready }
            ?: candidates.firstOrNull { it.availability(environment) !is Availability.Unsupported }
            ?: candidates.firstOrNull()
    }

    fun availability(provider: Provider<*>): Availability = provider.availability(environment)
}
