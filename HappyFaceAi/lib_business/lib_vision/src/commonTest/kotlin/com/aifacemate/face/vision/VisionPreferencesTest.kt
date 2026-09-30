package com.aifacemate.face.vision

import com.aifacemate.face.data.SettingsStore
import kotlin.test.*

class VisionPreferencesTest {
    private val retired = listOf("yolo", "expression", "gesture").map { "vision.$it.modelPath" }
    private val keys = (listOf("yolo", "expression", "gesture").flatMap { task -> listOf("enabled", "confidence").map { "vision.$task.$it" } } +
        VisionTask.entries.flatMap { it.capabilities }.map { "vision.provider.${it.id}" } + retired)
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
    fun tasksRoundTripIndependently() {
        val config = VisionPreferences(yoloEnabled = true, yoloConfidenceThreshold = 0.4f, expressionEnabled = true,
            expressionConfidenceThreshold = 0.8f)
        VisionPreferencesStore.save(config)
        assertEquals(config, VisionPreferencesStore.load())
        VisionPreferencesStore.save(VisionPreferencesStore.load().copy(yoloEnabled = false, yoloConfidenceThreshold = 0.1f))
        val loaded = VisionPreferencesStore.load()
        assertFalse(loaded.yoloEnabled)
        assertTrue(loaded.expressionEnabled)
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
    fun customModelPathsFromEarlierReleasesAreIgnoredAndRemovedOnSave() {
        retired.forEach { SettingsStore.put(it, "/models/custom.onnx") }
        SettingsStore.put("vision.yolo.enabled", "true")
        val loaded = VisionPreferencesStore.load()
        assertEquals(VisionPreferences(yoloEnabled = true), loaded)
        VisionPreferencesStore.save(loaded)
        retired.forEach { assertNull(SettingsStore.get(it)) }
    }

    @Test
    fun taskAccessorsAndProviderChoicesRoundTrip() {
        val config = VisionPreferences().update(VisionTask.Gestures, enabled = true, threshold = .8f)
            .withProvider(HandLandmarker.id, "vision.hands.rknn")
        assertTrue(config.enabled(VisionTask.Gestures))
        assertEquals(.8f, config.threshold(VisionTask.Gestures))
        VisionPreferencesStore.save(config)
        assertEquals(config, VisionPreferencesStore.load())
        assertEquals("vision.hands.rknn", VisionPreferencesStore.load().providerId(HandLandmarker))
        VisionPreferencesStore.save(config.copy(providerIds = emptyMap()))
        assertNull(VisionPreferencesStore.load().providerId(HandLandmarker))
    }
}
