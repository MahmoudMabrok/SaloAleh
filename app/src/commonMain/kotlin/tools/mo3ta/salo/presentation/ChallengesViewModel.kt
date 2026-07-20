package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.data.engagement.ChallengeBadgeStore
import tools.mo3ta.salo.domain.ChallengeType

data class ChallengesTotals(
    val dhikr: Int = 0,
    val baqiyat: Int = 0,
    val istighfar: Int = 0,
    val zabad: Int = 0,
    val ghars: Int = 0,
    val quran: Int = 0,
    val albaqara: Int = 0,
)

/**
 * All-time community totals per challenge (cumulative across every day). Server-maintained by
 * `generate-stats.js` at each challenge root and client-read-only.
 */
data class ChallengesOverallTotals(
    val dhikr: Int = 0,
    val baqiyat: Int = 0,
    val istighfar: Int = 0,
    val zabad: Int = 0,
    val ghars: Int = 0,
    val quran: Int = 0,
    val albaqara: Int = 0,
)

class ChallengesViewModel(
    private val challengeBadgeStore: ChallengeBadgeStore,
) : ViewModel() {

    private val cairoZone = TimeZone.of("Africa/Cairo")

    private val _totals = MutableStateFlow(ChallengesTotals())
    val totals: StateFlow<ChallengesTotals> = _totals.asStateFlow()

    private val _overallTotals = MutableStateFlow(ChallengesOverallTotals())
    val overallTotals: StateFlow<ChallengesOverallTotals> = _overallTotals.asStateFlow()

    // Per-challenge live daily streaks (local, synchronous) shown as a pill on each card.
    private val _streaks = MutableStateFlow(challengeBadgeStore.getCurrentStreaks(Clock.System.todayIn(cairoZone)))
    val streaks: StateFlow<Map<ChallengeType, Int>> = _streaks.asStateFlow()

    // Challenges the user already participated in today (Cairo), for the card border highlight.
    private val _participatedToday = MutableStateFlow(challengeBadgeStore.getActiveChallenges(Clock.System.todayIn(cairoZone)))
    val participatedToday: StateFlow<Set<ChallengeType>> = _participatedToday.asStateFlow()

    fun onScreenEntered() {
        val today = Clock.System.todayIn(cairoZone)
        _streaks.value = challengeBadgeStore.getCurrentStreaks(today)
        _participatedToday.value = challengeBadgeStore.getActiveChallenges(today)
        viewModelScope.launch {
            val dateKey = Clock.System.todayIn(cairoZone).toString()
            val db = runCatching { Firebase.database }.getOrNull() ?: return@launch

            val dhikr = async { readTotal(db, "100_challenge/$dateKey/totalTodayDhikr") }
            val baqiyat = async { readTotal(db, "baqiyat_saliha/$dateKey/totalTodayBaqiyat") }
            val istighfar = async { readTotal(db, "istighfar_challenge/$dateKey/totalTodayIstighfar") }
            val zabad = async { readTotal(db, "zabad_challenge/$dateKey/totalTodayZabad") }
            val ghars = async { readTotal(db, "ghars_challenge/$dateKey/totalTodayGhars") }
            val quran = async { readTotal(db, "quran_challenge/$dateKey/totalTodayQuran") }
            val albaqara = async { readTotal(db, "albaqara_challenge/$dateKey/totalTodayAlBaqara") }

            // All-time community totals (challenge root, not per-day).
            val dhikrAll = async { readTotal(db, "100_challenge/totalDhkr") }
            val baqiyatAll = async { readTotal(db, "baqiyat_saliha/totalBaqiyat") }
            val istighfarAll = async { readTotal(db, "istighfar_challenge/totalIstighfar") }
            val zabadAll = async { readTotal(db, "zabad_challenge/totalZabad") }
            val gharsAll = async { readTotal(db, "ghars_challenge/totalGhars") }
            val quranAll = async { readTotal(db, "quran_challenge/totalQuran") }
            val albaqaraAll = async { readTotal(db, "albaqara_challenge/totalAlBaqara") }

            _totals.update {
                ChallengesTotals(
                    dhikr = dhikr.await(),
                    baqiyat = baqiyat.await(),
                    istighfar = istighfar.await(),
                    zabad = zabad.await(),
                    ghars = ghars.await(),
                    quran = quran.await(),
                    albaqara = albaqara.await(),
                )
            }

            _overallTotals.update {
                ChallengesOverallTotals(
                    dhikr = dhikrAll.await(),
                    baqiyat = baqiyatAll.await(),
                    istighfar = istighfarAll.await(),
                    zabad = zabadAll.await(),
                    ghars = gharsAll.await(),
                    quran = quranAll.await(),
                    albaqara = albaqaraAll.await(),
                )
            }
        }
    }

    private suspend fun readTotal(db: dev.gitlive.firebase.database.FirebaseDatabase, path: String): Int {
        return runCatching {
            (db.reference(path).valueEvents.first().value as? Number)?.toInt() ?: 0
        }.getOrDefault(0)
    }
}
