package tools.mo3ta.salo.domain

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.data.crypto.sha256hex
import tools.mo3ta.salo.data.engagement.DailyGoalStore
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.engagement.RoundStreakStore
import tools.mo3ta.salo.data.session.LocalAccountReset
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.data.time.NetworkTimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountRestoreManagerTest {

    private val code = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    private val uid = sha256hex(code)
    private val roundKey = "2026-05-15"

    // 2026-05-12T09:00Z is 12:00 in Cairo, so the Cairo day is unambiguous.
    private val today = LocalDate(2026, 5, 12)

    private class FixedTimeProvider : NetworkTimeProvider {
        override fun prime() {}
        override fun getCompetitionWindow() = MohamedLoversCompetitionWindow(
            networkNow = Instant.parse("2026-05-12T09:00:00Z"),
            roundKey = "2026-05-15",
        )
    }

    private class Fixture {
        val settings = MapSettings()
        val api = FakeMohamedLoversFirebaseApi()
        val sessionStore = MohamedLoversSessionStore(settings)
        val engagementStore = EngagementStore(settings)
        val roundStreakStore = RoundStreakStore(settings)
        val dailyGoalStore = DailyGoalStore(settings)
        val restoredLifetimes = mutableListOf<Pair<String, Int>>()
        var lifetimeFetchFails = false

        fun manager(): AccountRestoreManager = AccountRestoreManager(
            firebaseApi = api,
            sessionStore = sessionStore,
            localAccountReset = LocalAccountReset(settings),
            engagementStore = engagementStore,
            roundStreakStore = roundStreakStore,
            dailyGoalStore = dailyGoalStore,
            networkTimeProvider = FixedTimeProvider(),
            challengeLifetimes = listOf(
                ChallengeLifetimeLink(
                    id = "ghars",
                    fetch = {
                        if (lifetimeFetchFails) Result.failure(IllegalStateException("offline"))
                        else Result.success(4200)
                    },
                    apply = { restoredLifetimes += "ghars" to it },
                ),
            ),
        )
    }

    private fun snapshot(
        todayCount: Int = 0,
        roundStreak: Int = 0,
        nickname: String = "",
    ) = AccountSnapshot(
        installDate = "2026-01-01",
        achievements = mapOf(
            "2026-05-08" to UserAchievement(rank = 2, score = 9000, winnerCode = ""),
            "2026-05-01" to UserAchievement(rank = 7, score = 4000, winnerCode = ""),
        ),
        totalCount = 12_000,
        todayCount = todayCount,
        roundStreak = roundStreak,
        nickname = nickname,
    )

    @Test
    fun invalidCode_isRejectedWithoutTouchingAnything() = runTest {
        val f = Fixture()
        val before = f.sessionStore.getRawUid()

        assertEquals(AccountRestoreResult.InvalidCode, f.manager().restore("not a code"))

        assertEquals(before, f.sessionStore.getRawUid())
        assertTrue(f.api.accountSnapshotCalls.isEmpty())
    }

    @Test
    fun publicId_isCalledOutSeparately() = runTest {
        val f = Fixture()
        assertEquals(AccountRestoreResult.PublicIdGiven, f.manager().restore(uid))
    }

    @Test
    fun ownCode_isReportedAsSameAccount() = runTest {
        val f = Fixture()
        val ownCode = f.sessionStore.getRawUid()
        assertEquals(AccountRestoreResult.SameAccount, f.manager().restore(ownCode))
    }

    @Test
    fun unknownAccount_leavesLocalDataUntouched() = runTest {
        val f = Fixture()
        f.settings.putInt("heart_score", 300)
        val before = f.sessionStore.getRawUid()
        f.api.accountSnapshots[uid] = null

        assertEquals(AccountRestoreResult.NotFound, f.manager().restore(code))

        assertEquals(before, f.sessionStore.getRawUid())
        assertEquals(300, f.settings.getInt("heart_score", 0))
    }

    @Test
    fun failedLookup_leavesLocalDataUntouched() = runTest {
        val f = Fixture()
        f.settings.putInt("heart_score", 300)
        val before = f.sessionStore.getRawUid()
        f.api.accountSnapshotResult = Result.failure(IllegalStateException("offline"))

        val result = f.manager().restore(code)

        assertTrue(result is AccountRestoreResult.Failed)
        assertEquals(before, f.sessionStore.getRawUid())
        assertEquals(300, f.settings.getInt("heart_score", 0))
    }

    @Test
    fun successfulRestore_adoptsUidAndWipesPreviousAccountState() = runTest {
        val f = Fixture()
        f.settings.putInt("heart_score", 300)
        f.settings.putString("fcm_token", "old-device-token")
        f.settings.putBoolean("fcm_token_synced", true)
        f.settings.putString("app_language", "en")
        f.api.accountSnapshots[uid] = snapshot()

        val result = f.manager().restore(code)

        assertTrue(result is AccountRestoreResult.Restored)
        assertEquals(code, f.sessionStore.getRawUid())
        assertEquals(uid, f.sessionStore.getOrCreateUid())
        assertNull(f.settings.getIntOrNull("heart_score"))
        // The FCM token must not follow the previous account onto the restored uid.
        assertNull(f.settings.getStringOrNull("fcm_token"))
        assertFalse(f.settings.getBoolean("fcm_token_synced", true))
        // Device preferences are not account data.
        assertEquals("en", f.settings.getStringOrNull("app_language"))
    }

    @Test
    fun successfulRestore_hydratesServerSideState() = runTest {
        val f = Fixture()
        f.api.accountSnapshots[uid] = snapshot(todayCount = 640, roundStreak = 5, nickname = "محب")

        val result = f.manager().restore(code) as AccountRestoreResult.Restored

        assertEquals(2, f.engagementStore.getAllAchievements().size)
        assertEquals(640, f.dailyGoalStore.todayProgress(today))
        assertEquals(5, f.roundStreakStore.getCurrentStreak(roundKey, today))
        assertEquals("محب", f.sessionStore.getNickname())
        assertTrue(f.sessionStore.isNicknameEnabled)
        assertEquals(listOf("ghars" to 4200), f.restoredLifetimes)
        assertEquals(12_000, result.summary.totalCount)
        assertEquals(2, result.summary.achievements)
        assertEquals(5, result.summary.roundStreak)
        assertEquals(4200, result.summary.challengeTotals)
    }

    @Test
    fun restoredAchievements_useTheRoundKeyAsTheirDate() = runTest {
        val f = Fixture()
        f.api.accountSnapshots[uid] = snapshot()

        f.manager().restore(code)

        val ranks = f.engagementStore.getAllAchievements()
            .filterIsInstance<Achievement.RankAchievement>()
            .associateBy { it.roundKey }
        assertEquals(LocalDate(2026, 5, 8), ranks.getValue("2026-05-08").earnedDate)
        assertEquals(2, ranks.getValue("2026-05-08").rank)
    }

    @Test
    fun streakWithNoActivityToday_standsFromYesterdaySoTodaysSalawatExtendsIt() = runTest {
        val f = Fixture()
        f.api.accountSnapshots[uid] = snapshot(todayCount = 0, roundStreak = 3)

        f.manager().restore(code)

        // Alive today (restored from yesterday) …
        assertEquals(3, f.roundStreakStore.getCurrentStreak(roundKey, today))
        // … and today's first salawat extends rather than repeats it.
        assertEquals(4, f.roundStreakStore.recordActivity(roundKey, today).currentStreak)
    }

    @Test
    fun streakWithActivityToday_isNotDoubleCounted() = runTest {
        val f = Fixture()
        f.api.accountSnapshots[uid] = snapshot(todayCount = 120, roundStreak = 3)

        f.manager().restore(code)

        assertEquals(3, f.roundStreakStore.getCurrentStreak(roundKey, today))
        assertEquals(3, f.roundStreakStore.recordActivity(roundKey, today).currentStreak)
    }

    @Test
    fun uidSuffixNickname_isNotRestoredAsARealNickname() = runTest {
        val f = Fixture()
        f.api.accountSnapshots[uid] = snapshot(nickname = uid.takeLast(6))

        f.manager().restore(code)

        assertNull(f.sessionStore.getNickname())
        assertFalse(f.sessionStore.isNicknameEnabled)
    }

    @Test
    fun failedLifetimeRead_restoresZero_whichTheStoreIgnores() = runTest {
        val f = Fixture()
        f.lifetimeFetchFails = true
        f.api.accountSnapshots[uid] = snapshot()

        val result = f.manager().restore(code) as AccountRestoreResult.Restored

        assertEquals(listOf("ghars" to 0), f.restoredLifetimes)
        assertEquals(0, result.summary.challengeTotals)
    }

    @Test
    fun restoreIsAvailableFromTheDeviceCode() {
        val f = Fixture()
        assertEquals(f.sessionStore.getRawUid(), f.manager().currentBackupCode())
    }
}
