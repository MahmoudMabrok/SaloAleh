package tools.mo3ta.salo.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.referral.ReferralStore
import tools.mo3ta.salo.data.MilestoneTracker
import tools.mo3ta.salo.data.baqiyat.BaqiyatFirebaseClient
import tools.mo3ta.salo.data.baqiyat.BaqiyatStore
import tools.mo3ta.salo.data.dhikr.DhikrChallengeFirebaseClient
import tools.mo3ta.salo.data.dhikr.DhikrChallengeStore
import tools.mo3ta.salo.data.istighfar.IstighfarChallengeFirebaseClient
import tools.mo3ta.salo.data.istighfar.IstighfarChallengeStore
import tools.mo3ta.salo.data.engagement.ChallengeBadgeStore
import tools.mo3ta.salo.data.engagement.DailyGoalStore
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.engagement.RoundStreakStore
import tools.mo3ta.salo.data.firebase.FirestoreMirror
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseApi
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseClient
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.hadith.HadithListRepository
import tools.mo3ta.salo.data.heart.HeartStore
import tools.mo3ta.salo.data.language.LanguageStore
import tools.mo3ta.salo.data.salawat.SalawatVariantStore
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
import tools.mo3ta.salo.presentation.BaqiyatViewModel
import tools.mo3ta.salo.presentation.DhikrChallengeViewModel
import tools.mo3ta.salo.presentation.IstighfarChallengeViewModel
import tools.mo3ta.salo.presentation.HadithListViewModel
import tools.mo3ta.salo.presentation.MohamedLoversViewModel
import tools.mo3ta.salo.presentation.TakbeerSessionViewModel
import tools.mo3ta.salo.presentation.TenDaysViewModel

val appModule = module {
    single { FirestoreMirror() }
    single { MohamedLoversFirebaseClient(get(), get(), get()) } bind MohamedLoversFirebaseApi::class
    single { MohamedLoversSessionStore(get()) }
    single { EngagementStore(get()) }
    single { DailyGoalStore(get()) }
    single { RoundStreakStore(get()) }
    single { ChallengeBadgeStore(get()) }
    single { HeartStore(get()) }
    single { DhikrChallengeStore(get()) }
    single { DhikrChallengeFirebaseClient(get(), get()) }
    single { IstighfarChallengeStore(get()) }
    single { IstighfarChallengeFirebaseClient(get(), get()) }
    single { BaqiyatStore(get()) }
    single { BaqiyatFirebaseClient(get(), get()) }
    single { LanguageStore(get()) }
    single { SalawatVariantStore(get()) }
    single { NotificationSettingsStore(get()) }
    single { PremiumStore(get()) }
    single { ReferralStore(get()) }
    single { MilestoneTracker(get()) }
    single { DailyHadithStore(get()) }
    single { MohamedLoversRepository(get(), get(), get(), get(), get()) }
    single { createHttpClient() }
    single { HadithRemoteDataSource(get()) }
    single { HadithListRepository(get()) }
    viewModel { MohamedLoversViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { DhikrChallengeViewModel(get(), get(), get(), get(), get()) }
    viewModel { IstighfarChallengeViewModel(get(), get(), get(), get(), get()) }
    viewModel { BaqiyatViewModel(get(), get(), get(), get(), get()) }
    viewModel { AchievementsViewModel(get(), get(), get()) }
    viewModel { HadithListViewModel(get()) }
    single { TenDaysStore(get()) }
    single { TenDaysFirebaseClient(get(), get(), get()) }
    viewModel { TenDaysViewModel(get(), get(), get(), get()) }
    single<TakbeerSoundPlayer> { createTakbeerSoundPlayer() }
    viewModel { TakbeerSessionViewModel(get()) }
}
