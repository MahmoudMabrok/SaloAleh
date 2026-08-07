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
import tools.mo3ta.salo.data.albaqara.AlBaqaraChallengeFirebaseClient
import tools.mo3ta.salo.data.albaqara.AlBaqaraChallengeStore
import tools.mo3ta.salo.data.zabad.ZabadChallengeFirebaseClient
import tools.mo3ta.salo.data.zabad.ZabadChallengeStore
import tools.mo3ta.salo.data.ghars.GharsChallengeFirebaseClient
import tools.mo3ta.salo.data.ghars.GharsChallengeStore
import tools.mo3ta.salo.data.quran.QuranChallengeFirebaseClient
import tools.mo3ta.salo.data.quran.QuranChallengeStore
import tools.mo3ta.salo.data.alfhasana.AlfHasanaChallengeFirebaseClient
import tools.mo3ta.salo.data.alfhasana.AlfHasanaChallengeStore
import tools.mo3ta.salo.data.kalimat.KalimatChallengeFirebaseClient
import tools.mo3ta.salo.data.kalimat.KalimatChallengeStore
import tools.mo3ta.salo.data.hawqala.HawqalaChallengeFirebaseClient
import tools.mo3ta.salo.data.hawqala.HawqalaChallengeStore
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
import tools.mo3ta.salo.data.reminder.AlKahfReminderStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.data.update.UpdateChecker
import tools.mo3ta.salo.data.security.AutoClickGuardStore
import tools.mo3ta.salo.data.update.UpdatePromptStore
import tools.mo3ta.salo.domain.AccountRestoreManager
import tools.mo3ta.salo.domain.ChallengeLifetimeLink
import tools.mo3ta.salo.data.session.LocalAccountReset
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.data.tendays.TenDaysStore
import tools.mo3ta.salo.data.tendays.TenDaysFirebaseClient
import tools.mo3ta.salo.audio.TakbeerSoundPlayer
import tools.mo3ta.salo.audio.createTakbeerSoundPlayer
import tools.mo3ta.salo.presentation.AccountBackupViewModel
import tools.mo3ta.salo.presentation.AchievementsViewModel
import tools.mo3ta.salo.presentation.BaqiyatViewModel
import tools.mo3ta.salo.presentation.ChallengesViewModel
import tools.mo3ta.salo.presentation.DhikrChallengeViewModel
import tools.mo3ta.salo.presentation.IstighfarChallengeViewModel
import tools.mo3ta.salo.presentation.AlBaqaraChallengeViewModel
import tools.mo3ta.salo.presentation.ZabadChallengeViewModel
import tools.mo3ta.salo.presentation.GharsChallengeViewModel
import tools.mo3ta.salo.presentation.QuranChallengeViewModel
import tools.mo3ta.salo.presentation.AlfHasanaChallengeViewModel
import tools.mo3ta.salo.presentation.KalimatChallengeViewModel
import tools.mo3ta.salo.presentation.HawqalaChallengeViewModel
import tools.mo3ta.salo.presentation.HadithListViewModel
import tools.mo3ta.salo.presentation.MohamedLoversViewModel
import tools.mo3ta.salo.presentation.TakbeerSessionViewModel
import tools.mo3ta.salo.presentation.TenDaysViewModel

