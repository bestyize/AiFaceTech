package com.aifacemate.face

import com.aifacemate.face.vision.*
import kotlin.test.*

class LivingPerceptionAdapterTest {
    @Test fun classifiedGestureIsNotRejectedByAnExtraFacePageThreshold() {
        val hand = DetectedHand(NormalizedBox(0f, 0f, 1f, 1f), .6f, emptyList())
        val state = PerceptionState(observation = VisionObservation(640, 480,
            hands = listOf(HandObservation(hand, HandGesture.ThumbsUp))), sampledAtMillis = 100)
        val event = assertNotNull(state.toBehaviorEvent(persons = false, gestures = true))
        assertEquals(true, event.thumbsUp)
        assertNull(event.personVisible)
        assertEquals(100L, event.capturedAtMillis)
    }

    @Test fun objectOnlyResultDoesNotSignalAHandDisappeared() {
        val person = DetectedObject("person", 0, NormalizedBox(0f, 0f, 1f, 1f), .65f)
        val state = PerceptionState(observation = VisionObservation(640, 480, objects = listOf(person)), sampledAtMillis = 100)
        val event = assertNotNull(state.toBehaviorEvent(persons = true, gestures = false))
        assertEquals(true, event.personVisible)
        assertNull(event.thumbsUp)
    }

    @Test fun uninitializedPerceptionDoesNotProduceEvidence() {
        assertNull(PerceptionState().toBehaviorEvent(true, true))
        assertNull(PerceptionState(observation = VisionObservation(640, 480)).toBehaviorEvent(true, true))
    }
}
