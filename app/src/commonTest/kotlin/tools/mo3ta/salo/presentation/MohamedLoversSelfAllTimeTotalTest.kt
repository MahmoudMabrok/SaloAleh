package tools.mo3ta.salo.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MohamedLoversSelfAllTimeTotalTest {

    @Test
    fun nullUntilClosedRoundsTotalIsFetched() {
        assertNull(MohamedLoversUiState(syncedTotal = 500, sessionClicks = 20).selfAllTimeTotal)
    }

    @Test
    fun addsCurrentRoundSyncedAndPendingToClosedRounds() {
        val state = MohamedLoversUiState(selfClosedRoundsTotal = 100_000L, syncedTotal = 3_000, sessionClicks = 45)
        assertEquals(103_045L, state.selfAllTimeTotal)
    }
}
