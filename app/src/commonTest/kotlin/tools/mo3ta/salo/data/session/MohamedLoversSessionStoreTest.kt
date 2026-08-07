package tools.mo3ta.salo.data.session

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_DAILY_PUSH_CAP
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_MANUAL_DAILY_CAP
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MohamedLoversSessionStoreTest {

    private lateinit var store: MohamedLoversSessionStore

    @BeforeTest
    fun setup() {
        store = MohamedLoversSessionStore(MapSettings())
    }

    @Test
    fun pendingSession_initially_empty() {
        val session = store.getPendingSession("2026-05-01")
        assertEquals(0, session.clickCount)
    }

    @Test
    fun incrementPendingClick_accumulates_in_same_round() {
        store.incrementPendingClick("2026-05-01", 1)
        store.incrementPendingClick("2026-05-01", 2)
        val session = store.getPendingSession("2026-05-01")
        assertEquals(3, session.clickCount)
    }

    @Test
    fun incrementPendingClick_separate_rounds_independent() {
        store.incrementPendingClick("2026-05-01", 5)
        store.incrementPendingClick("2026-05-08", 1)
        assertEquals(5, store.getPendingSession("2026-05-01").clickCount)
        assertEquals(1, store.getPendingSession("2026-05-08").clickCount)
    }

    @Test
    fun clearPendingRound_removes_only_that_round() {
        store.incrementPendingClick("2026-05-01", 3)
        store.incrementPendingClick("2026-05-08", 7)
        store.clearPendingRound("2026-05-01")
        assertEquals(0, store.getPendingSession("2026-05-01").clickCount)
        assertEquals(7, store.getPendingSession("2026-05-08").clickCount)
    }

    @Test
    fun getAllPendingRounds_returns_all_nonzero() {
        store.incrementPendingClick("2026-05-01", 3)
        store.incrementPendingClick("2026-05-08", 7)
        val all = store.getAllPendingRounds()
        assertEquals(2, all.size)
        assertEquals(3, all["2026-05-01"])
        assertEquals(7, all["2026-05-08"])
    }

    @Test
    fun getAllPendingRounds_excludes_cleared() {
        store.incrementPendingClick("2026-05-01", 3)
        store.clearPendingRound("2026-05-01")
        val all = store.getAllPendingRounds()
        assertTrue(all.isEmpty())
    }

    @Test
    fun getOrCreateAlias_stable_across_calls() {
        val first = store.getOrCreateAlias()
        val second = store.getOrCreateAlias()
        assertEquals(first, second)
        assertTrue(first.startsWith("محب محمد "))
    }

    @Test
    fun getOrSetInstallDate_firstCall_storesAndReturns() {
        val date = LocalDate(2026, 5, 8)
        val result = store.getOrSetInstallDate(date)
        assertEquals("2026-05-08", result)
        val result2 = store.getOrSetInstallDate(LocalDate(2026, 5, 9))
        assertEquals("2026-05-08", result2)
    }

    @Test
    fun hasExistingUserState_initiallyFalse_thenTrueAfterUidCreated() {
        assertEquals(false, store.hasExistingUserState())
        store.getRawUid()
        assertEquals(true, store.hasExistingUserState())
    }

    @Test
    fun personalBestRank_defaultIsMaxInt() {
        assertEquals(Int.MAX_VALUE, store.getPersonalBestRank())
    }

    @Test
    fun updatePersonalBestRank_onlyImproves() {
        store.updatePersonalBestRank(5)
        assertEquals(5, store.getPersonalBestRank())
        store.updatePersonalBestRank(8)
        assertEquals(5, store.getPersonalBestRank())
        store.updatePersonalBestRank(2)
        assertEquals(2, store.getPersonalBestRank())
    }

    @Test
    fun lastRoundTaps_defaultZero_roundTrip() {
        assertEquals(0, store.getLastRoundTaps())
        store.saveLastRoundTaps(420)
        assertEquals(420, store.getLastRoundTaps())
    }

    // --- Score reliability: snapshot-aware decrement ---

    @Test
    fun decrementPendingClick_subtracts_snapshotted_count() {
        store.incrementPendingClick("R1", 5)
        store.decrementPendingClick("R1", 5)
        assertEquals(0, store.getPendingSession("R1").clickCount)
    }

    @Test
    fun decrementPendingClick_preserves_taps_added_after_snapshot() {
        store.incrementPendingClick("R1", 5)
        // Simulate: snapshot = 5, then user taps 3 more during flush
        store.incrementPendingClick("R1", 3)
        // Decrement by snapshot amount (5), expect 3 remaining
        store.decrementPendingClick("R1", 5)
        assertEquals(3, store.getPendingSession("R1").clickCount)
    }

    @Test
    fun decrementPendingClick_floors_at_zero() {
        store.incrementPendingClick("R1", 2)
        store.decrementPendingClick("R1", 10)
        assertEquals(0, store.getPendingSession("R1").clickCount)
    }

    @Test
    fun decrementPendingClick_removes_from_index_when_zero() {
        store.incrementPendingClick("R1", 5)
        store.decrementPendingClick("R1", 5)
        assertTrue(store.getAllPendingRounds().isEmpty())
    }

    @Test
    fun decrementPendingClick_keeps_in_index_when_nonzero() {
        store.incrementPendingClick("R1", 5)
        store.incrementPendingClick("R1", 3)
        store.decrementPendingClick("R1", 5)
        val pending = store.getAllPendingRounds()
        assertEquals(1, pending.size)
        assertEquals(3, pending["R1"])
    }

    // --- Manual ("record external") daily cap ledger ---

    private val d1 = LocalDate(2026, 5, 1)
    private val d2 = LocalDate(2026, 5, 2)

    @Test
    fun manualRemaining_defaultsToFullCap() {
        assertEquals(MOHAMED_LOVERS_MANUAL_DAILY_CAP, store.manualRemainingToday(d1))
    }

    @Test
    fun recordManualEntry_appliesFullAmountBelowCap() {
        val applied = store.recordManualEntry(d1, 300)
        assertEquals(300, applied)
        assertEquals(MOHAMED_LOVERS_MANUAL_DAILY_CAP - 300, store.manualRemainingToday(d1))
    }

    @Test
    fun recordManualEntry_accumulatesWithinDay() {
        store.recordManualEntry(d1, 4_000)
        val applied = store.recordManualEntry(d1, 4_000)
        assertEquals(4_000, applied)
        assertEquals(MOHAMED_LOVERS_MANUAL_DAILY_CAP - 8_000, store.manualRemainingToday(d1))
    }

    @Test
    fun recordManualEntry_clampsToRemainingCap() {
        store.recordManualEntry(d1, 9_000)
        // Only 1_000 of the requested 5_000 fits under the 10_000 cap.
        val applied = store.recordManualEntry(d1, 5_000)
        assertEquals(1_000, applied)
        assertEquals(0, store.manualRemainingToday(d1))
    }

    @Test
    fun recordManualEntry_returnsZeroWhenExhausted() {
        store.recordManualEntry(d1, MOHAMED_LOVERS_MANUAL_DAILY_CAP)
        assertEquals(0, store.recordManualEntry(d1, 500))
    }

    @Test
    fun recordManualEntry_resetsOnNewDay() {
        store.recordManualEntry(d1, MOHAMED_LOVERS_MANUAL_DAILY_CAP)
        assertEquals(0, store.manualRemainingToday(d1))
        // A new Cairo day restores the full allowance.
        assertEquals(MOHAMED_LOVERS_MANUAL_DAILY_CAP, store.manualRemainingToday(d2))
        assertEquals(500, store.recordManualEntry(d2, 500))
    }

    @Test
    fun refundManualEntry_restoresAllowance() {
        store.recordManualEntry(d1, 8_000)
        assertEquals(3_000, store.refundManualEntry(d1, 3_000))
        assertEquals(MOHAMED_LOVERS_MANUAL_DAILY_CAP - 5_000, store.manualRemainingToday(d1))
    }

    @Test
    fun refundManualEntry_flooredAtCap() {
        store.recordManualEntry(d1, 1_000)
        // Only the 1_000 the ledger actually holds is given back, so the mirrored server delta
        // never undercounts what was really used today.
        assertEquals(1_000, store.refundManualEntry(d1, 5_000))
        assertEquals(MOHAMED_LOVERS_MANUAL_DAILY_CAP, store.manualRemainingToday(d1))
    }

    // --- Startup reconciliation with the server-side ledger ---

    @Test
    fun syncManualUsedFromRemote_adoptsRemoteOnAFreshInstall() {
        // A reinstall wipes the local ledger; the server still knows today's usage.
        assertEquals(6_000, store.syncManualUsedFromRemote(d1, 6_000))
        assertEquals(MOHAMED_LOVERS_MANUAL_DAILY_CAP - 6_000, store.manualRemainingToday(d1))
    }

    @Test
    fun syncManualUsedFromRemote_keepsTheHigherLocalLedger() {
        store.recordManualEntry(d1, 7_000)
        // A stale/failed server write must not hand back allowance that was already spent.
        assertEquals(7_000, store.syncManualUsedFromRemote(d1, 2_000))
        assertEquals(MOHAMED_LOVERS_MANUAL_DAILY_CAP - 7_000, store.manualRemainingToday(d1))
    }

    @Test
    fun syncManualUsedFromRemote_ignoresNegativeAndScopesToTheDay() {
        assertEquals(0, store.syncManualUsedFromRemote(d1, -500))
        assertEquals(MOHAMED_LOVERS_MANUAL_DAILY_CAP, store.manualRemainingToday(d1))

        store.syncManualUsedFromRemote(d1, 4_000)
        // The ledger is per Cairo day — yesterday's usage never eats today's allowance.
        assertEquals(MOHAMED_LOVERS_MANUAL_DAILY_CAP, store.manualRemainingToday(d2))
    }

    // --- Gradual new-user ramp: a lower dailyCap overrides the permanent cap ---

    @Test
    fun manualRemaining_respectsLowerDailyCap() {
        assertEquals(1_000, store.manualRemainingToday(d1, dailyCap = 1_000))
    }

    @Test
    fun recordManualEntry_clampsToLowerDailyCap() {
        val applied = store.recordManualEntry(d1, 5_000, dailyCap = 1_000)
        assertEquals(1_000, applied)
        assertEquals(0, store.manualRemainingToday(d1, dailyCap = 1_000))
    }

    @Test
    fun recordManualEntry_lowerCapSharesLedgerWithPermanentCap() {
        // Under a day-2 ramp cap of 2_000, 2_000 is used; the permanent cap then sees 8_000 left.
        store.recordManualEntry(d1, 5_000, dailyCap = 2_000)
        assertEquals(0, store.manualRemainingToday(d1, dailyCap = 2_000))
        assertEquals(MOHAMED_LOVERS_MANUAL_DAILY_CAP - 2_000, store.manualRemainingToday(d1))
    }

    // --- Daily competition push cap ---

    @Test
    fun dailyPush_startsWithTheFullCap() {
        assertEquals(0, store.dailyPushUsed(d1))
        assertEquals(MOHAMED_LOVERS_DAILY_PUSH_CAP, store.dailyPushRemaining(d1))
    }

    @Test
    fun recordDailyPush_accumulatesAndDrainsTheAllowance() {
        store.recordDailyPush(d1, 10_000)
        store.recordDailyPush(d1, 5_000)
        assertEquals(15_000, store.dailyPushUsed(d1))
        assertEquals(MOHAMED_LOVERS_DAILY_PUSH_CAP - 15_000, store.dailyPushRemaining(d1))
    }

    @Test
    fun dailyPush_resetsOnANewCairoDay() {
        store.recordDailyPush(d1, MOHAMED_LOVERS_DAILY_PUSH_CAP)
        assertEquals(0, store.dailyPushRemaining(d1))
        assertEquals(0, store.dailyPushUsed(d2))
        assertEquals(MOHAMED_LOVERS_DAILY_PUSH_CAP, store.dailyPushRemaining(d2))
    }

    @Test
    fun syncDailyPushFromRemote_adoptsTheServerTotalAfterAReinstall() {
        // Local ledger is empty (fresh install), the server already recorded 18k for the day.
        assertEquals(18_000, store.syncDailyPushFromRemote(d1, 18_000))
        assertEquals(MOHAMED_LOVERS_DAILY_PUSH_CAP - 18_000, store.dailyPushRemaining(d1))
    }

    @Test
    fun syncDailyPushFromRemote_keepsTheLocalLedgerWhenItIsAhead() {
        // A round rollover zeroes the server-side day total mid-day; the local ledger must win.
        store.recordDailyPush(d1, 20_000)
        assertEquals(20_000, store.syncDailyPushFromRemote(d1, 0))
        assertEquals(MOHAMED_LOVERS_DAILY_PUSH_CAP - 20_000, store.dailyPushRemaining(d1))
    }

    @Test
    fun baselineIsStoredWithItsFetchTimeAndClearedOnANewDay() {
        store.saveDailyBaseline(d1, yesterdayTotalScore = 9_000, atMs = 1_700_000_000_000L)
        assertEquals(9_000, store.dailyBaseline(d1))
        assertEquals(1_700_000_000_000L, store.dailyBaselineFetchedAt(d1))
        // The next Cairo day starts without a baseline until a fresh snapshot arrives.
        assertNull(store.dailyBaseline(d2))
        assertEquals(0L, store.dailyBaselineFetchedAt(d2))
    }

    @Test
    fun baselineIsNullBeforeAnyFetch() {
        assertNull(store.dailyBaseline(d1))
    }

    @Test
    fun savingTheBaselineDoesNotDisturbTheDaysUsage() {
        store.recordDailyPush(d1, 4_000)
        store.saveDailyBaseline(d1, yesterdayTotalScore = 9_000, atMs = 1L)
        assertEquals(4_000, store.dailyPushUsed(d1))
    }

    // --- Legacy migration ---

    @Test
    fun legacy_migration_moves_old_pending_to_new_format() {
        val s = MapSettings()
        s.putString("pending_round_key", "2026-05-01")
        s.putInt("pending_click_count", 10)
        val store = MohamedLoversSessionStore(s)
        val session = store.getPendingSession("2026-05-01")
        assertEquals(10, session.clickCount)
    }
}
