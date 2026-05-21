package tools.mo3ta.salo.domain

import tools.mo3ta.salo.presentation.MohamedLoversLeaderboardEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class RankChangeTest {

    private fun entry(
        rank: Int,
        rankChange: String = "",
    ) = MohamedLoversLeaderboardEntry(
        rank = rank,
        displayTag = "EG • ABC123",
        totalCount = 100,
        isCurrentUser = false,
        rankChange = rankChange,
    )

    @Test
    fun displayedRank_shows_hash_prefix_when_ranked() {
        assertEquals("#3 ", entry(rank = 3).displayedRank)
    }

    @Test
    fun displayedRank_empty_when_rank_zero() {
        assertEquals("", entry(rank = 0).displayedRank)
    }

    @Test
    fun rankChange_defaults_to_empty_string() {
        assertEquals("", entry(rank = 1).rankChange)
    }

    @Test
    fun rankChange_preserves_up_value() {
        assertEquals("up", entry(rank = 2, rankChange = "up").rankChange)
    }

    @Test
    fun rankChange_preserves_down_value() {
        assertEquals("down", entry(rank = 3, rankChange = "down").rankChange)
    }

    @Test
    fun rankChange_preserves_same_value() {
        assertEquals("same", entry(rank = 1, rankChange = "same").rankChange)
    }

    @Test
    fun rankChange_preserves_new_value() {
        assertEquals("new", entry(rank = 5, rankChange = "new").rankChange)
    }

    @Test
    fun firebaseLeaderboardEntry_carries_rankChange() {
        val fbEntry = FirebaseLeaderboardEntry(
            rank = 1,
            uid = "abc",
            score = 500,
            countryCode = "EG",
            rankChange = "up",
        )
        assertEquals("up", fbEntry.rankChange)
    }

    @Test
    fun firebaseLeaderboardEntry_rankChange_defaults_empty() {
        val fbEntry = FirebaseLeaderboardEntry(rank = 1, uid = "abc", score = 500)
        assertEquals("", fbEntry.rankChange)
    }
}
