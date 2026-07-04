package tools.mo3ta.salo.data.istighfar

import kotlin.test.Test
import kotlin.test.assertEquals

class IstighfarChallengeFirebaseClientTest {

    @Test
    fun parseIstighfarLeaderboardEntries_acceptsFirebaseArrayShape() {
        val entries = parseIstighfarLeaderboardEntries(
            listOf(
                mapOf(
                    "count" to 70,
                    "countryCode" to "EG",
                    "rank" to 1,
                    "rankChange" to "same",
                    "uid" to "rank-one",
                    "nickname" to "تائب",
                ),
                mapOf(
                    "count" to 42,
                    "countryCode" to "EG",
                    "rank" to 2,
                    "rankChange" to "down",
                    "uid" to "rank-two",
                ),
            ),
        )

        assertEquals(2, entries.size)
        assertEquals("rank-one", entries[0].uid)
        assertEquals(70, entries[0].count)
        assertEquals(1, entries[0].rank)
        assertEquals("same", entries[0].rankChange)
        assertEquals("تائب", entries[0].nickname)
        assertEquals("rank-two", entries[1].uid)
    }

    @Test
    fun parseIstighfarLeaderboardEntries_keepsMapFallbackOrderedByNumericKey() {
        val entries = parseIstighfarLeaderboardEntries(
            mapOf(
                "2" to mapOf(
                    "count" to 40,
                    "countryCode" to "EG",
                    "rank" to 3,
                    "uid" to "rank-three",
                ),
                "0" to mapOf(
                    "count" to 70,
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
