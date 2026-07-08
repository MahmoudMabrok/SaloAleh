package tools.mo3ta.salo.data.referral

import android.content.Context
import android.net.Uri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener

object InstallReferrerHelper {

    fun checkInstallReferrer(context: Context, referralStore: ReferralStore) {
        if (referralStore.isReferralApplied()) return
        if (referralStore.isInstallReferrerChecked()) return

        val client = InstallReferrerClient.newBuilder(context).build()
        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                if (responseCode != InstallReferrerClient.InstallReferrerResponse.OK) {
                    referralStore.markInstallReferrerChecked()
                    client.endConnection()
                    return
                }
                try {
                    val referrer = client.installReferrer.installReferrer ?: ""
                    val code = extractReferralCode(referrer)
                    if (code != null && referralStore.getPendingReferralCode() == null) {
                        referralStore.savePendingReferralCode(code)
                    }
                } catch (_: Exception) {
                    // Ignore failures — best effort
                } finally {
                    referralStore.markInstallReferrerChecked()
                    client.endConnection()
                }
            }

            override fun onInstallReferrerServiceDisconnected() {}
        })
    }

    private fun extractReferralCode(referrer: String): String? {
        val decoded = Uri.decode(referrer)
        val params = decoded.split("&")
        for (param in params) {
            val parts = param.split("=", limit = 2)
            if (parts.size == 2 && parts[0] == "referral_code") {
                return parts[1].takeIf { it.isNotBlank() }
            }
        }
        return null
    }
}
