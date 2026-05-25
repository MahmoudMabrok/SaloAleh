package tools.mo3ta.salo.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.MilestoneTracker
import tools.mo3ta.salo.data.engagement.DailyGoalStore
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseApi
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseClient
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.hadith.HadithListRepository
import tools.mo3ta.salo.data.language.LanguageStore
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.data.remote.HadithRemoteDataSource
import tools.mo3ta.salo.data.remote.createHttpClient
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.data.tendays.TenDaysStore
import tools.mo3ta.salo.data.tendays.TenDaysFirebaseClient
import tools.mo3ta.salo.audio.TakbeerSoundPlayer
import tools.mo3ta.salo.audio.createTakbeerSoundPlayer
import tools.mo3ta.salo.presentation.AchievementsViewModel
import tools.mo3ta.salo.presentation.HadithListViewModel
import tools.mo3ta.salo.presentation.MohamedLoversViewModel
import tools.mo3ta.salo.presentation.TakbeerSessionViewModel
import tools.mo3ta.salo.presentation.TenDaysViewModel

val appModule = module {
    single { MohamedLoversFirebaseClient(get()) } bind MohamedLoversFirebaseApi::class
    single { MohamedLoversSessionStore(get()) }
    single { EngagementStore(get()) }
    single { DailyGoalStore(get()) }
    single { LanguageStore(get()) }
    single { NotificationSettingsStore(get()) }
    single { PremiumStore(get()) }
    single { MilestoneTracker(get()) }
    single { DailyHadithStore(get()) }
    single { MohamedLoversRepository(get(), get(), get(), get()) }
    single { createHttpClient() }
    single { HadithRemoteDataSource(get()) }
    single { HadithListRepository(get()) }
    viewModel { MohamedLoversViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { AchievementsViewModel(get()) }
    viewModel { HadithListViewModel(get()) }
    single { TenDaysStore(get()) }
    single { TenDaysFirebaseClient(get()) }
    viewModel { TenDaysViewModel(get(), get(), get(), get()) }
    single<TakbeerSoundPlayer> { createTakbeerSoundPlayer() }
    viewModel { TakbeerSessionViewModel(get()) }
}
