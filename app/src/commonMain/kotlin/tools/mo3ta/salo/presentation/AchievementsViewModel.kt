package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.domain.Achievement

class AchievementsViewModel(engagementStore: EngagementStore) : ViewModel() {

    private val _achievements = MutableStateFlow<List<Achievement>>(engagementStore.getAllAchievements())
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()

    private val _currentStreak = MutableStateFlow(engagementStore.getCurrentStreak())
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()
}
