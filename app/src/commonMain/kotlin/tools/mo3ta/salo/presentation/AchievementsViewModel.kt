package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.mo3ta.salo.data.engagement.ChallengeBadgeStore
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.engagement.RoundStreakStore
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.ChallengeType

class AchievementsViewModel(
    engagementStore: EngagementStore,
    roundStreakStore: RoundStreakStore,
    challengeBadgeStore: ChallengeBadgeStore,
) : ViewModel() {

    private val _achievements = MutableStateFlow<List<Achievement>>(
        engagementStore.getAllAchievements() + roundStreakStore.getEarnedBadges(),
    )
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()

    private val _currentStreak = MutableStateFlow(engagementStore.getCurrentStreak())
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()

    private val _challengeBadgeCounts = MutableStateFlow(challengeBadgeStore.getWinCounts())
    val challengeBadgeCounts: StateFlow<Map<ChallengeType, Int>> = _challengeBadgeCounts.asStateFlow()
}
