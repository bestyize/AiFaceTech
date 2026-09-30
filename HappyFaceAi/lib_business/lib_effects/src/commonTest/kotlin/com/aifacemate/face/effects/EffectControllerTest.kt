package com.aifacemate.face.effects

import kotlin.test.*

class EffectControllerTest {
    @Test fun expiryCancellationAndDuplicatesNeverQueueAReplay() {
        val controller = EffectController()
        val scene = EffectScene("one", EffectId.Fireworks, 0)
        assertTrue(controller.play(scene, 0))
        assertFalse(controller.play(scene, 100))
        assertNotNull(controller.sceneAt(3599))
        assertNull(controller.sceneAt(3600))
        assertFalse(controller.play(scene, 3600))
        val next = EffectScene("two", EffectId.Hearts, 4000)
        assertTrue(controller.play(next, 4000))
        controller.clear()
        assertNull(controller.sceneAt(4100))
        assertFalse(controller.play(next, 4100))
    }
    @Test fun lowerPriorityIsDroppedAndNewHigherPriorityReplaces() {
        val controller = EffectController()
        val comfort = EffectScene("comfort", EffectId.RainToSun, 0, 20)
        assertTrue(controller.play(comfort, 0))
        val reward = EffectScene("reward", EffectId.StarReward, 100, 10)
        assertFalse(controller.play(reward, 100))
        assertEquals(comfort, controller.sceneAt(100))
        val love = EffectScene("love", EffectId.Hearts, 200, 30)
        assertTrue(controller.play(love, 200))
        assertEquals(love, controller.sceneAt(200))
        controller.clear()
        assertFalse(controller.play(reward, 300))
    }
    @Test fun staleFutureAndSameSceneCannotRestartAnimation() {
        val controller = EffectController()
        assertFalse(controller.play(EffectScene("future", EffectId.Hearts, 100), 0))
        assertFalse(controller.play(EffectScene("stale", EffectId.Hearts, 0), 3000))
        val active = EffectScene("active", EffectId.Hearts, 4000)
        assertTrue(controller.play(active, 4000))
        assertFalse(controller.play(EffectScene("repeat", EffectId.Hearts, 4200), 4200))
        assertEquals(4000L, controller.sceneAt(4200)?.startedAtMillis)
        assertEquals(0f, active.progress(3000))
        assertEquals(1f, active.progress(8000))
    }
}
