package tools.mo3ta.salo.presentation

import tools.mo3ta.salo.data.tendays.DhikrType
import tools.mo3ta.salo.data.tendays.TenDaysLeaderboardEntry

data class TenDaysDayState(
    val day: Int,
    val dhikrCounts: Map<DhikrType, Int> = DhikrType.entries.associateWith { 0 },
    val takbeerCount: Int = 0,
    val isFasting: Boolean = false,
    val isSadaqah: Boolean = false,
) {
    val dayScore: Int
        get() = dhikrCounts.values.sum() +
                (if (isFasting) 100 else 0) +
                (takbeerCount * 5) +
                (if (isSadaqah) 150 else 0)
}

data class TenDaysUiState(
    val currentDay: Int = 1,
    val totalDays: Int = 9,
    val days: List<TenDaysDayState> = (1..9).map { TenDaysDayState(day = it) },
    val totalScore: Int = 0,
    val selfRank: Int = 0,
    val leaderboard: List<TenDaysLeaderboardEntry> = emptyList(),
    val autoPlayTakbeer: Boolean = false,
    val isActive: Boolean = true,
    val periodKey: String = "",
)
