package tools.mo3ta.salo.data.home

import com.russhwolf.settings.Settings

/**
 * Discoverability pulse for the home-screen today-count card.
 *
 * The pulse plays on the first [MAX_LAUNCHES] process launches in which that card
 * is actually on screen, and it stops for good the first time the card is tapped.
 * Whichever comes first wins. Persistence is local `multiplatform-settings` only.
 *
 * [noteCardVisibleLaunch] counts one launch per process. Calling it again after the
 * home screen is recreated in the same process does not consume another launch.
 */
class RankStripPulseStore(private val settings: Settings) {

    private var countedThisProcess = false

    fun hasTapped(): Boolean = settings.getBoolean(KEY_TAPPED, false)

    fun launchesSeen(): Int = settings.getInt(KEY_LAUNCHES, 0)

    /**
     * Records this process as one launch the first time the card is visible, then
     * reports whether the pulse should play.
     */
    fun noteCardVisibleLaunch(): Boolean {
        if (hasTapped()) return false
        if (!countedThisProcess) {
            countedThisProcess = true
            val next = (launchesSeen() + 1).coerceAtMost(MAX_LAUNCHES + 1)
            settings.putInt(KEY_LAUNCHES, next)
        }
        return shouldShowRankStripPulse(tapped = false, launchesSeen = launchesSeen())
    }

    fun markTapped() {
        settings.putBoolean(KEY_TAPPED, true)
    }

    companion object {
        const val MAX_LAUNCHES = 5
        const val KEY_TAPPED = "rank_strip_pulse_tapped"
        const val KEY_LAUNCHES = "rank_strip_pulse_launches"
    }
}

/**
 * Pulse is on for recorded launches `1..[maxLaunches]` until the card has been tapped.
 *
 * [launchesSeen] includes the current launch. `0` means the card has not been shown yet.
 */
internal fun shouldShowRankStripPulse(
    tapped: Boolean,
    launchesSeen: Int,
    maxLaunches: Int = RankStripPulseStore.MAX_LAUNCHES,
): Boolean = !tapped && launchesSeen in 1..maxLaunches