val appModule = module {
    single { FirestoreMirror() }
    single { MohamedLoversFirebaseClient(get(), get(), get()) } bind MohamedLoversFirebaseApi::class
    single { MohamedLoversSessionStore(get()) }
    single { UpdatePromptStore(get()) }
    single { AutoClickGuardStore(get()) }
    single { AlKahfReminderStore(get()) }
    single { UpdateChecker(get(), get()) }
    single { EngagementStore(get()) }
    single { DailyGoalStore(get()) }
    single { RoundStreakStore(get()) }
    single { ChallengeBadgeStore(get()) }
    single { HeartStore(get()) }
    single { DhikrChallengeStore(get()) }
    single { DhikrChallengeFirebaseClient(get(), get()) }
    single { IstighfarChallengeStore(get()) }
    single { IstighfarChallengeFirebaseClient(get(), get()) }
    single { AlBaqaraChallengeStore(get()) }
    single { AlBaqaraChallengeFirebaseClient(get(), get()) }
    single { ZabadChallengeStore(get()) }
    single { ZabadChallengeFirebaseClient(get(), get()) }
    single { GharsChallengeStore(get()) }
    single { GharsChallengeFirebaseClient(get(), get()) }
    single { QuranChallengeStore(get()) }
    single { QuranChallengeFirebaseClient(get(), get()) }
    single { AlfHasanaChallengeStore(get()) }
    single { AlfHasanaChallengeFirebaseClient(get()) }
    single { KalimatChallengeStore(get()) }
    single { KalimatChallengeFirebaseClient(get()) }
    single { HawqalaChallengeStore(get()) }
    single { HawqalaChallengeFirebaseClient(get()) }
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
    single { LocalAccountReset(get()) }
    // One link per challenge that keeps a lifetime "total over time" counter, so restoring an
    // account adopts the server's totals instead of republishing this device's zeroes over them.
    single {
        listOf(
            ChallengeLifetimeLink("dhikr", get<DhikrChallengeFirebaseClient>()::fetchUserTotal, get<DhikrChallengeStore>()::restoreLifetime),
            ChallengeLifetimeLink("baqiyat", get<BaqiyatFirebaseClient>()::fetchUserTotal, get<BaqiyatStore>()::restoreLifetime),
            ChallengeLifetimeLink("istighfar", get<IstighfarChallengeFirebaseClient>()::fetchUserTotal, get<IstighfarChallengeStore>()::restoreLifetime),
            ChallengeLifetimeLink("zabad", get<ZabadChallengeFirebaseClient>()::fetchUserTotal, get<ZabadChallengeStore>()::restoreLifetime),
            ChallengeLifetimeLink("quran", get<QuranChallengeFirebaseClient>()::fetchUserTotal, get<QuranChallengeStore>()::restoreLifetime),
            ChallengeLifetimeLink("albaqara", get<AlBaqaraChallengeFirebaseClient>()::fetchUserTotal, get<AlBaqaraChallengeStore>()::restoreLifetime),
            ChallengeLifetimeLink("alfhasana", get<AlfHasanaChallengeFirebaseClient>()::fetchUserTotal, get<AlfHasanaChallengeStore>()::restoreLifetime),
            ChallengeLifetimeLink("ghars", get<GharsChallengeFirebaseClient>()::fetchUserTotal, get<GharsChallengeStore>()::restoreLifetime),
            ChallengeLifetimeLink("hawqala", get<HawqalaChallengeFirebaseClient>()::fetchUserTotal, get<HawqalaChallengeStore>()::restoreLifetime),
        )
    }
    single { AccountRestoreManager(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { createHttpClient() }
    single { HadithRemoteDataSource(get()) }
    single { HadithListRepository(get()) }
    viewModel { MohamedLoversViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { DhikrChallengeViewModel(get(), get(), get(), get(), get()) }
    viewModel { IstighfarChallengeViewModel(get(), get(), get(), get(), get()) }
    viewModel { AlBaqaraChallengeViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ZabadChallengeViewModel(get(), get(), get(), get(), get()) }
    viewModel { GharsChallengeViewModel(get(), get(), get(), get(), get()) }
    viewModel { QuranChallengeViewModel(get(), get(), get(), get(), get()) }
    viewModel { AlfHasanaChallengeViewModel(get(), get(), get(), get(), get()) }
    viewModel { KalimatChallengeViewModel(get(), get(), get(), get(), get()) }
    viewModel { HawqalaChallengeViewModel(get(), get(), get(), get(), get()) }
    viewModel { BaqiyatViewModel(get(), get(), get(), get(), get()) }
    viewModel { AchievementsViewModel(get(), get(), get()) }
    viewModel { AccountBackupViewModel(get()) }
    viewModel { ChallengesViewModel(get()) }
    viewModel { HadithListViewModel(get()) }
    single { TenDaysStore(get()) }
    single { TenDaysFirebaseClient(get(), get(), get()) }
    viewModel { TenDaysViewModel(get(), get(), get(), get()) }
    single<TakbeerSoundPlayer> { createTakbeerSoundPlayer() }
    viewModel { TakbeerSessionViewModel(get()) }
}
