package tools.mo3ta.salo.ui

import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.Achievement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AchievementsScreenTest {

    @Test
    fun winnerCodeForCopyButton_withWinnerCode_returnsTrimmedCode() {
        val achievement = Achievement.RankAchievement(
            roundKey = "round-2026-04-30",
            rank = 1,
            earnedDate = LocalDate(2026, 4, 30),
            score = 1200,
            winnerCode = "  WINNER-CODE-001  ",
        )

        assertEquals("WINNER-CODE-001", winnerCodeForCopyButton(achievement))
    }

    @Test
    fun winnerCodeForCopyButton_withoutWinnerCode_hidesCopyButton() {
        val achievement = Achievement.RankAchievement(
            roundKey = "round-2026-04-30",
            rank = 1,
            earnedDate = LocalDate(2026, 4, 30),
            score = 1200,
            winnerCode = "   ",
        )

        assertNull(winnerCodeForCopyButton(achievement))
    }

}
