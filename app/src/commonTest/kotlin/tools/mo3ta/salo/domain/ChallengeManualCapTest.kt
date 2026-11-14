package tools.mo3ta.salo.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ChallengeManualCapTest {

    @Test
    fun baseCapsArePerChallenge() {
        assertEquals(CHALLENGE_MANUAL_DAILY_CAP, ChallengeManualCap.baseCapFor(ChallengeType.DHIKR))
        assertEquals(BAQIYAT_MANUAL_DAILY_CAP, ChallengeManualCap.baseCapFor(ChallengeType.BAQIYAT))
    }

    @Test
    fun streaksBelowTheMilestoneKeepTheBaseCap() {
        for (streak in listOf(0, 1, 29)) {
            assertEquals(
                CHALLENGE_MANUAL_DAILY_CAP,
                ChallengeManualCap.dailyCap(ChallengeType.DHIKR, streak),
                "dhikr streak=$streak",
            )
            assertEquals(
                BAQIYAT_MANUAL_DAILY_CAP,
                ChallengeManualCap.dailyCap(ChallengeType.BAQIYAT, streak),
                "baqiyat streak=$streak",
            )
        }
    }

    @Test
    fun milestoneAndHigherStreaksUnlockTenThousand() {
        for (streak in listOf(30, 60, 90, 365)) {
            assertEquals(
                MILESTONE_MANUAL_DAILY_CAP,
                ChallengeManualCap.dailyCap(ChallengeType.DHIKR, streak),
                "dhikr streak=$streak",
            )
            assertEquals(
                MILESTONE_MANUAL_DAILY_CAP,
                ChallengeManualCap.dailyCap(ChallengeType.BAQIYAT, streak),
                "baqiyat streak=$streak",
            )
        }
    }

    @Test
    fun brokenStreakDropsBackToTheBaseCap() {
        assertEquals(MILESTONE_MANUAL_DAILY_CAP, ChallengeManualCap.dailyCap(ChallengeType.QURAN, 30))
        assertEquals(CHALLENGE_MANUAL_DAILY_CAP, ChallengeManualCap.dailyCap(ChallengeType.QURAN, 0))
    }

    @Test
    fun eligibilityIsIndependentPerChallenge() {
        // The same user can be eligible in one challenge and not in another.
        assertEquals(MILESTONE_MANUAL_DAILY_CAP, ChallengeManualCap.dailyCap(ChallengeType.DHIKR, 30))
        assertEquals(CHALLENGE_MANUAL_DAILY_CAP, ChallengeManualCap.dailyCap(ChallengeType.QURAN, 29))
    }
}
