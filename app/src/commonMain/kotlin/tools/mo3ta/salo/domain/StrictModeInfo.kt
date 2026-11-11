package tools.mo3ta.salo.domain

/**
 * The server-owned anti-cheat quarantine flag (`mohamed_lovers/strictMode/{uid}`).
 *
 * Written only by the admin scripts (never by clients) when the nightly checks flag a player
 * for abnormal daily counts or an over-pace round total, and lifted automatically after a run
 * of clean days. While present the client switches to strict counting: the main tap is
 * disabled, every credited salawa goes through a live per-interaction push (online-only), and
 * no locally stored total is published.
 */
data class StrictModeInfo(
    val since: Long = 0L,
    val reason: String = "",
    val dateKey: String = "",
    val cleanDays: Int = 0,
)
