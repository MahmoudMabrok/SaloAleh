package tools.mo3ta.salo.data.dhikr

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.first
import tools.mo3ta.salo.domain.DHIKR_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.DhikrChallengeDayStats

class DhikrChallengeFirebaseClient {

    private val log = Logger.withTag("DhikrChallengeFirebase")

    fun isConfigured(): Boolean = runCatching { Firebase.database }.isSuccess

    suspend fun writeUserDay(
        dateKey: String,
        uid: String,
        count: Int,
        countryCode: String,
    ): Result<Unit> {
        log.d { "writeUserDay[$dateKey/$uid] count=$count" }
        return runCatching {
            Firebase.database.reference(userPath(dateKey, uid)).updateChildren(
                mapOf(
                    COUNT_KEY to count.coerceAtLeast(0),
                    DATA_KEY to mapOf(
                        UID_KEY to uid,
                        DATE_KEY to dateKey,
                        COUNTRY_CODE_KEY to countryCode,
                        GOAL_KEY to DHIKR_CHALLENGE_DAILY_GOAL,
                        COMPLETED_KEY to (count >= DHIKR_CHALLENGE_DAILY_GOAL),
                        UPDATED_AT_KEY to ServerValue.TIMESTAMP,
                    ),
                ),
            )
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "writeUserDay[$dateKey/$uid] ok" } },
                onFailure = { log.e(it) { "writeUserDay[$dateKey/$uid] failed" } },
            )
        }
    }

    suspend fun fetchUserCount(dateKey: String, uid: String): Result<Int?> {
        log.d { "fetchUserCount[$dateKey/$uid]" }
        return runCatching {
            val snapshot = Firebase.database.reference(userPath(dateKey, uid))
                .child(COUNT_KEY)
                .valueEvents
                .first()
            if (!snapshot.exists) null else (snapshot.value as? Number)?.toInt()
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchUserCount[$dateKey/$uid]=$it" } },
                onFailure = { log.e(it) { "fetchUserCount[$dateKey/$uid] failed" } },
            )
        }
    }

    suspend fun fetchDayStats(dateKey: String, uid: String): Result<DhikrChallengeDayStats> {
        log.d { "fetchDayStats[$dateKey/$uid]" }
        return runCatching {
            val dayRef = Firebase.database.reference(dayPath(dateKey))
            val rankSnapshot = dayRef.child(USERS_PATH).child(uid).child(RANK_KEY)
                .valueEvents
                .first()
            val participantCountSnapshot = dayRef.child(PARTICIPANT_COUNT_KEY)
                .valueEvents
                .first()
            val totalTodayDhikrSnapshot = dayRef.child(TOTAL_TODAY_DHIKR_KEY)
                .valueEvents
                .first()

            DhikrChallengeDayStats(
                rank = (rankSnapshot.value as? Number)?.toInt() ?: 0,
                participantCount = (participantCountSnapshot.value as? Number)?.toInt() ?: 0,
                totalTodayDhikr = (totalTodayDhikrSnapshot.value as? Number)?.toInt() ?: 0,
            )
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchDayStats[$dateKey/$uid] rank=${it.rank} participants=${it.participantCount} totalToday=${it.totalTodayDhikr}" } },
                onFailure = { log.e(it) { "fetchDayStats[$dateKey/$uid] failed" } },
            )
        }
    }

    private fun dayPath(dateKey: String) = "$ROOT_PATH/$dateKey"
    private fun usersPath(dateKey: String) = "$ROOT_PATH/$dateKey/$USERS_PATH"
    private fun userPath(dateKey: String, uid: String) = "${usersPath(dateKey)}/$uid"

    private companion object {
        const val ROOT_PATH = "100_challenge"
        const val USERS_PATH = "users"
        const val COUNT_KEY = "count"
        const val RANK_KEY = "rank"
        const val DATA_KEY = "data"
        const val UID_KEY = "uid"
        const val DATE_KEY = "date"
        const val COUNTRY_CODE_KEY = "countryCode"
        const val GOAL_KEY = "goal"
        const val COMPLETED_KEY = "completed"
        const val UPDATED_AT_KEY = "updatedAt"
        const val PARTICIPANT_COUNT_KEY = "participantCount"
        const val TOTAL_TODAY_DHIKR_KEY = "totalTodayDhikr"
    }
}
