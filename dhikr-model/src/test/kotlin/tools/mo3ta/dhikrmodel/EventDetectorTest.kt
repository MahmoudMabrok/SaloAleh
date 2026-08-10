package tools.mo3ta.dhikrmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDetectorTest {
    private val spec = DetectorSpec(
        activationThreshold = 0.7f,
        releaseThreshold = 0.4f,
        minConsecutiveHits = 2,
        minEventSeconds = 0.0,
        releaseWindows = 2,
        cooldownMs = 200.0,
        smoothing = SmoothingSpec("none", 0.5f, 1),
    )

    @Test
    fun sustainedActivationCountsOnlyOnce() {
        val detector = EventDetector(spec, windowSeconds = 2.0)

        assertFalse(detector.push(0.0, 0.8f).detected)
        assertTrue(detector.push(0.2, 0.9f).detected)
        assertFalse(detector.push(0.4, 0.95f).detected)
        assertFalse(detector.push(0.6, 0.8f).detected)
        assertEquals(DhikrDetectorState.CONFIRMED, detector.push(0.8, 0.7f).state)
    }

    @Test
    fun releaseAndCooldownRearmRapidRepetition() {
        val detector = EventDetector(spec, windowSeconds = 2.0)
        detector.push(0.0, 0.8f)
        assertTrue(detector.push(0.2, 0.8f).detected)
        detector.push(0.4, 0.1f)
        assertEquals(DhikrDetectorState.COOLDOWN, detector.push(0.6, 0.1f).state)

        // At the exact cooldown boundary this same window is reconsidered.
        assertEquals(DhikrDetectorState.CANDIDATE, detector.push(0.8, 0.8f).state)
        assertTrue(detector.push(1.0, 0.8f).detected)
    }

    @Test
    fun candidateInHysteresisBandDoesNotAddAHitOrCancel() {
        val detector = EventDetector(spec, windowSeconds = 2.0)
        detector.push(0.0, 0.8f)
        assertEquals(DhikrDetectorState.CANDIDATE, detector.push(0.2, 0.5f).state)
        assertTrue(detector.push(0.4, 0.8f).detected)
    }
}
