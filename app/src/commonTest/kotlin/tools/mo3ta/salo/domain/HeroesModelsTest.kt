package tools.mo3ta.salo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeroesModelsTest {

    private fun entry(rank: Int, name: String, count: Int, country: String = "EG") =
        mapOf("rank" to rank, "name" to name, "count" to count, "countryCode" to country)

    @Test
    fun parses_all_challenges_in_fixed_order() {
        val raw = mapOf(
            "date" to "2026-07-10",
            "updatedAt" to "2026-07-10T21:45:00.000Z",
            "challenges" to mapOf(
                "salawat" to listOf(entry(1, "Ali", 500), entry(2, "Sara", 300)),
                "dhikr" to listOf(entry(1, "Omar", 120)),
                "baqiyat" to emptyList<Any?>(),
                "istighfar" to listOf(entry(1, "Hana", 90)),
            ),
        )

        val board = parseHeroesBoard(raw)!!
        assertEquals("2026-07-10", board.date)
        assertTrue(board.hasAny)
        assertEquals(
            listOf(
                HeroChallengeType.Salawat,
                HeroChallengeType.Dhikr,
                HeroChallengeType.Baqiyat,
                HeroChallengeType.Istighfar,
                HeroChallengeType.Quran,
                HeroChallengeType.Zabad,
                HeroChallengeType.Ghars,
            ),
            board.challenges.map { it.type },
        )
        assertEquals(2, board.challenges[0].entries.size)
        assertEquals("Ali", board.challenges[0].entries[0].name)
        assertEquals(500, board.challenges[0].entries[0].count)
        assertTrue(board.challenges[2].entries.isEmpty())
    }

    @Test
    fun parses_zero_indexed_map_form_and_sorts_by_rank() {
        // RTDB round-trips arrays as 0-indexed maps, possibly out of order.
        val raw = mapOf(
            "date" to "2026-07-10",
            "challenges" to mapOf(
                "dhikr" to mapOf(
                    "1" to entry(2, "Second", 80),
                    "0" to entry(1, "First", 100),
                    "2" to entry(3, "Third", 60),
                ),
            ),
        )

        val board = parseHeroesBoard(raw)!!
        val dhikr = board.challenges.first { it.type == HeroChallengeType.Dhikr }
        assertEquals(listOf("First", "Second", "Third"), dhikr.entries.map { it.name })
    }

    @Test
    fun skips_entries_without_a_name() {
        val raw = mapOf(
            "date" to "2026-07-10",
            "challenges" to mapOf(
                "salawat" to listOf(
                    entry(1, "Valid", 10),
                    mapOf("rank" to 2, "count" to 5), // no name → dropped
                ),
            ),
        )
        val board = parseHeroesBoard(raw)!!
        val salawat = board.challenges.first { it.type == HeroChallengeType.Salawat }
        assertEquals(1, salawat.entries.size)
        assertEquals("Valid", salawat.entries[0].name)
    }

    @Test
    fun empty_board_reports_no_heroes() {
        val raw = mapOf("date" to "2026-07-10", "challenges" to emptyMap<Any?, Any?>())
        val board = parseHeroesBoard(raw)!!
        assertFalse(board.hasAny)
        assertEquals(HeroChallengeType.entries.size, board.challenges.size)
    }

    @Test
    fun malformed_value_returns_null() {
        assertNull(parseHeroesBoard(null))
        assertNull(parseHeroesBoard("not-a-map"))
    }
}
