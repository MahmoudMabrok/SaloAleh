# Mohamed Lovers — CMP Migration Design

**Date:** 2026-04-28  
**Targets:** Android + iOS  
**Package:** `tools.mo3ta.salo`

---

## Overview

Migrate the existing Android-only Mohamed Lovers feature into a Compose Multiplatform `composeApp` module targeting Android and iOS. Restructure using clean architecture. No UseCase layer — ViewModel calls Repository directly. Repository is a legitimate orchestrator, not a sinkhole.

---

## Module Structure

```
composeApp/src/
  commonMain/kotlin/tools/mo3ta/salo/
    data/
      firebase/      MohamedLoversFirebaseClient.kt
      session/       MohamedLoversSessionStore.kt
      time/          NetworkTimeProvider.kt (interface)
      country/       CountryCodeProvider.kt (interface)
    domain/
      MohamedLoversModels.kt
      MohamedLoversRepository.kt
    presentation/
      MohamedLoversViewModel.kt
      MohamedLoversUiState.kt
    ui/
      MohamedLoversScreen.kt
      components/
        MohamedLoversArchShrine.kt
        MohamedLoversCounter.kt
        MohamedLoversFonts.kt
        MohamedLoversHadithBanner.kt
        MohamedLoversInfoSheet.kt
        MohamedLoversPalette.kt
        MohamedLoversPrayerOverlay.kt
        MohamedLoversSkyBackground.kt
    analytics/
      AnalyticsManager.kt
      NoOpAnalyticsManager.kt
    di/
      AppModule.kt
    App.kt

  androidMain/kotlin/tools/mo3ta/salo/
    data/time/    KronosNetworkTimeProvider.kt
    data/country/ AndroidCountryCodeProvider.kt
    di/           AndroidModule.kt
    MainActivity.kt

  iosMain/kotlin/tools/mo3ta/salo/
    data/time/    IosNetworkTimeProvider.kt
    data/country/ IosCountryCodeProvider.kt
    di/           IosModule.kt
    MainViewController.kt
```

---

## Layers

`ui → presentation → domain → data` — one direction only.

| Layer | Responsibility |
|---|---|
| `data/` | Firebase client, session store, NTP, country detection |
| `domain/` | Models, Repository (orchestrates data sources) |
| `presentation/` | ViewModel, UiState |
| `ui/` | Compose screens and components |
| `analytics/` | AnalyticsManager interface + no-op impl |
| `di/` | Koin module wiring |

**No UseCase layer.** Repository orchestrates non-trivially (`bootstrap` merges 4 sources, `flushPendingSession` has retry/guard logic). ViewModel calls Repository directly.

---

## Key Interfaces

```kotlin
interface NetworkTimeProvider {
    fun prime()
    fun getCompetitionWindow(): MohamedLoversCompetitionWindow
}

interface CountryCodeProvider {
    fun get(): String
}

interface AnalyticsManager {
    fun logAction(name: String, params: Map<String, String>)
}
```

---

## Data Flows

### Bootstrap (on init + refresh)
```
ViewModel.refresh()
  → Repository.bootstrap()
      → FirebaseClient.isConfigured()
      → CountryCodeProvider.get()
      → NetworkTimeProvider.getCompetitionWindow()
      → SessionStore.getPendingSession()
  → ViewModel patches UiState
  → Repository.flushPendingSession()
  → connectToLeaderboard() — starts Firebase real-time flows
```

### Tap (fast path, no I/O)
```
ViewModel.onCountClick()
  → SessionStore.incrementPendingClick()
  → ViewModel patches sessionClicks
  → applyLeaderboard() locally
```

### Flush (on stop / foreground)
```
ViewModel.flushPendingSession()
  → Repository.flushPendingSession(countryCode, fallbackRoundKey)
      → SessionStore.getPendingSession()
      → FirebaseClient.incrementSession()
      → SessionStore.clearPendingSession()
  → applyLeaderboard()
```

---

## DI Wiring (Koin)

```kotlin
// commonMain
val appModule = module {
    single { MohamedLoversFirebaseClient() }
    single { MohamedLoversSessionStore(get()) }
    single<AnalyticsManager> { NoOpAnalyticsManager() }
    single { MohamedLoversRepository(get(), get(), get(), get()) }
    viewModel { MohamedLoversViewModel(get()) }
}

// androidMain
val androidModule = module {
    single<NetworkTimeProvider> { KronosNetworkTimeProvider(get()) }
    single<CountryCodeProvider> { AndroidCountryCodeProvider(get()) }
}

// iosMain
val iosModule = module {
    single<NetworkTimeProvider> { IosNetworkTimeProvider() }
    single<CountryCodeProvider> { IosCountryCodeProvider() }
}
```

Koin started at app entry point (`MainActivity` / `MainViewController`) with `startKoin { modules(appModule, platformModule) }`.

---

## Library Changes

| Replaced | With |
|---|---|
| Hilt / `javax.inject` | Koin (`koin-core`, `koin-compose`, `koin-compose-viewmodel`) |
| `SharedPreferences` | `multiplatform-settings` (Russhwolf) |
| `java.time.*` | `kotlinx-datetime` |
| Firebase Android SDK | GitLive `firebase-kotlin-sdk` (`firebase-auth`, `firebase-database`) |
| Kronos | Stays in `androidMain` only |
| `EntryPointAccessors` (Hilt) | `koinInject()` in Composable |

---

## Error Handling

- `MohamedLoversError.Connection` — null/network errors
- `MohamedLoversError.Raw(message)` — exception message passthrough
- Screen shows Toast on error; ViewModel clears after show
- Flush failures logged + surfaced via `error` state; pending session preserved in store for retry on next flush

---

## Platform Notes

**Android NTP:** Kronos (`KronosNetworkTimeProvider`) — unchanged logic.  
**iOS NTP:** Device clock initially (`Clock.System.now()`). NTP sync can be added later via a background coroutine querying a time API.  
**Android country code:** `TelephonyManager` (network ISO → SIM ISO → locale fallback).  
**iOS country code:** `NSLocale.current.regionCode` fallback.  
**Firebase:** GitLive SDK wraps native Android + iOS Firebase SDKs — offline persistence, real-time listeners work on both platforms.
