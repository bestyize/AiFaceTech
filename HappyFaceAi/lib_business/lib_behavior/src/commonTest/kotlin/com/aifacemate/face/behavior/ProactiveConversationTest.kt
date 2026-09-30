package com.aifacemate.face.behavior

import com.aifacemate.face.behavior.proactive.ProactiveConversationController
import kotlin.test.*

class ProactiveConversationTest {
    @Test fun endingLongConversationStartsANewQuietPeriod() {
        val controller = ProactiveConversationController()
        for (time in 0L..800L step 400) controller.observe(smile(time), true, time)
        assertNotNull(controller.observe(smile(1200), true, 1200))
        controller.conversationEnded(100000)
        controller.observe(BehaviorEvent.Vision(false, null, 100400, faceAnalyzed = true), true, 100400)
        for (time in 100800L..159600L step 400) assertNull(controller.observe(smile(time), true, time))
        for (time in 160000L..160800L step 400) assertNull(controller.observe(smile(time), true, time))
        assertNotNull(controller.observe(smile(161200), true, 161200))
    }
    private fun smile(time: Long) = BehaviorEvent.Vision(true, null, time, faceAnalyzed = true, appearance = FacialAppearance.Smile)
    @Test fun onlyStableVisibleSmileStartsOneConversation() {
        val controller = ProactiveConversationController()
        assertNull(controller.observe(smile(0), true, 0))
        assertNull(controller.observe(smile(400), true, 400))
        assertNull(controller.observe(smile(800), true, 800))
        assertTrue(assertNotNull(controller.observe(smile(1200), true, 1200)).text.contains("开心的事"))
        for (time in 1600L..65000L step 400) assertNull(controller.observe(smile(time), true, time))
    }
    @Test fun busyEvidenceIsNotQueuedAndCooldownRequiresRearming() {
        val controller = ProactiveConversationController()
        for (time in 0L..2000L step 400) assertNull(controller.observe(smile(time), false, time))
        for (time in 2400L..3200L step 400) assertNull(controller.observe(smile(time), true, time))
        assertNotNull(controller.observe(smile(3600), true, 3600))
        controller.observe(BehaviorEvent.Vision(false, null, 4000, faceAnalyzed = true), true, 4000)
        for (time in 4400L..63200L step 400) assertNull(controller.observe(smile(time), true, time))
        for (time in 63600L..64400L step 400) assertNull(controller.observe(smile(time), true, time))
        assertNotNull(controller.observe(smile(64800), true, 64800))
    }
    @Test fun staleMissingOrInterruptedFaceDoesNotTrigger() {
        val controller = ProactiveConversationController()
        assertNull(controller.observe(smile(0), true, 2000))
        assertNull(controller.observe(smile(4000), true, 3000))
        controller.observe(smile(4000), true, 4000)
        controller.observe(smile(4400), true, 4400)
        controller.resetEvidence()
        assertNull(controller.observe(smile(4800), true, 4800))
        assertNull(controller.observe(BehaviorEvent.Vision(null, null, 5200), true, 5200))
    }
}
