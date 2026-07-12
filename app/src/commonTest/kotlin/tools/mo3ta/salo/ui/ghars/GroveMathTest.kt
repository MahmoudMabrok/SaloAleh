package tools.mo3ta.salo.ui.ghars

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroveMathTest {

    @Test fun palmParamsAreDeterministic() {
        repeat(60) { index ->
            assertEquals(palmParams(index), palmParams(index), "palm #$index must be identical every call")
        }
    }

    @Test fun differentPalmsDiffer() {
        // Not a strict requirement, but the seed should spread indices apart.
        assertTrue(palmParams(3) != palmParams(4))
        assertTrue(palmParams(11) != palmParams(12))
    }

    @Test fun everyTwentyFifthPalmBearsDates() {
        assertTrue(palmParams(24).bearsDates) // 25th palm (index 24)
        assertTrue(palmParams(49).bearsDates) // 50th palm
        assertFalse(palmParams(0).bearsDates)
        assertFalse(palmParams(23).bearsDates)
    }

    @Test fun frondCountStaysInRange() {
        repeat(100) { index ->
            val fronds = palmParams(index).frondCount
            assertTrue(fronds in 12..16, "fronds=$fronds out of range for #$index")
        }
    }

    @Test fun shownPalmsNeverExceedsGroveSize() {
        for (count in 0..500) {
            assertTrue(shownPalms(count) <= GROVE_SIZE, "shownPalms($count) exceeded $GROVE_SIZE")
        }
    }

    @Test fun wrapArithmetic() {
        assertEquals(0, shownPalms(0))
        assertEquals(1, shownPalms(1))
        assertEquals(25, shownPalms(25))
        assertEquals(1, shownPalms(26)) // the wrap: fresh soil after a completed grove
        assertEquals(25, shownPalms(50))

        assertEquals(0, groveStartIndex(1))
        assertEquals(0, groveStartIndex(25))
        assertEquals(25, groveStartIndex(26))
        assertEquals(25, groveStartIndex(50))

        assertEquals(0, completedGroves(24))
        assertEquals(1, completedGroves(25))
        assertEquals(1, completedGroves(26))
        assertEquals(3, completedGroves(83))

        assertFalse(completesGrove(25))
        assertTrue(completesGrove(26)) // first tasbeeh after a full grove sends it receding
        assertTrue(completesGrove(51))
        assertFalse(completesGrove(1))
    }

    @Test fun rowOccupancySumsToShownAndNeverOverflowsRows() {
        for (shown in 0..GROVE_SIZE) {
            val take = rowOccupancy(shown)
            assertEquals(shown, take.sum(), "row occupancy must account for every visible palm")
            for (b in take.indices) {
                assertTrue(take[b] <= ROW_CAPACITY[b], "row $b overflowed its capacity")
            }
        }
    }

    @Test fun fullGroveFillsAllRows() {
        val take = rowOccupancy(GROVE_SIZE)
        assertEquals(ROW_CAPACITY.toList(), take.toList())
        assertEquals(GROVE_SIZE, ROW_CAPACITY.sum())
    }

    @Test fun rowsFillFrontFirst() {
        // With 10 palms: the front row is full (slots 0..6) and the row behind has taken 3.
        val take = rowOccupancy(10)
        assertEquals(listOf(7, 3, 0), take.toList())
        assertEquals(0, rowStart(0))
        assertEquals(7, rowStart(1))
        assertEquals(15, rowStart(2))
    }

    @Test fun aPlantedPalmNeverMoves() {
        // The whole point of the front-first fill: slot j keeps its row and its place in
        // that row no matter how many palms are planted after it.
        for (j in 0 until GROVE_SIZE) {
            val band = rowOfSlot(j)
            val place = slotInRow(j)
            for (shown in (j + 1)..GROVE_SIZE) {
                val take = rowOccupancy(shown)
                assertTrue(place < take[band], "palm $j left row $band once $shown palms were up")
                assertEquals(band, rowOfSlot(j), "palm $j changed row at $shown palms")
                assertEquals(place, slotInRow(j), "palm $j slid within its row at $shown palms")
            }
        }
    }

    @Test fun newestPalmIsTheLastSlotOfTheCurrentRow() {
        // Palm 8 (slot 7) opens the middle row; palm 15 (slot 14) is the last of that row.
        assertEquals(0, rowOfSlot(6))
        assertEquals(1, rowOfSlot(7))
        assertEquals(0, slotInRow(7))
        assertEquals(1, rowOfSlot(14))
        assertEquals(7, slotInRow(14))
        assertEquals(2, rowOfSlot(15))
        assertEquals(9, slotInRow(GROVE_SIZE - 1))
    }

    @Test fun palmsInARowNeverShareASlot() {
        for (band in ROW_CAPACITY.indices) {
            val lo = rowStart(band)
            val xs = (lo until lo + ROW_CAPACITY[band]).map { palmXFraction(it, jitter = 0f) }
            assertEquals(xs.size, xs.toSet().size, "row $band stacked two palms on one spot")
            assertEquals(xs.sorted(), xs, "row $band does not fill left to right")
        }
    }
}
