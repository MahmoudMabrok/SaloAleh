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

    @Test fun everyFiftiethPalmBearsDates() {
        assertTrue(palmParams(49).bearsDates) // 50th palm (index 49) — completes a grove
        assertTrue(palmParams(99).bearsDates) // 100th palm
        assertFalse(palmParams(0).bearsDates)
        assertFalse(palmParams(48).bearsDates)
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
        assertEquals(50, shownPalms(50))
        assertEquals(1, shownPalms(51)) // the wrap: fresh soil after a completed grove
        assertEquals(50, shownPalms(100))

        assertEquals(0, groveStartIndex(1))
        assertEquals(0, groveStartIndex(50))
        assertEquals(50, groveStartIndex(51))
        assertEquals(50, groveStartIndex(100))

        assertEquals(0, completedGroves(49))
        assertEquals(1, completedGroves(50))
        assertEquals(1, completedGroves(51))
        assertEquals(3, completedGroves(150))

        assertFalse(completesGrove(50))
        assertTrue(completesGrove(51)) // first tasbeeh after a full grove sends it receding
        assertTrue(completesGrove(101))
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
        // With 10 palms: the front row is full (slots 0..7) and the row behind has taken 2.
        val take = rowOccupancy(10)
        assertEquals(listOf(8, 2, 0, 0), take.toList())
        assertEquals(0, rowStart(0))
        assertEquals(8, rowStart(1))
        assertEquals(18, rowStart(2))
        assertEquals(32, rowStart(3))
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
        // Rows are [8, 10, 14, 18]: band 0 = slots 0..7, band 1 = 8..17, band 2 = 18..31, band 3 = 32..49.
        assertEquals(0, rowOfSlot(7))
        assertEquals(1, rowOfSlot(8))
        assertEquals(0, slotInRow(8))
        assertEquals(1, rowOfSlot(17))
        assertEquals(9, slotInRow(17))
        assertEquals(2, rowOfSlot(18))
        assertEquals(3, rowOfSlot(GROVE_SIZE - 1))
        assertEquals(17, slotInRow(GROVE_SIZE - 1))
    }

    @Test fun gardensOwnedIsLifetimeFloorDividedByGroveSize() {
        assertEquals(0, gardensOwned(0))
        assertEquals(0, gardensOwned(49))
        assertEquals(1, gardensOwned(50))
        assertEquals(1, gardensOwned(99))
        assertEquals(2, gardensOwned(100)) // the daily goal is exactly two gardens
        assertEquals(3, gardensOwned(160))
        assertEquals(0, gardensOwned(-5))
    }

    @Test fun everyGardenEndsOnADatePalm() {
        // A garden is a full grove, so its 50th palm (slot 49) must always be the date-bearer.
        for (garden in 0 until 6) {
            val lastSlotIndex = gardenFirstPalmIndex(garden) + (GROVE_SIZE - 1)
            assertTrue(palmParams(lastSlotIndex).bearsDates, "garden $garden did not end on a date palm")
            assertEquals(garden * GROVE_SIZE, gardenFirstPalmIndex(garden))
        }
    }

    @Test fun gardensTileWithoutGapsOrOverlap() {
        // Walking garden g must reproduce exactly the palms grown as count g*GROVE_SIZE+1 .. (g+1)*GROVE_SIZE.
        for (garden in 0 until 4) {
            val first = gardenFirstPalmIndex(garden)
            val next = gardenFirstPalmIndex(garden + 1)
            assertEquals(GROVE_SIZE, next - first, "garden $garden is not $GROVE_SIZE palms wide")
        }
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
