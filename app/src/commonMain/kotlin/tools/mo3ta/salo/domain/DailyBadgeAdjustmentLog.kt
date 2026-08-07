package tools.mo3ta.salo.domain

import kotlinx.datetime.Instant

/**
 * Audit trail for daily-badge score reconciliations — the cases where the client raised today's
 * local count to match the badge the server had already published for the user.
 *
 * A reconciliation is always *some* kind of anomaly: the badge only lands after the score that
 * earned it reached the server, so a local count below the badge threshold means the device lost
 * today's progress. Most of the time that is innocent (reinstall, cleared storage, a second device
 * mid-day), but a device that keeps producing them is worth looking at, so every one is recorded
 * under the user node for later analysis. Record-only — nothing about the score depends on it, and
 * the write is fire-and-forget.
 *
 * Entries land at `mohamed_lovers/users/{uid}/badgeAdjustments/{entryKey}` and are **not readable by
 * the client** (the user node is `.read: false`); only the admin scripts see them.
 */
object DailyBadgeAdjustmentLog {

    /** The server badge claimed more than the local day count knew about, so the count adopted it. */
    const val CASE_BADGE_ABOVE_PROGRESS = "badge_above_progress"

    /**
     * The log key for [at]: the Cairo wall-clock minute, same format and constraints as the
     * external-salawat log (`yyyy-MM-dd HH;mm`, all characters legal in an RTDB key). Two
     * reconciliations within the same minute collapse onto one entry, which is fine — they carry
     * the same reason and the later one is the state that stuck.
     */
    fun entryKey(at: Instant): String = ExternalSalawatLog.entryKey(at)
}

/**
 * One recorded badge reconciliation.
 *
 * @param case why the adjustment ran; see [DailyBadgeAdjustmentLog.CASE_BADGE_ABOVE_PROGRESS].
 * @param atMs the device's clock when it ran. The server stamps its own time alongside this, so a
 *   large gap between the two is itself a signal (a manipulated device clock).
 * @param progress today's local count **before** the raise — the number that was found to be short.
 * @param badgeKey the server-published badge that triggered it.
 * @param badgeValue that badge's threshold, i.e. the value the count was raised to.
 */
data class DailyBadgeAdjustment(
    val case: String,
    val atMs: Long,
    val progress: Int,
    val badgeKey: String,
    val badgeValue: Int,
)
