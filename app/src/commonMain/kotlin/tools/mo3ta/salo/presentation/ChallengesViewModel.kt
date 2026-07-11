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

data class ChallengesTotals(
    val dhikr: Int = 0,
    val baqiyat: Int = 0,
    val istighfar: Int = 0,
    val quran: Int = 0,
)

class ChallengesViewModel : ViewModel() {

    private val cairoZone = TimeZone.of("Africa/Cairo")

    private val _totals = MutableStateFlow(ChallengesTotals())
    val totals: StateFlow<ChallengesTotals> = _totals.asStateFlow()

    fun onScreenEntered() {
        viewModelScope.launch {
            val dateKey = Clock.System.todayIn(cairoZone).toString()
            val db = runCatching { Firebase.database }.getOrNull() ?: return@launch

            val dhikr = async { readTotal(db, "100_challenge/$dateKey/totalTodayDhikr") }
            val baqiyat = async { readTotal(db, "baqiyat_saliha/$dateKey/totalTodayBaqiyat") }
            val istighfar = async { readTotal(db, "istighfar_challenge/$dateKey/totalTodayIstighfar") }
            val quran = async { readTotal(db, "quran_challenge/$dateKey/totalTodayQuran") }

            _totals.update {
                ChallengesTotals(
                    dhikr = dhikr.await(),
                    baqiyat = baqiyat.await(),
                    istighfar = istighfar.await(),
                    quran = quran.await(),
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
