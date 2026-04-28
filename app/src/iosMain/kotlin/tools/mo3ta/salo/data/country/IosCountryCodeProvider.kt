package tools.mo3ta.salo.data.country

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE

class IosCountryCodeProvider : CountryCodeProvider {
    override fun get(): String {
        val code = NSLocale.currentLocale.countryCode?.uppercase() ?: ""
        return if (code.length >= 2) code else MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE
    }
}
