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
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.logFirebaseError
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.AppUpdateConfig
import tools.mo3ta.salo.domain.FirebaseLeaderboard
import tools.mo3ta.salo.domain.FirebaseLeaderboardEntry
import tools.mo3ta.salo.domain.HeroesBoard
import tools.mo3ta.salo.domain.parseHeroesBoard
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_TOP_LIMIT
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE
import tools.mo3ta.salo.domain.MohamedLoversMedals
import tools.mo3ta.salo.domain.MohamedLoversPlayer
import tools.mo3ta.salo.domain.UserAchievement

class MohamedLoversFirebaseClient(
    private val sessionStore: MohamedLoversSessionStore,
    private val mirror: FirestoreMirror,
    private val analyticsManager: AnalyticsManager,
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
                    onFailure = { error ->
                        log.e(error) { "observeSelfPlayer[$roundKey/$uid] error" }
                        trackReadFailure("observe_self_player", error)
                    },
                )
            }
            .catch { e ->
                log.e(e) { "observeSelfPlayer[$roundKey/$uid] flow error" }
                trackReadFailure("observe_self_player", e)
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
                onFailure = { error ->
                    log.e(error) { "fetchRoundPlayerCount[$roundKey] failed" }
                    trackReadFailure("fetch_round_player_count", error)
                },
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
                onFailure = { error ->
                    log.e(error) { "fetchRoundTotal[$roundKey] failed" }
                    trackReadFailure("fetch_round_total", error)
                },
            )
        }
    }

    override suspend fun fetchAppConfig(): Result<AppUpdateConfig?> {
        log.d { "fetchAppConfig" }
        return runCatching {
            val snapshot = Firebase.database.reference("$ROOT_PATH/$APP_CONFIG_PATH")
                .valueEvents.first()
            if (!snapshot.exists) return@runCatching null
            val map = snapshot.value as? Map<*, *> ?: return@runCatching null
            val latest = map[LATEST_VERSION_KEY] as? String
            val minSupported = map[MIN_SUPPORTED_VERSION_KEY] as? String
            if (latest == null && minSupported == null) return@runCatching null
            AppUpdateConfig(
                latestVersion = latest ?: "",
                minSupportedVersion = minSupported,
            )
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchAppConfig=$it" } },
                onFailure = { error ->
                    log.e(error) { "fetchAppConfig failed" }
                    trackReadFailure("fetch_app_config", error)
                },
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
                onFailure = { error ->
                    log.e(error) { "fetchAllTimeTotal failed" }
                    trackReadFailure("fetch_all_time_total", error)
                },
            )
        }
    }

    override suspend fun fetchHeroes(): Result<HeroesBoard?> {
        log.d { "fetchHeroes" }
        return runCatching {
            val snapshot = Firebase.database.reference("$ROOT_PATH/$HEROES_PATH")
                .valueEvents.first()
            if (!snapshot.exists) null else parseHeroesBoard(snapshot.value)
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchHeroes date=${it?.date} hasAny=${it?.hasAny}" } },
                onFailure = { error ->
                    log.e(error) { "fetchHeroes failed" }
                    trackReadFailure("fetch_heroes", error)
                },
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
                    onFailure = { error ->
                        log.e(error) { "observeLeaderboard[$roundKey] error" }
                        trackReadFailure("observe_leaderboard", error)
                    },
                )
            }
            .catch { e ->
                log.e(e) { "observeLeaderboard[$roundKey] flow error" }
                trackReadFailure("observe_leaderboard", e)
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
            SCHEMA_VERSION_KEY to CLIENT_SCHEMA_VERSION,
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
                onFailure = { error ->
                    log.e(error) { "incrementSession[$roundKey/$uid] failed" }
                    trackWriteFailure("increment_session", error)
                },
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
                playerPatch(uid, TOTAL_EXTERNAL_KEY to ServerValue.increment(count.toDouble()))
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "incrementExternalCount[$roundKey/$uid] ok" }
                    mirror.mirrorExternalCount(roundKey, uid, count)
                },
                onFailure = { error ->
                    log.e(error) { "incrementExternalCount[$roundKey/$uid] failed" }
                    trackWriteFailure("increment_external_count", error)
                },
            )
        }
    }

    override suspend fun decrementScore(roundKey: String, uid: String, amount: Int): Result<Int> {
        log.d { "decrementScore[$roundKey/$uid] amount=$amount" }
        if (amount <= 0) return Result.success(0)
        return runCatching {
            val playerRef = Firebase.database.reference(playersPath(roundKey)).child(uid)
            val current = (playerRef.child(TOTAL_COUNT_KEY).valueEvents.first().value as? Number)?.toInt() ?: 0
            // Floor at 0: a correction can never push the saved score below zero.
            val newTotal = (current - amount).coerceAtLeast(0)
            val applied = current - newTotal
            if (applied > 0) {
                playerRef.updateChildren(playerPatch(uid, TOTAL_COUNT_KEY to newTotal))
            }
            newTotal to applied
        }.also { result ->
            result.fold(
                onSuccess = { (newTotal, applied) ->
                    log.d { "decrementScore[$roundKey/$uid] ok newTotal=$newTotal applied=$applied" }
                    if (applied > 0) mirror.mirrorPlayerField(roundKey, uid, TOTAL_COUNT_KEY, newTotal)
                },
                onFailure = { error ->
                    log.e(error) { "decrementScore[$roundKey/$uid] failed" }
                    trackWriteFailure("decrement_score", error)
                },
            )
        }.map { it.second }
    }

    override suspend fun resetPlayerScore(roundKey: String, uid: String): Result<Unit> {
        log.d { "resetPlayerScore[$roundKey/$uid]" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                playerPatch(uid, TOTAL_COUNT_KEY to 0)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "resetPlayerScore[$roundKey/$uid] ok" }
                    mirror.mirrorResetPlayerScore(roundKey, uid)
                },
                onFailure = { error ->
                    log.e(error) { "resetPlayerScore[$roundKey/$uid] failed" }
                    trackWriteFailure("reset_player_score", error)
                },
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
                onFailure = { error ->
                    log.e(error) { "writeUserActivity[$uid] failed" }
                    trackWriteFailure("write_user_activity", error)
                },
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
                onFailure = { error ->
                    log.e(error) { "writeFcmToken[$uid] failed" }
                    trackWriteFailure("write_fcm_token", error)
                },
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
                onFailure = { error ->
                    log.e(error) { "writeNotificationPrefs[$uid] failed" }
                    trackWriteFailure("write_notification_prefs", error)
                },
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
                onFailure = { error ->
                    log.e(error) { "fetchUserAchievements[$uid] failed" }
                    trackReadFailure("fetch_user_achievements", error)
                },
            )
        }
    }

    override suspend fun fetchSelfMedals(uid: String): Result<MohamedLoversMedals> {
        log.d { "fetchSelfMedals[$uid]" }
        return runCatching {
            val snapshot = Firebase.database
                .reference("$ROOT_PATH/$USERS_PATH/$uid/$MEDALS_PATH")
                .valueEvents.first()
            val map = snapshot.value as? Map<*, *> ?: return@runCatching MohamedLoversMedals()
            MohamedLoversMedals(
                gold = (map[MEDAL_GOLD_KEY] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                silver = (map[MEDAL_SILVER_KEY] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                bronze = (map[MEDAL_BRONZE_KEY] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
            )
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchSelfMedals[$uid] = $it" } },
                onFailure = { error ->
                    log.e(error) { "fetchSelfMedals[$uid] failed" }
                    trackReadFailure("fetch_self_medals", error)
                },
            )
        }
    }

    override suspend fun setScoreMasked(roundKey: String, uid: String, masked: Boolean): Result<Unit> {
        log.d { "setScoreMasked[$roundKey/$uid] masked=$masked" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                playerPatch(uid, SCORE_MASKED_KEY to masked)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "setScoreMasked[$roundKey/$uid] ok" }
                    mirror.mirrorPlayerField(roundKey, uid, SCORE_MASKED_KEY, masked)
                },
                onFailure = { error ->
                    log.e(error) { "setScoreMasked[$roundKey/$uid] failed" }
                    trackWriteFailure("set_score_masked", error)
                },
            )
        }
    }

    override suspend fun setSupporter(roundKey: String, uid: String, supporter: Boolean): Result<Unit> {
        log.d { "setSupporter[$roundKey/$uid] supporter=$supporter" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                playerPatch(uid, IS_SUPPORTER_KEY to supporter)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "setSupporter[$roundKey/$uid] ok" }
                    mirror.mirrorPlayerField(roundKey, uid, IS_SUPPORTER_KEY, supporter)
                },
                onFailure = { error ->
                    log.e(error) { "setSupporter[$roundKey/$uid] failed" }
                    trackWriteFailure("set_supporter", error)
                },
            )
        }
    }

    override suspend fun writeDailyBadge(roundKey: String, uid: String, badgeKey: String?): Result<Unit> {
        log.d { "writeDailyBadge[$roundKey/$uid] badge=$badgeKey" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                playerPatch(uid, DAILY_BADGE_KEY to badgeKey)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writeDailyBadge[$roundKey/$uid] ok" }
                    mirror.mirrorPlayerField(roundKey, uid, DAILY_BADGE_KEY, badgeKey)
                },
                onFailure = { error ->
                    log.e(error) { "writeDailyBadge[$roundKey/$uid] failed" }
                    trackWriteFailure("write_daily_badge", error)
                },
            )
        }
    }

    override suspend fun writeRoundStreak(roundKey: String, uid: String, streak: Int): Result<Unit> {
        // Publish positive streaks; clear the field (null) when the streak is not active
        // so the leaderboard badge disappears for this user.
        val value: Int? = streak.takeIf { it > 0 }
        log.d { "writeRoundStreak[$roundKey/$uid] streak=$value" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                playerPatch(uid, ROUND_STREAK_KEY to value)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writeRoundStreak[$roundKey/$uid] ok" }
                    mirror.mirrorPlayerField(roundKey, uid, ROUND_STREAK_KEY, value)
                },
                onFailure = { error ->
                    log.e(error) { "writeRoundStreak[$roundKey/$uid] failed" }
                    trackWriteFailure("write_round_streak", error)
                },
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
                onFailure = { error ->
                    log.e(error) { "writeSupporterStatus[$uid] failed" }
                    trackWriteFailure("write_supporter_status", error)
                },
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
                onFailure = { error ->
                    log.e(error) { "writePurchaseMetadata[$uid/$productId] failed" }
                    trackWriteFailure("write_purchase_metadata", error)
                },
            )
        }
    }

    override suspend fun writeNickname(roundKey: String, uid: String, nickname: String): Result<Unit> {
        log.d { "writeNickname[$roundKey/$uid] nickname=$nickname" }
        val value = nickname.ifBlank { "" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                playerPatch(uid, NICKNAME_KEY to value)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writeNickname[$roundKey/$uid] ok" }
                    mirror.mirrorNickname(roundKey, uid, value)
                },
                onFailure = { error ->
                    log.e(error) { "writeNickname[$roundKey/$uid] failed" }
                    trackWriteFailure("write_nickname", error)
                },
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
            onFailure = { error ->
                log.e(error) { "fetchLiveLeaderboard[$roundKey] error" }
                trackReadFailure("fetch_live_leaderboard", error)
            },
        )
    }

    private fun trackReadFailure(operation: String, error: Throwable) {
        analyticsManager.logFirebaseError(
            surface = "mohamed_lovers",
            operation = operation,
            access = "read",
            error = error,
        )
    }

    private fun trackWriteFailure(operation: String, error: Throwable) {
        analyticsManager.logFirebaseError(
            surface = "mohamed_lovers",
            operation = operation,
            access = "write",
            error = error,
        )
    }

    override suspend fun writeReferralCode(uid: String, code: String): Result<Unit> {
        log.d { "writeReferralCode[$uid] code=$code" }
        return runCatching {
            Firebase.database.reference("$ROOT_PATH/$REFERRAL_CODES_PATH/$code").setValue(uid)
            Firebase.database.reference("$ROOT_PATH/$USERS_PATH/$uid").updateChildren(
                mapOf(REFERRAL_CODE_KEY to code)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writeReferralCode[$uid] ok" }
                    mirror.mirrorReferralCode(uid, code)
                },
                onFailure = { error ->
                    log.e(error) { "writeReferralCode[$uid] failed" }
                    trackWriteFailure("write_referral_code", error)
                },
            )
        }
    }

    override suspend fun lookupReferralCode(code: String): Result<String?> {
        log.d { "lookupReferralCode[$code]" }
        return runCatching {
            val snapshot = Firebase.database.reference("$ROOT_PATH/$REFERRAL_CODES_PATH/$code")
                .valueEvents.first()
            snapshot.value as? String
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "lookupReferralCode[$code] = $it" } },
                onFailure = { error ->
                    log.e(error) { "lookupReferralCode[$code] failed" }
                    trackReadFailure("lookup_referral_code", error)
                },
            )
        }
    }

    override suspend fun applyReferral(referrerUid: String, referredUid: String): Result<Unit> {
        log.d { "applyReferral referrer=$referrerUid referred=$referredUid" }
        return runCatching {
            Firebase.database.reference("$ROOT_PATH/$USERS_PATH/$referrerUid/$REFERRALS_PATH/$referredUid")
                .setValue(true)
            Firebase.database.reference("$ROOT_PATH/$USERS_PATH/$referredUid").updateChildren(
                mapOf(REFERRED_BY_KEY to referrerUid)
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "applyReferral ok" }
                    mirror.mirrorReferral(referrerUid, referredUid)
                },
                onFailure = { error ->
                    log.e(error) { "applyReferral failed" }
                    trackWriteFailure("apply_referral", error)
                },
            )
        }
    }

    override suspend fun fetchReferralStats(code: String): Result<ReferralStats> {
        log.d { "fetchReferralStats[$code]" }
        return runCatching {
            val snap = Firebase.database.reference("$ROOT_PATH/$REFERRAL_STATS_PATH/$code")
                .valueEvents.first()
            if (!snap.exists) {
                ReferralStats(referralCount = 0, salawatTotal = 0L)
            } else {
                val count = (snap.child("referralCount").value as? Number)?.toInt() ?: 0
                val total = (snap.child("salawatTotal").value as? Number)?.toLong() ?: 0L
                ReferralStats(referralCount = count, salawatTotal = total)
            }
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchReferralStats[$code] = $it" } },
                onFailure = { error ->
                    log.e(error) { "fetchReferralStats[$code] failed" }
                    trackReadFailure("fetch_referral_stats", error)
                },
            )
        }
    }

    /**
     * Every partial write to a player node must carry [UID_KEY] and [SCHEMA_VERSION_KEY].
     * RTDB evaluates the node's `.write` rule (`newData.child('uid').val() === $uid`) and its
     * `.validate` (`newData.hasChildren(['uid', 'schemaVersion'])`) against the merged
     * post-write state, so a patch that omits either field is denied whenever the node does
     * not exist yet — i.e. any user who has not tapped in the current round. `schemaVersion`
     * is the required-field gate: builds that predate it never send it, so their writes are
     * rejected server-side and the user is pushed to update. Patches that omit `totalCount`
     * stay invisible to the server aggregates, which all require it.
     */
    private fun playerPatch(uid: String, vararg fields: Pair<String, Any?>): Map<String, Any?> =
        mapOf(UID_KEY to uid, SCHEMA_VERSION_KEY to CLIENT_SCHEMA_VERSION, *fields)

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
        val roundStreak = (map[ROUND_STREAK_KEY] as? Number)?.toInt()?.takeIf { it > 0 }
        val goldMedals = (map[GOLD_MEDALS_KEY] as? Number)?.toInt()?.takeIf { it > 0 }
        val silverMedals = (map[SILVER_MEDALS_KEY] as? Number)?.toInt()?.takeIf { it > 0 }
        val bronzeMedals = (map[BRONZE_MEDALS_KEY] as? Number)?.toInt()?.takeIf { it > 0 }
        val nickname = map[NICKNAME_KEY] as? String ?: ""
        return FirebaseLeaderboardEntry(rank = rank, uid = uid, score = score, countryCode = countryCode, rankChange = rankChange, scoreMasked = scoreMasked, isSupporter = isSupporter, dailyBadge = dailyBadge, roundStreak = roundStreak, goldMedals = goldMedals, silverMedals = silverMedals, bronzeMedals = bronzeMedals, nickname = nickname)
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
        const val SCHEMA_VERSION_KEY = "schemaVersion"
        // Schema version stamped on every player write. The RTDB rule requires this field
        // (`hasChildren(['uid','schemaVersion'])`), so builds that predate it are denied and
        // forced to update. Bump only when the player-write contract changes.
        const val CLIENT_SCHEMA_VERSION = 1
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
        const val ROUND_STREAK_KEY = "roundStreak"
        const val GOLD_MEDALS_KEY = "goldMedals"
        const val SILVER_MEDALS_KEY = "silverMedals"
        const val BRONZE_MEDALS_KEY = "bronzeMedals"
        const val NICKNAME_KEY = "nickname"
        const val YESTERDAY_TOTAL_SCORE_KEY = "yesterdayTotalScore"
        const val ROUND_TOTAL_PATH = "roundTotal"
        const val ROUND_PLAYER_COUNT_PATH = "roundPlayerCount"
        const val ALL_TIME_TOTAL_PATH = "allTimeTotal"
        const val APP_CONFIG_PATH = "app_config"
        const val LATEST_VERSION_KEY = "latestVersion"
        const val MIN_SUPPORTED_VERSION_KEY = "minSupportedVersion"
        const val HEROES_PATH = "heroes"
        const val USERS_PATH = "users"
        const val REMINDER_NOTIFS_ENABLED_KEY = "reminderNotifsEnabled"
        const val LEADERBOARD_NOTIFS_ENABLED_KEY = "leaderboardNotifsEnabled"
        const val ACHIEVEMENTS_PATH = "achievements"
        const val MEDALS_PATH = "medals"
        const val MEDAL_GOLD_KEY = "gold"
        const val MEDAL_SILVER_KEY = "silver"
        const val MEDAL_BRONZE_KEY = "bronze"
        const val REFERRAL_CODES_PATH = "referral_codes"
        const val REFERRAL_STATS_PATH = "referral_stats"
        const val REFERRAL_CODE_KEY = "referralCode"
        const val REFERRALS_PATH = "referrals"
        const val REFERRED_BY_KEY = "referredBy"
        const val PURCHASES_PATH = "purchases"
        const val PRODUCT_ID_KEY = "productId"
        const val PRODUCT_TYPE_KEY = "productType"
        const val PURCHASE_DATE_KEY = "purchaseDate"
    }
}
