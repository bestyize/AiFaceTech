package com.aifacemate.face.capability

import kotlin.test.*

class ProviderRegistryTest {
    private interface Greeter : AutoCloseable {
        companion object : CapabilityKey<Greeter>("test.greeter")
    }
    private interface Counter : AutoCloseable {
        companion object : CapabilityKey<Counter>("test.counter")
    }

    private class FakeProvider<C : AutoCloseable>(
        override val id: String,
        override val capability: CapabilityKey<C>,
        private val supported: Boolean = true,
    ) : Provider<C> {
        override val displayName = id
        override val description = ""
        override val models = emptyList<ModelSpec>()
        override fun availability(environment: DeviceEnvironment) =
            if (supported) Availability.Ready else Availability.Unsupported("fixture")
        override suspend fun open(): C = error("not opened in this test")
    }

    private val environment = DeviceEnvironment(DevicePlatform.Desktop, OperatingSystem.Linux, CpuArchitecture.Arm64)

    @Test
    fun providersAreFilteredByCapabilityInRegistrationOrder() {
        val registry = ProviderRegistry(listOf(
            FakeProvider("a", Greeter), FakeProvider("count", Counter), FakeProvider("b", Greeter),
        ), environment)
        assertEquals(listOf("a", "b"), registry.providers(Greeter).map { it.id })
        assertEquals(listOf("count"), registry.providers(Counter).map { it.id })
    }

    @Test
    fun selectionPrefersSavedIdThenFirstSupportedProvider() {
        val registry = ProviderRegistry(listOf(
            FakeProvider("npu", Greeter, supported = false), FakeProvider("cpu", Greeter), FakeProvider("cloud", Greeter),
        ), environment)
        assertEquals("cpu", registry.select(Greeter)?.id)
        assertEquals("cloud", registry.select(Greeter, preferredId = "cloud")?.id)
        // A saved id may be unsupported on this device; it is still honoured so the UI can explain why.
        assertEquals("npu", registry.select(Greeter, preferredId = "npu")?.id)
        assertEquals("cpu", registry.select(Greeter, preferredId = "removed-provider")?.id)
        assertNull(registry.select(Counter))
    }

    @Test
    fun duplicateIdsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            ProviderRegistry(listOf(FakeProvider("same", Greeter), FakeProvider("same", Counter)), environment)
        }
    }

    @Test
    fun sizesAreFormattedForSettings() {
        assertEquals("约 5.0 MB", formatModelSize(5_024_481))
        assertEquals("约 8.0 MB", formatModelSize(8_005_355))
        assertEquals("约 82 MB", formatModelSize(81_963_205))
    }
}
