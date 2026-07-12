package tools.mo3ta.salo.data.firebase

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FirestoreMirror {

    private val log = Logger.withTag("FirestoreMirror")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fs by lazy { Firebase.firestore }

    fun mirrorPlayerIncrement(
        roundKey: String,
        uid: String,
        delta: Int,
        countryCode: String,
        nickname: String,
    ) = mirror("player-increment[$roundKey/$uid]") {
        fs.collection(ROUNDS_COLLECTION).document(roundKey)
            .collection(PLAYERS_SUBCOLLECTION).document(uid)
            .set(
                mapOf(
                    "uid" to uid,
                    "countryCode" to countryCode,
                    "totalCount" to FieldValue.increment(delta),
                    "updatedAt" to FieldValue.serverTimestamp,
                    "nickname" to nickname,
                ),
                merge = true,
            )
    }

    fun mirrorExternalCount(roundKey: String, uid: String, count: Int) =
        mirror("external-count[$roundKey/$uid]") {
            fs.collection(ROUNDS_COLLECTION).document(roundKey)
                .collection(PLAYERS_SUBCOLLECTION).document(uid)
                .set(
                    mapOf("totalExternal" to FieldValue.increment(count)),
                    merge = true,
                )
        }

    fun mirrorResetPlayerScore(roundKey: String, uid: String) =
        mirror("reset-score[$roundKey/$uid]") {
            fs.collection(ROUNDS_COLLECTION).document(roundKey)
                .collection(PLAYERS_SUBCOLLECTION).document(uid)
                .set(mapOf("totalCount" to 0), merge = true)
        }

    fun mirrorPlayerField(roundKey: String, uid: String, field: String, value: Any?) =
        mirror("player-field[$roundKey/$uid/$field]") {
            fs.collection(ROUNDS_COLLECTION).document(roundKey)
                .collection(PLAYERS_SUBCOLLECTION).document(uid)
                .set(mapOf(field to value), merge = true)
        }

    fun mirrorUserActivity(uid: String, installDate: String, lastOpenDate: String) =
        mirror("user-activity[$uid]") {
            fs.collection(USERS_COLLECTION).document(uid)
                .set(
                    mapOf("installDate" to installDate, "lastOpenDate" to lastOpenDate),
                    merge = true,
                )
        }

    fun mirrorFcmToken(uid: String, token: String) =
        mirror("fcm-token[$uid]") {
            fs.collection(USERS_COLLECTION).document(uid)
                .set(mapOf("fcmToken" to token), merge = true)
        }

    fun mirrorNotificationPrefs(uid: String, remindersEnabled: Boolean, leaderboardEnabled: Boolean) =
        mirror("notif-prefs[$uid]") {
            fs.collection(USERS_COLLECTION).document(uid)
                .set(
                    mapOf(
                        "reminderNotifsEnabled" to remindersEnabled,
                        "leaderboardNotifsEnabled" to leaderboardEnabled,
                    ),
                    merge = true,
                )
        }

    fun mirrorSupporterStatus(uid: String, supporter: Boolean) =
        mirror("supporter-status[$uid]") {
            fs.collection(USERS_COLLECTION).document(uid)
                .set(mapOf("isSupporter" to supporter), merge = true)
        }

    fun mirrorPurchaseMetadata(uid: String, productId: String, productType: String, purchaseDate: String) =
        mirror("purchase[$uid/$productId]") {
            fs.collection(USERS_COLLECTION).document(uid)
                .collection(PURCHASES_SUBCOLLECTION).document(productId)
                .set(
                    mapOf(
                        "productId" to productId,
                        "productType" to productType,
                        "purchaseDate" to purchaseDate,
                    ),
                )
        }

    fun mirrorNickname(roundKey: String, uid: String, nickname: String) =
        mirror("nickname[$roundKey/$uid]") {
            fs.collection(ROUNDS_COLLECTION).document(roundKey)
                .collection(PLAYERS_SUBCOLLECTION).document(uid)
                .set(mapOf("nickname" to nickname), merge = true)
        }

    fun mirrorDhikrUserDay(
        dateKey: String,
        uid: String,
        count: Int,
        countryCode: String,
        nickname: String,
        goal: Int,
        completed: Boolean,
    ) = mirror("dhikr-user[$dateKey/$uid]") {
        fs.collection(DHIKR_COLLECTION).document(dateKey)
            .collection(USERS_SUBCOLLECTION).document(uid)
            .set(
                mapOf(
                    "count" to count.coerceAtLeast(0),
                    "data" to mapOf(
                        "uid" to uid,
                        "date" to dateKey,
                        "countryCode" to countryCode,
                        "nickname" to nickname,
                        "goal" to goal,
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp,
                    ),
                ),
                merge = true,
            )
    }

    fun mirrorIstighfarUserDay(
        dateKey: String,
        uid: String,
        count: Int,
        countryCode: String,
        nickname: String,
        goal: Int,
        completed: Boolean,
    ) = mirror("istighfar-user[$dateKey/$uid]") {
        fs.collection(ISTIGHFAR_COLLECTION).document(dateKey)
            .collection(USERS_SUBCOLLECTION).document(uid)
            .set(
                mapOf(
                    "count" to count.coerceAtLeast(0),
                    "data" to mapOf(
                        "uid" to uid,
                        "date" to dateKey,
                        "countryCode" to countryCode,
                        "nickname" to nickname,
                        "goal" to goal,
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp,
                    ),
                ),
                merge = true,
            )
    }

    fun mirrorZabadUserDay(
        dateKey: String,
        uid: String,
        count: Int,
        countryCode: String,
        nickname: String,
        goal: Int,
        completed: Boolean,
    ) = mirror("zabad-user[$dateKey/$uid]") {
        fs.collection(ZABAD_COLLECTION).document(dateKey)
            .collection(USERS_SUBCOLLECTION).document(uid)
            .set(
                mapOf(
                    "count" to count.coerceAtLeast(0),
                    "data" to mapOf(
                        "uid" to uid, "date" to dateKey, "countryCode" to countryCode,
                        "nickname" to nickname, "goal" to goal, "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp,
                    ),
                ),
                merge = true,
            )
    }

    fun mirrorQuranUserDay(
        dateKey: String,
        uid: String,
        count: Int,
        countryCode: String,
        nickname: String,
        goal: Int,
        completed: Boolean,
    ) = mirror("quran-user[$dateKey/$uid]") {
        fs.collection(QURAN_COLLECTION).document(dateKey)
            .collection(USERS_SUBCOLLECTION).document(uid)
            .set(
                mapOf(
                    "count" to count.coerceAtLeast(0),
                    "data" to mapOf(
                        "uid" to uid,
                        "date" to dateKey,
                        "countryCode" to countryCode,
                        "nickname" to nickname,
                        "goal" to goal,
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp,
                    ),
                ),
                merge = true,
            )
    }

    fun mirrorBaqiyatUserDay(
        dateKey: String,
        uid: String,
        count: Int,
        countryCode: String,
        nickname: String,
    ) = mirror("baqiyat-player[$dateKey/$uid]") {
        fs.collection(BAQIYAT_COLLECTION).document(dateKey)
            .collection(PLAYERS_SUBCOLLECTION).document(uid)
            .set(
                mapOf(
                    "uid" to uid,
                    "count" to count.coerceAtLeast(0),
                    "countryCode" to countryCode,
                    "nickname" to nickname,
                    "updatedAt" to FieldValue.serverTimestamp,
                ),
                merge = true,
            )
    }

    fun mirrorTenDaysScore(
        periodKey: String,
        uid: String,
        totalScore: Int,
        countryCode: String,
    ) = mirror("tendays-score[$periodKey/$uid]") {
        fs.collection(TEN_DAYS_COLLECTION).document(periodKey)
            .collection(PLAYERS_SUBCOLLECTION).document(uid)
            .set(
                mapOf(
                    "uid" to uid,
                    "totalScore" to totalScore,
                    "updatedAt" to FieldValue.serverTimestamp,
                    "countryCode" to countryCode,
                ),
            )
    }

    fun mirrorReferralCode(uid: String, code: String) =
        mirror("referral-code[$uid]") {
            fs.collection(USERS_COLLECTION).document(uid)
                .set(mapOf("referralCode" to code), merge = true)
        }

    fun mirrorReferral(referrerUid: String, referredUid: String) =
        mirror("referral[$referrerUid->$referredUid]") {
            fs.collection(USERS_COLLECTION).document(referrerUid)
                .collection(REFERRALS_SUBCOLLECTION).document(referredUid)
                .set(mapOf("referredUid" to referredUid, "timestamp" to FieldValue.serverTimestamp))
            fs.collection(USERS_COLLECTION).document(referredUid)
                .set(mapOf("referredBy" to referrerUid), merge = true)
        }

    private fun mirror(tag: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                log.d { "mirror $tag ok" }
            } catch (e: Exception) {
                log.e(e) { "mirror $tag failed (non-fatal)" }
            }
        }
    }

    companion object {
        const val ROUNDS_COLLECTION = "mohamed_lovers_rounds"
        const val USERS_COLLECTION = "mohamed_lovers_users"
        const val PLAYERS_SUBCOLLECTION = "players"
        const val USERS_SUBCOLLECTION = "users"
        const val LEADERBOARD_SUBCOLLECTION = "leaderboard"
        const val DAILY_LEADERBOARD_SUBCOLLECTION = "dailyLeaderboard"
        const val PURCHASES_SUBCOLLECTION = "purchases"
        const val ACHIEVEMENTS_SUBCOLLECTION = "achievements"
        const val META_COLLECTION = "mohamed_lovers_meta"
        const val DHIKR_COLLECTION = "dhikr_challenge"
        const val BAQIYAT_COLLECTION = "baqiyat_challenge"
        const val ISTIGHFAR_COLLECTION = "istighfar_challenge"
        const val ZABAD_COLLECTION = "zabad_challenge"
        const val QURAN_COLLECTION = "quran_challenge"
        const val TEN_DAYS_COLLECTION = "ten_days"
        const val REFERRALS_SUBCOLLECTION = "referrals"
    }
}
