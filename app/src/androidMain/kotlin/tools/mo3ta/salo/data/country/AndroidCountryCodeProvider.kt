package tools.mo3ta.salo.data.country

import android.content.Context
import android.telephony.TelephonyManager
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE

class AndroidCountryCodeProvider(private val context: Context) : CountryCodeProvider {

    override fun get(): String {
        val telephonyIso = runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.networkCountryIso?.takeIf { it.isNotBlank() }
                ?: tm?.simCountryIso?.takeIf { it.isNotBlank() }
        }.getOrNull()

        val localeIso = context.resources.configuration.locales[0].country
            .takeIf { it.isNotBlank() }

        val resolved = (telephonyIso ?: localeIso)?.uppercase()
        return resolved?.takeIf { it.length >= 2 } ?: MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE
    }
}
