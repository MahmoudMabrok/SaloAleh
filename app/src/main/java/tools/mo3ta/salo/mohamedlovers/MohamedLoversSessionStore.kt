package com.elsharif.dailyseventy.domain.mohamedlovers

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MohamedLoversSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val contentResolver = context.contentResolver

    fun getOrCreateAlias(): String {
        prefs.getString(KEY_ALIAS, null)?.takeIf { it.isNotBlank() }?.let { return it }

        val rawId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().replace("-", "")

        val alias = "محب محمد ${rawId.takeLast(4).uppercase(Locale.ROOT)}"
        prefs.edit { putString(KEY_ALIAS, alias) }
        return alias
    }

    fun getPendingSession(): MohamedLoversPendingSession {
        return MohamedLoversPendingSession(
            roundKey = prefs.getString(KEY_PENDING_ROUND, null),
            clickCount = prefs.getInt(KEY_PENDING_COUNT, 0),
        )
    }

    fun incrementPendingClick(roundKey: String, delta: Int = 1): MohamedLoversPendingSession {
        val currentRoundKey = prefs.getString(KEY_PENDING_ROUND, null)
        val currentCount = if (currentRoundKey == roundKey) {
            prefs.getInt(KEY_PENDING_COUNT, 0)
        } else {
            0
        }

        val updated = MohamedLoversPendingSession(
            roundKey = roundKey,
            clickCount = currentCount + delta.coerceAtLeast(1),
        )

        prefs.edit {
            putString(KEY_PENDING_ROUND, roundKey)
            putInt(KEY_PENDING_COUNT, updated.clickCount)
        }

        return updated
    }

    fun clearPendingSession() {
        prefs.edit {
            remove(KEY_PENDING_ROUND)
            remove(KEY_PENDING_COUNT)
        }
    }

    private companion object {
        const val PREFS_NAME = "mohamed_lovers_session"
        const val KEY_ALIAS = "alias"
        const val KEY_PENDING_ROUND = "pending_round_key"
        const val KEY_PENDING_COUNT = "pending_click_count"
    }
}
