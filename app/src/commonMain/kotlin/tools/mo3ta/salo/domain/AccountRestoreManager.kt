package tools.mo3ta.salo.domain

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tools.mo3ta.salo.data.crypto.sha256hex
import tools.mo3ta.salo.data.engagement.DailyGoalStore
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.engagement.RoundStreakStore
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseApi
import tools.mo3ta.salo.data.session.LocalAccountReset
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.data.time.NetworkTimeProvider

/**
 * One challenge's lifetime ("total over time") counter, bound to the store that holds it locally
 * and the client that reads the server's copy. Assembled per challenge in the DI module so the
 * restore does not need a constructor argument per challenge.
 */
class ChallengeLifetimeLink(
    val id: String,
    val fetch: suspend (uid: String) -> Result<Int>,
    val apply: (total: Int) -> Unit,
)

/**
 * Moves an account onto this device from its backup code (see [parseBackupCode]).
 *
 * The app has no accounts and no auth — identity is the raw uid persisted on the device, so a
 * restore is: adopt the code as this device's uid, drop the local state that belonged to the
 * account being replaced, and pull back what the server still holds for the adopted uid.
 *
 * Ordering matters. Every network read happens **before** anything local is touched, so a restore
 * that fails (offline, unknown code) leaves the device exactly as it was; only once the account is
 * confirmed does the wipe-and-hydrate run.
 */
class AccountRestoreManager(
    private val firebaseApi: MohamedLoversFirebaseApi,
    private val sessionStore: MohamedLoversSessionStore,
    private val localAccountReset: LocalAccountReset,
    private val engagementStore: EngagementStore,
    private val roundStreakStore: RoundStreakStore,
    private val dailyGoalStore: DailyGoalStore,
    private val networkTimeProvider: NetworkTimeProvider,
    private val challengeLifetimes: List<ChallengeLifetimeLink>,
) {

    /** This device's own backup code — the raw uid the user should save to restore later. */
    fun currentBackupCode(): String = sessionStore.getRawUid()

    suspend fun restore(input: String): AccountRestoreResult {
        val code = when (val parsed = parseBackupCode(input)) {
            is BackupCodeParseResult.Valid -> parsed.code
            BackupCodeParseResult.PublicId -> return AccountRestoreResult.PublicIdGiven
            BackupCodeParseResult.Invalid -> return AccountRestoreResult.InvalidCode
        }
        if (code == sessionStore.getRawUid()) return AccountRestoreResult.SameAccount

        val window = networkTimeProvider.getCompetitionWindow()
        val roundKey = window.roundKey ?: return AccountRestoreResult.Failed()
        val today = (window.networkNow ?: Clock.System.now()).toLocalDateTime(CAIRO).date
        val uid = sha256hex(code)

        val snapshot = firebaseApi.fetchAccountSnapshot(uid, roundKey).fold(
            onSuccess = { it ?: return AccountRestoreResult.NotFound },
            onFailure = { return AccountRestoreResult.Failed(it) },
        )
        // A challenge whose read fails restores as 0, which its store then ignores in favour of
        // whatever it already holds — never a value that could overwrite the server's.
        val lifetimes = challengeLifetimes.map { link -> link to link.fetch(uid).getOrDefault(0) }

        localAccountReset.wipeAccountData()
        sessionStore.adoptRestoredUid(code)
        sessionStore.setInstallDate(snapshot.installDate)
        engagementStore.restoreRankAchievements(snapshot.achievements, today)
        dailyGoalStore.restoreProgress(today, snapshot.todayCount)
        roundStreakStore.restoreStreak(
            streak = snapshot.roundStreak,
            // The server carries the count, not the day it was last extended: a day that already
            // has salawat on it is the streak's own day, otherwise the streak stands from
            // yesterday and today's first salawat extends it.
            lastActive = if (snapshot.todayCount > 0) today else today.minusDays(1),
        )
        restorePublishedName(snapshot.nickname, uid)
        lifetimes.forEach { (link, total) -> link.apply(total) }

        return AccountRestoreResult.Restored(
            AccountRestoreSummary(
                totalCount = snapshot.totalCount,
                achievements = snapshot.achievements.size,
                roundStreak = snapshot.roundStreak,
                challengeTotals = lifetimes.sumOf { (_, total) -> total },
            ),
        )
    }

    /**
     * The leaderboard name is a nickname when the account set one and the uid suffix otherwise, so
     * only a name that differs from the suffix is a real nickname worth restoring.
     */
    private fun restorePublishedName(nickname: String, uid: String) {
        if (nickname.isBlank() || nickname == uid.takeLast(6)) return
        sessionStore.setNickname(nickname)
        sessionStore.isNicknameEnabled = true
    }

    private fun LocalDate.minusDays(days: Int): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() - days)

    private companion object {
        val CAIRO = TimeZone.of("Africa/Cairo")
    }
}
