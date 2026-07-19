package tools.mo3ta.salo.domain

/**
 * Reading Surah Al-Baqara is a long act (≈1.5–2h), so a single completed reading
 * counts as a full day. The button records one read per tap; the leaderboard ranks
 * by the cumulative number of times the surah was read today.
 */
const val ALBAQARA_CHALLENGE_DAILY_GOAL = 1

/**
 * Surah Al-Baqara spans 48 pages of the Mushaf, so a single completed reading is,
 * by definition, 48 Quran pages. Crediting a reading to the Quran challenge adds this many.
 */
const val ALBAQARA_PAGE_COUNT = 48

data class AlBaqaraChallengeDayStats(
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayAlBaqara: Int = 0,
)

data class AlBaqaraLeaderboardEntry(
    val uid: String,
    val countryCode: String,
    val count: Int,
    val rank: Int,
    val rankChange: String, // "up" | "down" | "same" | "new"
    val nickname: String = "",
)
