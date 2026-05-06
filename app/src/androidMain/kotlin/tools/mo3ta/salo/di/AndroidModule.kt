package tools.mo3ta.salo.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.FirebaseAnalyticsManager
import tools.mo3ta.salo.data.country.AndroidCountryCodeProvider
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.time.KronosNetworkTimeProvider
import tools.mo3ta.salo.data.time.NetworkTimeProvider

val androidModule = module {
    single<AnalyticsManager> { FirebaseAnalyticsManager(androidContext()) }
    single<NetworkTimeProvider> { KronosNetworkTimeProvider(androidContext()) }
    single<CountryCodeProvider> { AndroidCountryCodeProvider(androidContext()) }
    single<Settings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("ml_session", Context.MODE_PRIVATE),
        )
    }
}
