package tools.mo3ta.salo.domain

/**
 * The daily challenges that award a repeatable achievement badge. A "win" is
 * reaching the challenge's daily goal; each win increments that badge's count by 1
 * (at most once per Cairo day).
 */
enum class ChallengeType(val id: String, val dailyGoal: Int) {
    DHIKR("dhikr", DHIKR_CHALLENGE_DAILY_GOAL),
    BAQIYAT("baqiyat", BAQIYAT_CHALLENGE_DAILY_GOAL),
    ISTIGHFAR("istighfar", ISTIGHFAR_CHALLENGE_DAILY_GOAL),
    ZABAD("zabad", ZABAD_CHALLENGE_DAILY_GOAL),
    QURAN("quran", QURAN_CHALLENGE_DAILY_GOAL),
}
