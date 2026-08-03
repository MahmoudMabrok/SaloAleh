package tools.mo3ta.salo.domain

enum class DailyBadge(val key: String, val threshold: Int) {
    SPARK("spark", 10),
    SPROUT("sprout", 100),
    HEART("heart", 200),
    TASBIH("tasbih", 500),
    DOME("dome", 1000),
    CRESCENT("crescent", 2000),
    LANTERN("lantern", 4000),
    CROWN("crown", 5000),
    MIHRAB("mihrab", 8000),
    STAR("star", 10000);

    fun next(): DailyBadge? {
        val idx = entries.indexOf(this)
        return entries.getOrNull(idx + 1)
    }

    companion object {
        fun fromTapCount(count: Int): DailyBadge? =
            entries.lastOrNull { count >= it.threshold }

        fun fromKey(key: String): DailyBadge? =
            entries.firstOrNull { it.key == key }

        val VALID_KEYS = entries.map { it.key }.toSet()
    }
}
