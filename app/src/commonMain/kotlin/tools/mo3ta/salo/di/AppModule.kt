package tools.mo3ta.salo.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import tools.mo3ta.salo.data.engagement.DailyGoalStore
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseApi
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseClient
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.hadith.HadithListRepository
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.data.remote.HadithRemoteDataSource
import tools.mo3ta.salo.data.remote.createHttpClient
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.presentation.AchievementsViewModel
import tools.mo3ta.salo.presentation.HadithListViewModel
import tools.mo3ta.salo.presentation.MohamedLoversViewModel

val appModule = module {
    single { MohamedLoversFirebaseClient(get()) } bind MohamedLoversFirebaseApi::class
    single { MohamedLoversSessionStore(get()) }
    single { EngagementStore(get()) }
    single { DailyGoalStore(get()) }
    single { NotificationSettingsStore(get()) }
    single { DailyHadithStore(get()) }
    single { MohamedLoversRepository(get(), get(), get(), get()) }
    single { createHttpClient() }
    single { HadithRemoteDataSource(get()) }
    single { HadithListRepository(get()) }
    viewModel { MohamedLoversViewModel(get(), get(), get(), get()) }
    viewModel { AchievementsViewModel(get()) }
    viewModel { HadithListViewModel(get()) }
}
