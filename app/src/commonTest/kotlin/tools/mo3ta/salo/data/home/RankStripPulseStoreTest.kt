package tools.mo3ta.salo.data.home

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RankStripPulseStoreTest {

    @Test
    fun freshInstall_pulsesOnTheFirstLaunchTheCardIsVisible() {
        val settings = MapSettings()
        val store = RankStripPulseStore(settings)

        assertFalse(store.hasTapped())
        assertEquals(0, store.launchesSeen())
        assertTrue(store.noteCardVisibleLaunch())
        assertEquals(1, store.launchesSeen())
    }

    @Test
    fun pulses_forTheFirstFiveLaunches_thenStops() {
        val settings = MapSettings()

        repeat(RankStripPulseStore.MAX_LAUNCHES) { index ->
            val store = RankStripPulseStore(settings)
            assertTrue(store.noteCardVisibleLaunch(), "launch ${index + 1}")
        }

        val sixth = RankStripPulseStore(settings)
        assertFalse(sixth.noteCardVisibleLaunch())
        assertEquals(RankStripPulseStore.MAX_LAUNCHES + 1, sixth.launchesSeen())
    }

    @Test
    fun sameProcess_doesNotConsumeASecondLaunch() {
        val settings = MapSettings()
        val store = RankStripPulseStore(settings)

        assertTrue(store.noteCardVisibleLaunch())
        assertTrue(store.noteCardVisibleLaunch())
        assertEquals(1, store.launchesSeen())
    }

    @Test
    fun tap_stopsThePulsePermanently_andLaterLaunchesDoNotCount() {
        val settings = MapSettings()
        val first = RankStripPulseStore(settings)
        assertTrue(first.noteCardVisibleLaunch())

        first.markTapped()

        assertTrue(first.hasTapped())
        assertFalse(first.noteCardVisibleLaunch())
        assertEquals(1, first.launchesSeen())

        val later = RankStripPulseStore(settings)
        assertFalse(later.noteCardVisibleLaunch())
        assertEquals(1, later.launchesSeen())
        assertTrue(later.hasTapped())
    }

    @Test
    fun tapBeforeAnyLaunch_neverPulses() {
        val settings = MapSettings()
        RankStripPulseStore(settings).markTapped()

        val store = RankStripPulseStore(settings)
        assertFalse(store.noteCardVisibleLaunch())
        assertEquals(0, store.launchesSeen())
    }

    @Test
    fun showStopDecision_isLaunchWindowOrFirstTap() {
        assertFalse(shouldShowRankStripPulse(tapped = false, launchesSeen = 0))
        assertTrue(shouldShowRankStripPulse(tapped = false, launchesSeen = 1))
        assertTrue(shouldShowRankStripPulse(tapped = false, launchesSeen = 5))
        assertFalse(shouldShowRankStripPulse(tapped = false, launchesSeen = 6))
        assertFalse(shouldShowRankStripPulse(tapped = true, launchesSeen = 1))
        assertFalse(shouldShowRankStripPulse(tapped = true, launchesSeen = 5))
        assertFalse(shouldShowRankStripPulse(tapped = false, launchesSeen = -1))
    }
}
