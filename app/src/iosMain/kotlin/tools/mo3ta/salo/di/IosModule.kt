package tools.mo3ta.salo.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.NoOpAnalyticsManager
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.country.IosCountryCodeProvider
import tools.mo3ta.salo.data.time.IosNetworkTimeProvider
import tools.mo3ta.salo.data.time.NetworkTimeProvider

val iosModule = module {
    single<AnalyticsManager> { NoOpAnalyticsManager() }
    single<NetworkTimeProvider> { IosNetworkTimeProvider() }
    single<CountryCodeProvider> { IosCountryCodeProvider() }
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
}
