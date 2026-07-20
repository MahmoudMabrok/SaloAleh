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
import tools.mo3ta.salo.domain.HeroesBoard
import tools.mo3ta.salo.domain.parseHeroesBoard

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

    // "Hero of yesterday" — the day's champions across every challenge, read-only from
    // RTDB `mohamed_lovers/heroes` (server-written, overwritten daily). Prefetched on screen
    // entry so the sheet opens instantly; null when there are no champions yet.
    private val _heroesBoard = MutableStateFlow<HeroesBoard?>(null)
    val heroesBoard: StateFlow<HeroesBoard?> = _heroesBoard.asStateFlow()

    private val _heroesLoading = MutableStateFlow(false)
    val heroesLoading: StateFlow<Boolean> = _heroesLoading.asStateFlow()

    private val _showHeroesSheet = MutableStateFlow(false)
    val showHeroesSheet: StateFlow<Boolean> = _showHeroesSheet.asStateFlow()

    fun openHeroesSheet() {
        _showHeroesSheet.value = true
    }

    fun dismissHeroesSheet() {
        _showHeroesSheet.value = false
    }

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
        _heroesLoading.value = _heroesBoard.value == null
        viewModelScope.launch {
            val dateKey = Clock.System.todayIn(cairoZone).toString()
            val db = runCatching { Firebase.database }.getOrNull() ?: run {
                _heroesLoading.value = false
                return@launch
            }

            val heroes = async { readHeroes(db) }

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

            _heroesBoard.value = heroes.await()
            _heroesLoading.value = false
        }
    }

    private suspend fun readHeroes(db: dev.gitlive.firebase.database.FirebaseDatabase): HeroesBoard? {
        return runCatching {
            val snapshot = db.reference("mohamed_lovers/heroes").valueEvents.first()
            if (!snapshot.exists) null else parseHeroesBoard(snapshot.value)?.takeIf { it.hasAny }
        }.getOrNull()
    }

    private suspend fun readTotal(db: dev.gitlive.firebase.database.FirebaseDatabase, path: String): Int {
        return runCatching {
            (db.reference(path).valueEvents.first().value as? Number)?.toInt() ?: 0
        }.getOrDefault(0)
    }
}
