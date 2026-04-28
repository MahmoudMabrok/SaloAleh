package com.elsharif.dailyseventy.domain.mohamedlovers

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class MohamedLoversFirebaseClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var persistenceEnabled = false

    private val authMutex = Mutex()

    fun isConfigured(): Boolean = ensureFirebaseApp() != null

    suspend fun ensureSignedInAnonymously(): Result<String> {
        val firebaseApp = ensureFirebaseApp()
            ?: return Result.failure(IllegalStateException("Firebase is not configured for this build."))

        return authMutex.withLock {
            runCatching {
                val auth = FirebaseAuth.getInstance(firebaseApp)
                auth.currentUser?.uid?.also {
                    Log.d("TestTest", "MohamedLoversFirebase: anon reuse uid=$it")
                } ?: run {
                    Log.d("TestTest", "MohamedLoversFirebase: anon signInAnonymously start")
                    val uid = auth.signInAnonymously().await().user?.uid
                        ?: error("Firebase anonymous sign-in failed.")
                    Log.d("TestTest", "MohamedLoversFirebase: anon signInAnonymously done uid=$uid")
                    uid
                }
            }
        }
    }

    fun observeTopPlayers(
        roundKey: String,
        limit: Int = MOHAMED_LOVERS_TOP_LIMIT,
    ): Flow<Result<List<MohamedLoversPlayer>>> = callbackFlow {
        val ref = playersRef(roundKey)
        if (ref == null) {
            trySend(Result.failure(IllegalStateException("Firebase is not configured for this build.")))
            close()
            return@callbackFlow
        }

        val query: Query = ref.orderByChild(TOTAL_COUNT_KEY).limitToLast(limit)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val players = snapshot.children.mapNotNull { it.toMohamedLoversPlayer() }
                trySend(Result.success(players))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }

        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    fun observeSelfPlayer(
        roundKey: String,
        uid: String,
    ): Flow<Result<MohamedLoversPlayer?>> = callbackFlow {
        val playerRef = playersRef(roundKey)?.child(uid)
        if (playerRef == null) {
            trySend(Result.failure(IllegalStateException("Firebase is not configured for this build.")))
            close()
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(Result.success(snapshot.takeIf { it.exists() }?.toMohamedLoversPlayer()))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }

        playerRef.addValueEventListener(listener)
        awaitClose { playerRef.removeEventListener(listener) }
    }

    suspend fun incrementSession(
        roundKey: String,
        uid: String,
        delta: Int,
        countryCode: String,
    ): Result<Unit> {
        val ref = playersRef(roundKey)?.child(uid) ?: return Result.failure(
            IllegalStateException("Firebase is not configured for this build."),
        )

        return suspendCancellableCoroutine { continuation ->
            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): Transaction.Result {
                    val currentValue = currentData.value as? Map<*, *> ?: emptyMap<String, Any?>()
                    val existingTotal = (currentValue[TOTAL_COUNT_KEY] as? Number)?.toInt() ?: 0
                    val existingIsWinner = currentValue[IS_WINNER_KEY] as? Boolean ?: false
                    val existingWinnerCode = currentValue[WINNER_CODE_KEY] as? String ?: ""

                    val safeCountryCode = countryCode.takeIf { it.length >= 2 }
                        ?: MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE

                    currentData.child(UID_KEY).value = uid
                    currentData.child(COUNTRY_CODE_KEY).value = safeCountryCode
                    currentData.child(TOTAL_COUNT_KEY).value = existingTotal + delta
                    currentData.child(IS_WINNER_KEY).value = existingIsWinner
                    currentData.child(WINNER_CODE_KEY).value = existingWinnerCode
                    currentData.child(UPDATED_AT_KEY).value = ServerValue.TIMESTAMP

                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?,
                ) {
                    if (!continuation.isActive) {
                        return
                    }

                    when {
                        error != null -> continuation.resume(Result.failure(error.toException()))
                        !committed -> continuation.resume(
                            Result.failure(IllegalStateException("Competition update was not committed.")),
                        )
                        else -> continuation.resume(Result.success(Unit))
                    }
                }
            })
        }
    }

    private fun playersRef(roundKey: String): DatabaseReference? {
        val database = database() ?: return null
        return database.reference.child(ROOT_PATH).child(roundKey).child(PLAYERS_PATH)
    }

    private fun database(): FirebaseDatabase? {
        val firebaseApp = ensureFirebaseApp() ?: return null
        val database = FirebaseDatabase.getInstance(firebaseApp)

        if (!persistenceEnabled) {
            synchronized(this) {
                if (!persistenceEnabled) {
                    runCatching { database.setPersistenceEnabled(true) }
                    persistenceEnabled = true
                }
            }
        }

        return database
    }

    private fun ensureFirebaseApp(): FirebaseApp? {
        return FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
    }

    private fun DataSnapshot.toMohamedLoversPlayer(): MohamedLoversPlayer? {
        val uid = child(UID_KEY).getValue(String::class.java)
            ?: key
            ?: return null

        return MohamedLoversPlayer(
            uid = uid,
            totalCount = child(TOTAL_COUNT_KEY).getValue(Int::class.java)
                ?: child(TOTAL_COUNT_KEY).getValue(Long::class.java)?.toInt()
                ?: 0,
            isWinner = child(IS_WINNER_KEY).getValue(Boolean::class.java) ?: false,
            winnerCode = child(WINNER_CODE_KEY).getValue(String::class.java).orEmpty(),
            countryCode = child(COUNTRY_CODE_KEY).getValue(String::class.java).orEmpty(),
            updatedAt = child(UPDATED_AT_KEY).getValue(Long::class.java) ?: 0L,
        )
    }

    private companion object {
        const val ROOT_PATH = "mohamed_lovers"
        const val PLAYERS_PATH = "players"
        const val UID_KEY = "uid"
        const val TOTAL_COUNT_KEY = "totalCount"
        const val IS_WINNER_KEY = "isWinner"
        const val WINNER_CODE_KEY = "winnerCode"
        const val COUNTRY_CODE_KEY = "countryCode"
        const val UPDATED_AT_KEY = "updatedAt"
    }
}
