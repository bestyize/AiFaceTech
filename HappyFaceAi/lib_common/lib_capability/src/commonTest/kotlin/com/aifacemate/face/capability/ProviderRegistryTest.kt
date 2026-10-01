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
        private val installed: Boolean = true,
    ) : Provider<C> {
        override val displayName = id
        override val description = ""
        override val models = emptyList<ModelSpec>()
        override fun availability(environment: DeviceEnvironment) = when {
            !supported -> Availability.Unsupported("fixture")
            installed -> Availability.Ready
            else -> Availability.NeedsModels(emptyList())
        }
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
    fun aDownloadedProviderIsUsedBeforeOneThatNeedsADownload() {
        // A phone that already has the large model keeps using it until the user picks another one.
        val registry = ProviderRegistry(listOf(
            FakeProvider("small", Greeter, installed = false), FakeProvider("medium", Greeter, installed = false),
            FakeProvider("large", Greeter),
        ), environment)
        assertEquals("large", registry.select(Greeter)?.id)
        assertEquals("small", registry.select(Greeter, preferredId = "small")?.id)
        // With nothing downloaded, registration order is the device default.
        val fresh = ProviderRegistry(listOf(
            FakeProvider("small", Greeter, installed = false), FakeProvider("large", Greeter, installed = false),
        ), environment)
        assertEquals("small", fresh.select(Greeter)?.id)
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
