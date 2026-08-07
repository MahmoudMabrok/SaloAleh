package tools.mo3ta.salo.domain

/**
 * Maximum amount a user may add per Cairo day, per challenge, through manual
 * ("external") entry — the sheet where a count reached on fingers/tasbih is typed in.
 * Regular tapping is uncapped; only these external batches are limited so a single
 * manual entry can't flood the leaderboard. Applied per challenge, resets each day.
 */
const val CHALLENGE_MANUAL_DAILY_CAP = 5_000

/**
 * Baqiyat's own, lower manual-entry daily cap. Baqiyat is counted in full cycles
 * (one pass over all phrases), so its external batches are limited to a tighter
 * ceiling than the per-count challenges above.
 */
const val BAQIYAT_MANUAL_DAILY_CAP = 2_000

/**
 * Maximum salawat a user may credit to the weekly Mohamed Lovers competition per Cairo day
 * through manual ("record external counts") entry — the sheet where salawat performed outside
 * the app is typed in. Regular in-app tapping is uncapped; only these external batches are
 * limited so a single manual entry can't flood the leaderboard. Resets each Cairo day.
 */
const val MOHAMED_LOVERS_MANUAL_DAILY_CAP = 15_000

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
    GHARS("ghars", GHARS_CHALLENGE_DAILY_GOAL),
    ALF_HASANA("alf_hasana", ALF_HASANA_CHALLENGE_DAILY_GOAL),
    KALIMAT("kalimat", KALIMAT_CHALLENGE_DAILY_GOAL),
    HAWQALA("hawqala", HAWQALA_CHALLENGE_DAILY_GOAL),
}
