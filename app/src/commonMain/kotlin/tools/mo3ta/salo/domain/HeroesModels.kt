package tools.mo3ta.salo.domain

/** A single champion in one challenge's top-3 for the day. */
data class HeroEntry(
    val rank: Int,
    val name: String,
    val count: Int,
    val countryCode: String = "",
)

/** Ordered list of the challenges shown in the heroes sheet. */
enum class HeroChallengeType(val key: String) {
    Salawat("salawat"),
    Dhikr("dhikr"),
    Baqiyat("baqiyat"),
    Istighfar("istighfar"),
    Quran("quran"),
}

data class HeroChallenge(
    val type: HeroChallengeType,
    val entries: List<HeroEntry>,
)

/**
 * The day's champions across all active challenges. Written server-side (read-only
 * to the client) to RTDB `mohamed_lovers/heroes`, overwritten daily.
 */
data class HeroesBoard(
    val date: String,
    val challenges: List<HeroChallenge>,
) {
    /** True when at least one challenge has a champion — controls whether the chip shows. */
    val hasAny: Boolean get() = challenges.any { it.entries.isNotEmpty() }
}

/**
 * Parses the raw RTDB/Firestore snapshot value into a [HeroesBoard]. Tolerates the
 * per-challenge entry lists being stored either as arrays (list) or 0-indexed maps,
 * which is how RTDB round-trips them. Returns null when the value is absent/malformed.
 */
fun parseHeroesBoard(value: Any?): HeroesBoard? {
    val root = value as? Map<*, *> ?: return null
    val date = root["date"] as? String ?: ""
    val challengesRaw = root["challenges"] as? Map<*, *> ?: emptyMap<Any?, Any?>()

    val challenges = HeroChallengeType.entries.map { type ->
        HeroChallenge(
            type = type,
            entries = parseHeroEntries(challengesRaw[type.key]),
        )
    }
    return HeroesBoard(date = date, challenges = challenges)
}

private fun parseHeroEntries(value: Any?): List<HeroEntry> {
    val rawEntries: List<Any?> = when (value) {
        is List<*> -> value
        is Map<*, *> -> value.entries
            .sortedBy { it.key.toString().toIntOrNull() ?: Int.MAX_VALUE }
            .map { it.value }
        else -> return emptyList()
    }
    return rawEntries.mapNotNull { it.toHeroEntry() }
        .sortedBy { it.rank }
}

private fun Any?.toHeroEntry(): HeroEntry? {
    val map = this as? Map<*, *> ?: return null
    val name = (map["name"] as? String)?.takeIf { it.isNotBlank() } ?: return null
    return HeroEntry(
        rank = (map["rank"] as? Number)?.toInt() ?: 0,
        name = name,
        count = (map["count"] as? Number)?.toInt() ?: 0,
        countryCode = map["countryCode"] as? String ?: "",
    )
}
