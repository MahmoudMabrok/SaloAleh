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

    @Test fun frontRowHoldsTheMostRecentPalms() {
        // With 10 palms, the front row (band 0) keeps the 7 newest slots.
        val shown = 10
        val take = rowOccupancy(shown)
        val frontStart = rowStart(take, shown, 0)
        assertEquals(shown - take[0], frontStart)
        assertEquals(3, frontStart) // slots 3..9 up front, 0..2 behind
    }
}
