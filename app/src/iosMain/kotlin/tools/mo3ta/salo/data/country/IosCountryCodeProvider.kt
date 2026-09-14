package tools.mo3ta.salo.data.country

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE

class IosCountryCodeProvider : CountryCodeProvider {
    override fun get(): String {
        val code = NSLocale.currentLocale.countryCode?.uppercase() ?: ""
        val normalized = code.takeUnless { it == "IL"} ?: "PS"
        return if (normalized.length >= 2) normalized else MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE
    }
}
