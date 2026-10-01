package com.aifacemate.face.vision

import com.aifacemate.face.capability.CpuArchitecture
import com.aifacemate.face.capability.DeviceEnvironment
import com.aifacemate.face.capability.DevicePlatform
import com.aifacemate.face.capability.FileModelStore
import com.aifacemate.face.capability.OperatingSystem
import com.aifacemate.face.capability.ProviderRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class OnnxDevicePolicyTest {
    private fun android(cores: Int) = DeviceEnvironment(DevicePlatform.Android, OperatingSystem.Android, CpuArchitecture.Arm64, cores)
    private fun ubuntuArm(cores: Int) = DeviceEnvironment(DevicePlatform.Desktop, OperatingSystem.Linux, CpuArchitecture.Arm64, cores)
    private fun mac(cores: Int) = DeviceEnvironment(DevicePlatform.Desktop, OperatingSystem.MacOs, CpuArchitecture.Arm64, cores)

    @Test
    fun everyPlatformGetsABoundedPool() {
        // RK3566 is a 4-core A55 board with either OS.
        assertEquals(OnnxThreading(2), onnxThreading(android(4)))
        assertEquals(OnnxThreading(2), onnxThreading(ubuntuArm(4)))
        assertEquals(OnnxThreading(2), onnxThreading(android(8)))
        assertEquals(OnnxThreading(4), onnxThreading(mac(10)))
        assertEquals(OnnxThreading(4), onnxThreading(mac(32)))
        assertEquals(OnnxThreading(1), onnxThreading(ubuntuArm(1)))
    }

    @Test
    fun theDefaultDetectorFollowsTheDeviceTier() {
        assertEquals(Yolo26Variant.Nano, yolo26Variants(android(4)).first())
        assertEquals(Yolo26Variant.Nano, yolo26Variants(ubuntuArm(4)).first())
        assertEquals(Yolo26Variant.Small, yolo26Variants(android(8)).first())
        assertEquals(Yolo26Variant.Medium, yolo26Variants(mac(10)).first())
        listOf(android(4), android(8), ubuntuArm(4), mac(10)).forEach { assertEquals(Yolo26Variant.entries.toSet(), yolo26Variants(it).toSet()) }
    }

    @Test
    fun registryUsesTheTierDefaultUntilAModelIsAlreadyDownloaded() {
        val root = kotlin.io.path.createTempDirectory("yolo-variants-").toFile()
        try {
            val store = FileModelStore({ root })
            val phone = android(8)
            val registry = ProviderRegistry(onnxVisionProviders(store, phone), phone)
            assertEquals(listOf("vision.objects.yolo26s-onnx", "vision.objects.yolo26n-onnx", "vision.objects.yolo26m-onnx"),
                registry.providers(ObjectDetector).map { it.id })
            assertEquals("vision.objects.yolo26s-onnx", registry.select(ObjectDetector)?.id)
            // An existing install with only the medium model keeps using it instead of asking for a new download.
            File(root, VisionModels.yolo26m.directory).mkdirs()
            File(File(root, VisionModels.yolo26m.directory), VisionModels.yolo26m.fileName).outputStream().use { output ->
                output.channel.truncate(0); output.channel.position(VisionModels.yolo26m.sizeBytes - 1); output.write(0)
            }
            assertEquals("vision.objects.yolo26m-onnx", registry.select(ObjectDetector)?.id)
            assertEquals("YOLO26m", registry.select(ObjectDetector)?.displayName)
        } finally {
            root.deleteRecursively()
        }
    }
}
