package tools.mo3ta.salo.data.dhikr

import kotlin.test.Test
import kotlin.test.assertEquals

class DhikrChallengeFirebaseClientTest {

    @Test
    fun parseDhikrLeaderboardEntries_acceptsFirebaseArrayShape() {
        val entries = parseDhikrLeaderboardEntries(
            listOf(
                mapOf(
                    "count" to 213,
                    "countryCode" to "EG",
                    "rank" to 1,
                    "rankChange" to "same",
                    "uid" to "rank-one",
                    "nickname" to "ذاكر",
                ),
                mapOf(
                    "count" to 200,
                    "countryCode" to "EG",
                    "rank" to 2,
                    "rankChange" to "down",
                    "uid" to "rank-two",
                ),
            ),
        )

        assertEquals(2, entries.size)
        assertEquals("rank-one", entries[0].uid)
        assertEquals(213, entries[0].count)
        assertEquals(1, entries[0].rank)
        assertEquals("same", entries[0].rankChange)
        assertEquals("ذاكر", entries[0].nickname)
        assertEquals("rank-two", entries[1].uid)
    }

    @Test
    fun parseDhikrLeaderboardEntries_keepsMapFallbackOrderedByNumericKey() {
        val entries = parseDhikrLeaderboardEntries(
            mapOf(
                "2" to mapOf(
                    "count" to 102,
                    "countryCode" to "EG",
                    "rank" to 3,
                    "uid" to "rank-three",
                ),
                "0" to mapOf(
                    "count" to 213,
                    "countryCode" to "EG",
                    "rank" to 1,
                    "uid" to "rank-one",
                ),
            ),
        )

        assertEquals(listOf("rank-one", "rank-three"), entries.map { it.uid })
        assertEquals("same", entries[0].rankChange)
    }
}
