package com.aifacemate.face.vision

import com.aifacemate.face.data.SettingsStore
import kotlin.test.*

class VisionPreferencesTest {
    private val keys = (listOf("yolo", "expression", "gesture").flatMap { task -> listOf("enabled", "modelPath", "confidence").map { "$task.$it" } } +
        VisionTask.entries.flatMap { it.capabilities }.map { "provider.${it.id}" }).map { "vision.$it" }
    private var original = emptyMap<String, String?>()

    @BeforeTest
    fun setUp() {
        original = keys.associateWith { SettingsStore.get(it) }
        keys.forEach(SettingsStore::remove)
    }

    @AfterTest
    fun tearDown() {
        original.forEach { (key, value) ->
            if (value == null) SettingsStore.remove(key) else SettingsStore.put(key, value)
        }
    }

    @Test
    fun defaultsDoNotEnableRecognition() {
        assertEquals(VisionPreferences(), VisionPreferencesStore.load())
    }

    @Test
    fun bothConfigurationsRoundTripIndependently() {
        val config = VisionPreferences(true, " C:\\models\\yolo26m.onnx ", 0.4f, true, "/models/expression.ONNX", 0.8f)
        VisionPreferencesStore.save(config)
        assertEquals(config.copy(yoloModelPath = "C:\\models\\yolo26m.onnx"), VisionPreferencesStore.load())
        VisionPreferencesStore.save(VisionPreferencesStore.load().copy(yoloEnabled = false, yoloConfidenceThreshold = 0.1f))
        val loaded = VisionPreferencesStore.load()
        assertFalse(loaded.yoloEnabled)
        assertTrue(loaded.expressionEnabled)
        assertEquals(config.expressionModelPath, loaded.expressionModelPath)
        assertEquals(config.expressionConfidenceThreshold, loaded.expressionConfidenceThreshold)
    }

    @Test
    fun corruptedThresholdsAndFlagsFallBackSafely() {
        SettingsStore.put("vision.yolo.enabled", "invalid")
        SettingsStore.put("vision.yolo.confidence", "NaN")
        SettingsStore.put("vision.expression.confidence", "Infinity")
        assertEquals(VisionPreferences(), VisionPreferencesStore.load())
        SettingsStore.put("vision.yolo.confidence", "-0.1")
        SettingsStore.put("vision.expression.confidence", "1.1")
        assertEquals(VisionPreferences(), VisionPreferencesStore.load())
    }

    @Test
    fun missingOrWrongModelPathsCannotBeSaved() {
        assertFailsWith<IllegalArgumentException> { VisionPreferencesStore.save(VisionPreferences(expressionModelPath = "face.tflite")) }
        assertFailsWith<IllegalArgumentException> { VisionPreferencesStore.save(VisionPreferences(gestureModelPath = "hand.task")) }
        assertFailsWith<IllegalArgumentException> { VisionPreferencesStore.save(VisionPreferences(yoloModelPath = "yolo26m.pt")) }
        assertEquals(VisionPreferences(), VisionPreferencesStore.load())
    }

    @Test
    fun taskAccessorsAndProviderChoicesRoundTrip() {
        val config = VisionPreferences().update(VisionTask.Gestures, enabled = true, threshold = .8f, modelPath = "/models/hand.onnx")
            .withProvider(HandLandmarker.id, "vision.hands.rknn")
        assertTrue(config.enabled(VisionTask.Gestures))
        assertEquals(.8f, config.threshold(VisionTask.Gestures))
        assertEquals("/models/hand.onnx", config.options(VisionTask.Gestures).customModelPath)
        assertNull(config.options(VisionTask.Objects).customModelPath)
        VisionPreferencesStore.save(config)
        assertEquals(config, VisionPreferencesStore.load())
        assertEquals("vision.hands.rknn", VisionPreferencesStore.load().providerId(HandLandmarker))
        VisionPreferencesStore.save(config.copy(providerIds = emptyMap()))
        assertNull(VisionPreferencesStore.load().providerId(HandLandmarker))
    }

    @Test
    fun emptyYoloPathSelectsTheManagedModel() {
        val config = VisionPreferences(yoloEnabled = true)
        VisionPreferencesStore.save(config)
        assertEquals(config, VisionPreferencesStore.load())
    }
}
