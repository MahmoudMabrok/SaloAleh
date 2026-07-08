package tools.mo3ta.salo.data.referral

import com.russhwolf.settings.Settings

class ReferralStore(private val settings: Settings) {

    fun getOrCreateReferralCode(uid: String): String {
        settings.getStringOrNull(KEY_REFERRAL_CODE)?.takeIf { it.isNotBlank() }?.let { return it }
        val code = uid.take(REFERRAL_CODE_LENGTH).uppercase()
        settings.putString(KEY_REFERRAL_CODE, code)
        return code
    }

    fun isReferralApplied(): Boolean = settings.getBoolean(KEY_REFERRAL_APPLIED, false)

    fun markReferralApplied() = settings.putBoolean(KEY_REFERRAL_APPLIED, true)

    fun getReferredBy(): String? = settings.getStringOrNull(KEY_REFERRED_BY)

    fun saveReferredBy(referrerUid: String) = settings.putString(KEY_REFERRED_BY, referrerUid)

    fun getPendingReferralCode(): String? = settings.getStringOrNull(KEY_PENDING_REFERRAL_CODE)

    fun savePendingReferralCode(code: String) = settings.putString(KEY_PENDING_REFERRAL_CODE, code)

    fun clearPendingReferralCode() = settings.remove(KEY_PENDING_REFERRAL_CODE)

    fun isInstallReferrerChecked(): Boolean = settings.getBoolean(KEY_INSTALL_REFERRER_CHECKED, false)

    fun markInstallReferrerChecked() = settings.putBoolean(KEY_INSTALL_REFERRER_CHECKED, true)

    fun isFirstLaunchDone(): Boolean = settings.getBoolean(KEY_FIRST_LAUNCH_DONE, false)

    fun markFirstLaunchDone() = settings.putBoolean(KEY_FIRST_LAUNCH_DONE, true)

    private companion object {
        const val KEY_REFERRAL_CODE = "referral_code"
        const val KEY_REFERRAL_APPLIED = "referral_applied"
        const val KEY_REFERRED_BY = "referred_by"
        const val KEY_PENDING_REFERRAL_CODE = "pending_referral_code"
        const val KEY_INSTALL_REFERRER_CHECKED = "install_referrer_checked"
        const val KEY_FIRST_LAUNCH_DONE = "first_launch_done"
        const val REFERRAL_CODE_LENGTH = 8
    }
}
