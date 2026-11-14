package tools.mo3ta.salo.domain

/**
 * أهل الذكر — the daily public milestone lists (30/60/90-day current streaks) per
 * challenge, published as a JSON snapshot by the "Ahl Adhikr Snapshot" workflow and
 * served from GitHub Pages. Identity is limited to a short public id and nickname;
 * the snapshot never carries tokens, backup codes, or private profile data.
 */

/** The three milestone tiers the snapshot publishes; higher tiers include lower ones. */
enum class AhlAdhikrMilestone(val key: String, val minDays: Int) {
    D30("30_days", 30),
    D60("60_days", 60),
    D90("90_days", 90);

    companion object {
        fun fromKey(key: String): AhlAdhikrMilestone? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The challenges the snapshot covers, in display order. `key` is the JSON section in
 * the snapshot file (kept in sync with scripts/ahl-adhikr-utils.js CHALLENGE_KEYS).
 */
enum class AhlAdhikrChallenge(val key: String) {
    SALAWAT("salawat"),
    DHIKR("dhikr"),
    BAQIYAT("baqiyat"),
    ISTIGHFAR("istighfar"),
    ZABAD("zabad"),
    QURAN("quran"),
    GHARS("ghars"),
    ALF_HASANA("alf_hasana"),
    KALIMAT("kalimat"),
    HAWQALA("hawqala");

    companion object {
        fun fromKey(key: String): AhlAdhikrChallenge? = entries.firstOrNull { it.key == key }
    }
}

/** One published user: short public id (uid suffix), display name, current streak. */
data class AhlAdhikrEntry(
    val id: String,
    val name: String,
    val streak: Int,
)

/** A parsed snapshot: which day it covers and every challenge's milestone lists. */
data class AhlAdhikrSnapshot(
    val generatedAt: String,
    val date: String,
    val challenges: Map<String, Map<String, List<AhlAdhikrEntry>>>,
) {
    /** Entries for one challenge/milestone; empty (never null) when data is missing. */
    fun listFor(challenge: AhlAdhikrChallenge, milestone: AhlAdhikrMilestone): List<AhlAdhikrEntry> =
        challenges[challenge.key]?.get(milestone.key) ?: emptyList()
}

/** Where the loaded snapshot came from — drives the stale-data notice in the UI. */
enum class AhlAdhikrSource { REMOTE, CACHE, EMPTY }
