package com.aifacemate.face.voice.realtime.provider.qwen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QwenResponseTrackerTest {
    @Test fun lateFramesCannotCloseOrPlayOverNextResponse() {
        val tracker = QwenResponseTracker()
        tracker.start("old")
        assertTrue(tracker.interrupt())
        tracker.start("new")
        assertFalse(tracker.accepts("old"))
        assertFalse(tracker.finish("old"))
        assertTrue(tracker.accepts("new"))
        assertTrue(tracker.finish("new"))
        assertFalse(tracker.accepts("new"))
    }
}
