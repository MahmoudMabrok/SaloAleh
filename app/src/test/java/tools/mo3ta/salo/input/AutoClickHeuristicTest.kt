package tools.mo3ta.salo.input

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cases are the fingerprints actually measured on an Android 16 device with a real auto-clicker
 * running against real finger taps — see [AutoClickHeuristic] for the capture.
 */
class AutoClickHeuristicTest {

    @Test
    fun `real finger tap is allowed`() {
        assertFalse(
            AutoClickHeuristic.isSynthetic(
                isTouchscreen = true,
                deviceId = 3,
                isVirtualDevice = false,
                flags = 0x0,
            )
        )
    }

    @Test
    fun `injected auto-clicker tap is rejected`() {
        assertTrue(
            AutoClickHeuristic.isSynthetic(
                isTouchscreen = true,
                deviceId = -1,
                isVirtualDevice = true,
                flags = AutoClickHeuristic.FLAG_IS_ACCESSIBILITY_EVENT,
            )
        )
    }

    @Test
    fun `negative device id alone is enough`() {
        assertTrue(
            AutoClickHeuristic.isSynthetic(
                isTouchscreen = true,
                deviceId = -1,
                isVirtualDevice = false,
                flags = 0x0,
            )
        )
    }

    @Test
    fun `virtual device alone is enough`() {
        assertTrue(
            AutoClickHeuristic.isSynthetic(
                isTouchscreen = true,
                deviceId = 7,
                isVirtualDevice = true,
                flags = 0x0,
            )
        )
    }

    @Test
    fun `accessibility flag alone is enough`() {
        assertTrue(
            AutoClickHeuristic.isSynthetic(
                isTouchscreen = true,
                deviceId = 7,
                isVirtualDevice = false,
                flags = AutoClickHeuristic.FLAG_IS_ACCESSIBILITY_EVENT,
            )
        )
    }

    @Test
    fun `obscured-window flag is not mistaken for the accessibility flag`() {
        // The app's own floating bubble sets FLAG_WINDOW_IS_OBSCURED on real taps behind it.
        assertFalse(
            AutoClickHeuristic.isSynthetic(
                isTouchscreen = true,
                deviceId = 3,
                isVirtualDevice = false,
                flags = 0x1 or 0x2,
            )
        )
    }

    @Test
    fun `non-touchscreen sources are left alone`() {
        // A mouse on a Chromebook or DeX reports a non-touch source. Blocking those would
        // break legitimate users for no gain, so the guard never looks at them.
        assertFalse(
            AutoClickHeuristic.isSynthetic(
                isTouchscreen = false,
                deviceId = -1,
                isVirtualDevice = true,
                flags = AutoClickHeuristic.FLAG_IS_ACCESSIBILITY_EVENT,
            )
        )
    }
}
