package tools.mo3ta.salo.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.NoOpAnalyticsManager
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseClient
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.presentation.AchievementsViewModel
import tools.mo3ta.salo.presentation.MohamedLoversViewModel

val appModule = module {
    single { MohamedLoversFirebaseClient(get()) }
    single { MohamedLoversSessionStore(get()) }
    single { EngagementStore(get()) }
    single { NotificationSettingsStore(get()) }
    single { DailyHadithStore(get()) }
    single<AnalyticsManager> { NoOpAnalyticsManager() }
    single { MohamedLoversRepository(get(), get(), get(), get()) }
    viewModel { MohamedLoversViewModel(get(), get(), get()) }
    viewModel { AchievementsViewModel(get()) }
}
