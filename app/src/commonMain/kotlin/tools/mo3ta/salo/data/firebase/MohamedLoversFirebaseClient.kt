package tools.mo3ta.salo.data.firebase

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.FirebaseLeaderboard
import tools.mo3ta.salo.domain.FirebaseLeaderboardEntry
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_TOP_LIMIT
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE
import tools.mo3ta.salo.domain.MohamedLoversPlayer
import tools.mo3ta.salo.domain.UserAchievement

class MohamedLoversFirebaseClient(
    private val sessionStore: MohamedLoversSessionStore,
    private val mirror: FirestoreMirror,
) : MohamedLoversFirebaseApi {

    private val log = Logger.withTag("FirebaseClient")

    override fun isConfigured(): Boolean {
        val result = runCatching { Firebase.database }.isSuccess
        log.d { "isConfigured=$result" }
        return result
    }

    override suspend fun ensureSignedInAnonymously(): Result<String> {
        log.d { "ensureSignedInAnonymously: getting/creating uid" }
        return runCatching { sessionStore.getOrCreateUid() }
            .also { result ->
                result.fold(
                    onSuccess = { log.d { "uid=$it" } },
                    onFailure = { log.e(it) { "ensureSignedInAnonymously failed" } },
                )
            }
    }

    override fun observeSelfPlayer(
        roundKey: String,
        uid: String,
    ): Flow<Result<MohamedLoversPlayer?>> =
        Firebase.database.reference(playersPath(roundKey)).child(uid)
            .valueEvents
            .map { snapshot ->
                runCatching { snapshot.takeIf { it.exists }?.toPlayer() }
            }
            .onEach { result ->
                result.fold(
                    onSuccess = { log.d { "observeSelfPlayer[$roundKey/$uid]: $it" } },
                    onFailure = { log.e(it) { "observeSelfPlayer[$roundKey/$uid] error" } },
                )
            }
            .catch { e ->
                log.e(e) { "observeSelfPlayer[$roundKey/$uid] flow error" }
                emit(Result.failure(e))
            }

    override suspend fun fetchRoundPlayerCount(roundKey: String): Result<Int> {
        log.d { "fetchRoundPlayerCount[$roundKey]" }
        return runCatching {
            val snapshot = Firebase.database.reference("$ROOT_PATH/$roundKey/$ROUND_PLAYER_COUNT_PATH")
                .valueEvents.first()
            (snapshot.value as? Number)?.toInt() ?: 0
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchRoundPlayerCount[$roundKey]=$it" } },
                onFailure = { log.e(it) { "fetchRoundPlayerCount[$roundKey] failed" } },
            )
        }
    }

    override suspend fun fetchRoundTotal(roundKey: String): Result<Int> {
        log.d { "fetchRoundTotal[$roundKey]" }
        return runCatching {
            val snapshot = Firebase.database.reference("$ROOT_PATH/$roundKey/$ROUND_TOTAL_PATH")
                .valueEvents.first()
            (snapshot.value as? Number)?.toInt() ?: 0
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchRoundTotal[$roundKey]=$it" } },
                onFailure = { log.e(it) { "fetchRoundTotal[$roundKey] failed" } },
            )
        }
    }

    override suspend fun fetchAllTimeTotal(): Result<Long> {
        log.d { "fetchAllTimeTotal" }
        return runCatching {
            val snapshot = Firebase.database.reference("$ROOT_PATH/$ALL_TIME_TOTAL_PATH")
                .valueEvents.first()
            (snapshot.value as? Number)?.toLong() ?: 0L
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchAllTimeTotal=$it" } },
                onFailure = { log.e(it) { "fetchAllTimeTotal failed" } },
            )
        }
    }

    override fun observeLeaderboard(roundKey: String, daily: Boolean): Flow<Result<FirebaseLeaderboard>> =
        Firebase.database.reference(leaderboardPath(roundKey, daily))
            .valueEvents
            .map { snapshot ->
                runCatching {
                    val rootMap = snapshot.value as? Map<*, *> ?: emptyMap<Any, Any>()
                    val isFinal = rootMap[IS_FINAL_KEY] as? Boolean ?: false
                    val entries = snapshot.children
                        .filter { it.key?.toIntOrNull() != null }
                        .mapNotNull { it.toLeaderboardEntry() }
                        .sortedBy { it.rank }
                    FirebaseLeaderboard(entries = entries, isFinal = isFinal)
                }
            }
            .onEach { result ->
                result.fold(
                    onSuccess = { log.d { "observeLeaderboard[$roundKey]: ${it.entries.size} entries, isFinal=${it.isFinal}" } },
                    onFailure = { log.e(it) { "observeLeaderboard[$roundKey] error" } },
                )
            }
            .catch { e ->
                log.e(e) { "observeLeaderboard[$roundKey] flow error" }
                emit(Result.failure(e))
            }

    override suspend fun incrementSession(
        roundKey: String,
        uid: String,
        delta: Int,
        countryCode: String,
    ): Result<Unit> {
        log.d { "incrementSession[$roundKey/$uid] delta=$delta country=$countryCode" }
        val safeCode = countryCode.takeIf { it.length >= 2 } ?: MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE
        val publishedName = sessionStore.getPublishedName()
        val fields = mutableMapOf<String, Any>(
            UID_KEY to uid,
            COUNTRY_CODE_KEY to safeCode,
            TOTAL_COUNT_KEY to ServerValue.increment(delta.toDouble()),
            UPDATED_AT_KEY to ServerValue.TIMESTAMP,
            NICKNAME_KEY to publishedName,
        )
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(fields)
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "incrementSession[$roundKey/$uid] ok" }
                    mirror.mirrorPlayerIncrement(roundKey, uid, delta, safeCode, publishedName)
                },
                onFailure = { log.e(it) { "incrementSession[$roundKey/$uid] failed" } },
            )
        }
    }

    override suspend fun incrementExternalCount(
        roundKey: String,
        uid: String,
        count: Int,
    ): Result<Unit> {
        log.d { "incrementExternalCount[$roundKey/$uid] count=$count" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                mapOf(TOTAL_EXTERNAL_KEY to ServerValue.increment(count.toDouble()))
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "incrementExternalCount[$roundKey/$uid] ok" }
                    mirror.mirrorExternalCount(roundKey, uid, count)
                },
                onFailure = { log.e(it) { "incrementExternalCount[$roundKey/$uid] failed" } },
            )
        }
    }

    override suspend fun resetPlayerScore(roundKey: String, uid: String): Result<Unit> {
        log.d { "resetPlayerScore[$roundKey/$uid]" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                mapOf(TOTAL_COUNT_KEY to 0)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "resetPlayerScore[$roundKey/$uid] ok" }
                    mirror.mirrorResetPlayerScore(roundKey, uid)
                },
                onFailure = { log.e(it) { "resetPlayerScore[$roundKey/$uid] failed" } },
            )
        }
    }

    override suspend fun writeUserActivity(uid: String, installDate: String, lastOpenDate: String): Result<Unit> {
        log.d { "writeUserActivity[$uid]" }
        return runCatching {
            Firebase.database.reference("$ROOT_PATH/$USERS_PATH/$uid").updateChildren(
                mapOf(
                    "installDate" to installDate,
                    "lastOpenDate" to lastOpenDate,
                )
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writeUserActivity[$uid] ok" }
                    mirror.mirrorUserActivity(uid, installDate, lastOpenDate)
                },
                onFailure = { log.e(it) { "writeUserActivity[$uid] failed" } },
            )
        }
    }

    override suspend fun writeFcmToken(uid: String, token: String): Result<Unit> {
        log.d { "writeFcmToken[$uid]" }
        return runCatching {
            Firebase.database.reference("$ROOT_PATH/$USERS_PATH/$uid").updateChildren(
                mapOf("fcmToken" to token)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writeFcmToken[$uid] ok" }
                    mirror.mirrorFcmToken(uid, token)
                },
                onFailure = { log.e(it) { "writeFcmToken[$uid] failed" } },
            )
        }
    }

    override suspend fun writeNotificationPrefs(
        uid: String,
        remindersEnabled: Boolean,
        leaderboardEnabled: Boolean,
    ): Result<Unit> {
        log.d { "writeNotificationPrefs[$uid] reminders=$remindersEnabled leaderboard=$leaderboardEnabled" }
        return runCatching {
            Firebase.database.reference("$ROOT_PATH/$USERS_PATH/$uid").updateChildren(
                mapOf(
                    REMINDER_NOTIFS_ENABLED_KEY to remindersEnabled,
                    LEADERBOARD_NOTIFS_ENABLED_KEY to leaderboardEnabled,
                )
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writeNotificationPrefs[$uid] ok" }
                    mirror.mirrorNotificationPrefs(uid, remindersEnabled, leaderboardEnabled)
                },
                onFailure = { log.e(it) { "writeNotificationPrefs[$uid] failed" } },
            )
        }
    }

    override suspend fun fetchUserAchievements(uid: String): Result<Map<String, UserAchievement>> {
        log.d { "fetchUserAchievements[$uid]" }
        return runCatching {
            val snapshot = Firebase.database
                .reference("$ROOT_PATH/$USERS_PATH/$uid/$ACHIEVEMENTS_PATH")
                .valueEvents.first()
            buildMap {
                snapshot.children.forEach { child ->
                    val roundKey = child.key ?: return@forEach
                    val map = child.value as? Map<*, *> ?: return@forEach
                    val rank = (map[RANK_KEY] as? Number)?.toInt() ?: return@forEach
                    val score = (map[SCORE_KEY] as? Number)?.toInt()
                    val winnerCode = map[WINNER_CODE_KEY] as? String ?: ""
                    put(roundKey, UserAchievement(rank = rank, score = score, winnerCode = winnerCode))
                }
            }
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchUserAchievements[$uid]: ${it.size} entries" } },
                onFailure = { log.e(it) { "fetchUserAchievements[$uid] failed" } },
            )
        }
    }

    override suspend fun setScoreMasked(roundKey: String, uid: String, masked: Boolean): Result<Unit> {
        log.d { "setScoreMasked[$roundKey/$uid] masked=$masked" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                mapOf(SCORE_MASKED_KEY to masked)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "setScoreMasked[$roundKey/$uid] ok" }
                    mirror.mirrorPlayerField(roundKey, uid, SCORE_MASKED_KEY, masked)
                },
                onFailure = { log.e(it) { "setScoreMasked[$roundKey/$uid] failed" } },
            )
        }
    }

    override suspend fun setSupporter(roundKey: String, uid: String, supporter: Boolean): Result<Unit> {
        log.d { "setSupporter[$roundKey/$uid] supporter=$supporter" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                mapOf(IS_SUPPORTER_KEY to supporter)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "setSupporter[$roundKey/$uid] ok" }
                    mirror.mirrorPlayerField(roundKey, uid, IS_SUPPORTER_KEY, supporter)
                },
                onFailure = { log.e(it) { "setSupporter[$roundKey/$uid] failed" } },
            )
        }
    }

    override suspend fun writeDailyBadge(roundKey: String, uid: String, badgeKey: String?): Result<Unit> {
        log.d { "writeDailyBadge[$roundKey/$uid] badge=$badgeKey" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                mapOf(DAILY_BADGE_KEY to badgeKey)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writeDailyBadge[$roundKey/$uid] ok" }
                    mirror.mirrorPlayerField(roundKey, uid, DAILY_BADGE_KEY, badgeKey)
                },
                onFailure = { log.e(it) { "writeDailyBadge[$roundKey/$uid] failed" } },
            )
        }
    }

    override suspend fun writeSupporterStatus(uid: String, supporter: Boolean): Result<Unit> {
        log.d { "writeSupporterStatus[$uid] supporter=$supporter" }
        return runCatching {
            Firebase.database.reference("$ROOT_PATH/$USERS_PATH/$uid").updateChildren(
                mapOf(IS_SUPPORTER_KEY to supporter)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writeSupporterStatus[$uid] ok" }
                    mirror.mirrorSupporterStatus(uid, supporter)
                },
                onFailure = { log.e(it) { "writeSupporterStatus[$uid] failed" } },
            )
        }
    }

    override suspend fun writePurchaseMetadata(
        uid: String,
        productId: String,
        productType: String,
        purchaseDate: String,
    ): Result<Unit> {
        log.d { "writePurchaseMetadata[$uid] product=$productId type=$productType date=$purchaseDate" }
        return runCatching {
            Firebase.database.reference("$ROOT_PATH/$USERS_PATH/$uid/$PURCHASES_PATH/$productId")
                .updateChildren(
                    mapOf(
                        PRODUCT_ID_KEY to productId,
                        PRODUCT_TYPE_KEY to productType,
                        PURCHASE_DATE_KEY to purchaseDate,
                    )
                )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writePurchaseMetadata[$uid/$productId] ok" }
                    mirror.mirrorPurchaseMetadata(uid, productId, productType, purchaseDate)
                },
                onFailure = { log.e(it) { "writePurchaseMetadata[$uid/$productId] failed" } },
            )
        }
    }

    override suspend fun writeNickname(roundKey: String, uid: String, nickname: String): Result<Unit> {
        log.d { "writeNickname[$roundKey/$uid] nickname=$nickname" }
        val value = nickname.ifBlank { "" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                mapOf(NICKNAME_KEY to value)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writeNickname[$roundKey/$uid] ok" }
                    mirror.mirrorNickname(roundKey, uid, value)
                },
                onFailure = { log.e(it) { "writeNickname[$roundKey/$uid] failed" } },
            )
        }
    }

    override suspend fun fetchLiveLeaderboard(roundKey: String): Result<FirebaseLeaderboard> = runCatching {
        val snapshot = Firebase.database.reference(playersPath(roundKey))
            .valueEvents
            .first()
        val players = snapshot.children
            .mapNotNull { it.toPlayer() }
            .sortedByDescending { it.totalCount }
            .take(MOHAMED_LOVERS_TOP_LIMIT)
        val entries = players.mapIndexed { index, player ->
            FirebaseLeaderboardEntry(
                rank = index + 1,
                uid = player.uid,
                score = player.totalCount,
                countryCode = player.countryCode,
                scoreMasked = false,
                isSupporter = false,
                nickname = player.nickname,
            )
        }
        FirebaseLeaderboard(entries = entries, isFinal = false)
    }.also { result ->
        result.fold(
            onSuccess = { log.d { "fetchLiveLeaderboard[$roundKey]: ${it.entries.size} entries" } },
            onFailure = { log.e(it) { "fetchLiveLeaderboard[$roundKey] error" } },
        )
    }

    private fun playersPath(roundKey: String) = "$ROOT_PATH/$roundKey/$PLAYERS_PATH"
    private fun leaderboardPath(roundKey: String, daily: Boolean = false): String {
        val node = if (daily) DAILY_LEADERBOARD_PATH else LEADERBOARD_PATH
        return "$ROOT_PATH/$roundKey/$node"
    }

    private fun dev.gitlive.firebase.database.DataSnapshot.toLeaderboardEntry(): FirebaseLeaderboardEntry? {
        val map = value as? Map<*, *> ?: return null
        val uid = map[UID_KEY] as? String ?: return null
        val score = (map[SCORE_KEY] as? Number)?.toInt() ?: return null
        val rank = (map[RANK_KEY] as? Number)?.toInt() ?: key?.toIntOrNull() ?: return null
        val countryCode = map[COUNTRY_CODE_KEY] as? String ?: ""
        val rankChange = map[RANK_CHANGE_KEY] as? String ?: ""
        val scoreMasked = map[SCORE_MASKED_KEY] as? Boolean ?: false
        val isSupporter = map[IS_SUPPORTER_KEY] as? Boolean ?: false
        val dailyBadge = map[DAILY_BADGE_KEY] as? String
        val nickname = map[NICKNAME_KEY] as? String ?: ""
        return FirebaseLeaderboardEntry(rank = rank, uid = uid, score = score, countryCode = countryCode, rankChange = rankChange, scoreMasked = scoreMasked, isSupporter = isSupporter, dailyBadge = dailyBadge, nickname = nickname)
    }

    private fun dev.gitlive.firebase.database.DataSnapshot.toPlayer(): MohamedLoversPlayer? {
        val map = value as? Map<*, *> ?: return null
        val uid = map[UID_KEY] as? String ?: key ?: return null
        return MohamedLoversPlayer(
            uid = uid,
            totalCount = (map[TOTAL_COUNT_KEY] as? Number)?.toInt() ?: 0,
            totalExternal = (map[TOTAL_EXTERNAL_KEY] as? Number)?.toInt() ?: 0,
            rank = (map[RANK_KEY] as? Number)?.toInt() ?: 0,
            isWinner = map[IS_WINNER_KEY] as? Boolean ?: false,
            winnerCode = map[WINNER_CODE_KEY] as? String ?: "",
            countryCode = map[COUNTRY_CODE_KEY] as? String ?: "",
            updatedAt = (map[UPDATED_AT_KEY] as? Number)?.toLong() ?: 0L,
            yesterdayTotalScore = (map[YESTERDAY_TOTAL_SCORE_KEY] as? Number)?.toInt() ?: 0,
            nickname = map[NICKNAME_KEY] as? String ?: "",
        )
    }

    private companion object {
        const val ROOT_PATH = "mohamed_lovers"
        const val PLAYERS_PATH = "players"
        const val LEADERBOARD_PATH = "leaderboard"
        const val DAILY_LEADERBOARD_PATH = "dailyLeaderboard"
        const val IS_FINAL_KEY = "isFinal"
        const val UID_KEY = "uid"
        const val SCORE_KEY = "score"
        const val RANK_KEY = "rank"
        const val TOTAL_COUNT_KEY = "totalCount"
        const val TOTAL_EXTERNAL_KEY = "totalExternal"
        const val IS_WINNER_KEY = "isWinner"
        const val WINNER_CODE_KEY = "winnerCode"
        const val COUNTRY_CODE_KEY = "countryCode"
        const val RANK_CHANGE_KEY = "rankChange"
        const val UPDATED_AT_KEY = "updatedAt"
        const val SCORE_MASKED_KEY = "scoreMasked"
        const val IS_SUPPORTER_KEY = "isSupporter"
        const val DAILY_BADGE_KEY = "dailyBadge"
        const val NICKNAME_KEY = "nickname"
        const val YESTERDAY_TOTAL_SCORE_KEY = "yesterdayTotalScore"
        const val ROUND_TOTAL_PATH = "roundTotal"
        const val ROUND_PLAYER_COUNT_PATH = "roundPlayerCount"
        const val ALL_TIME_TOTAL_PATH = "allTimeTotal"
        const val USERS_PATH = "users"
        const val REMINDER_NOTIFS_ENABLED_KEY = "reminderNotifsEnabled"
        const val LEADERBOARD_NOTIFS_ENABLED_KEY = "leaderboardNotifsEnabled"
        const val ACHIEVEMENTS_PATH = "achievements"
        const val PURCHASES_PATH = "purchases"
        const val PRODUCT_ID_KEY = "productId"
        const val PRODUCT_TYPE_KEY = "productType"
        const val PURCHASE_DATE_KEY = "purchaseDate"
    }
}
