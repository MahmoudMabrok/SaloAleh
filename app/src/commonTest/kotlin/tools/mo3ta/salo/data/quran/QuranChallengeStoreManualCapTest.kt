package tools.mo3ta.salo.data.quran

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.QURAN_MANUAL_DAILY_CAP
import kotlin.test.Test
import kotlin.test.assertEquals

class QuranChallengeStoreManualCapTest {

    private val day = LocalDate(2026, 7, 12)

    @Test fun quranManualCapIs700() {
        assertEquals(700, QURAN_MANUAL_DAILY_CAP)
    }

    @Test fun regularTapsAreNotCountedAgainstManualCap() {
        val store = QuranChallengeStore(MapSettings())
        repeat(5) { store.incrementToday(day) }
        assertEquals(QURAN_MANUAL_DAILY_CAP, store.manualRemainingToday(day))
        assertEquals(5, store.todayCount(day))
    }

    @Test fun addIsClampedToDailyCap() {
        val store = QuranChallengeStore(MapSettings())
        // Attempting to add above the cap only applies the remaining allowance.
        assertEquals(QURAN_MANUAL_DAILY_CAP, store.addToday(day, QURAN_MANUAL_DAILY_CAP + 500))
        assertEquals(0, store.manualRemainingToday(day))
        // A further add is rejected — cap already exhausted.
        assertEquals(QURAN_MANUAL_DAILY_CAP, store.addToday(day, 100))
    }

    @Test fun manualRemainingTracksUsage() {
        val store = QuranChallengeStore(MapSettings())
        store.addToday(day, 300)
        assertEquals(QURAN_MANUAL_DAILY_CAP - 300, store.manualRemainingToday(day))
    }

    @Test fun subtractRefundsManualCapLedger() {
        val store = QuranChallengeStore(MapSettings())
        store.addToday(day, QURAN_MANUAL_DAILY_CAP)
        assertEquals(0, store.manualRemainingToday(day))
        store.subtractToday(day, 200)
        assertEquals(200, store.manualRemainingToday(day))
    }

    @Test fun manualCapResetsOnNewDay() {
        val store = QuranChallengeStore(MapSettings())
        store.addToday(day, QURAN_MANUAL_DAILY_CAP)
        assertEquals(0, store.manualRemainingToday(day))
        val nextDay = LocalDate(2026, 7, 13)
        assertEquals(QURAN_MANUAL_DAILY_CAP, store.manualRemainingToday(nextDay))
        assertEquals(50, store.addToday(nextDay, 50))
    }
}
