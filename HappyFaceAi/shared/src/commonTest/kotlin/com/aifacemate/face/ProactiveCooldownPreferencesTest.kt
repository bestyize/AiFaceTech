package com.aifacemate.face

import kotlin.test.*

class ProactiveCooldownPreferencesTest {
    @Test fun missingAndOutOfRangeSettingsAreNormalized() {
        assertEquals(15, normalizedProactiveCooldown(null))
        assertEquals(5, normalizedProactiveCooldown(-1))
        assertEquals(120, normalizedProactiveCooldown(Int.MAX_VALUE))
        assertEquals(30, normalizedProactiveCooldown(30))
    }
}
