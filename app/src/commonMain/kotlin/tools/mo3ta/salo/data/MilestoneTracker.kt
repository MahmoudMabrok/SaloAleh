package tools.mo3ta.salo.data

import com.russhwolf.settings.Settings

class MilestoneTracker(private val settings: Settings) {

    private val milestones = listOf(1000, 5000, 10000, 25000, 50000)

    private var shownThisSession = mutableSetOf<Int>()

    fun checkMilestone(score: Int): Int? {
        val hit = milestones.lastOrNull { score >= it } ?: return null
        if (shownThisSession.contains(hit)) return null
        if (settings.getBoolean("milestone_shown_$hit", false)) return null
        return hit
    }

    fun markShown(milestone: Int) {
        shownThisSession.add(milestone)
        settings.putBoolean("milestone_shown_$milestone", true)
    }
}
