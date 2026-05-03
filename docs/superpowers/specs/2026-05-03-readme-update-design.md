---
title: README Update — Screenshots, ADRs, C4 Models, Architecture, Features
date: 2026-05-03
status: approved
---

# README Update Design

**Goal:** Expand README.md into a complete project landing page with screenshots, features, architecture diagrams, C4 models, and ADRs — all inline in a single file.

**Approach chosen:** Option A — single README.md, all content inline (no separate docs files for diagrams/ADRs).

---

## Section Order

1. Header + Badges
2. Screenshots
3. Features
4. Architecture
5. C4 Models
6. ADRs
7. Build & Run (existing, updated)
8. Project Structure (existing, updated)

---

## Section Specs

### 1. Header + Badges

- App name: `SaloAleh`
- Tagline: one-line description of the app's purpose (weekly tap competition, Android + iOS)
- Badges: Android CI build status, iOS CI build status, platform (Kotlin Multiplatform), license
- Badge links point to GitHub Actions workflows in `.github/workflows/`

### 2. Screenshots

- 2-column table: Android | iOS
- 3 rows: Main counter screen, Leaderboard (inside info sheet), Achievements screen
- Each cell: `![Screen name](docs/screenshots/android-main.png)` style path
- Each cell has `<!-- Replace with actual screenshot -->` comment above it
- Placeholder dir: `docs/screenshots/` (to be created, add `.gitkeep`)
- Note below table: instructions for capturing screenshots

### 3. Features

Bullet list grouped by area:

**Core**
- Weekly tap counter — send salawat, count tracked per competition round
- Friday bonus — 2× multiplier on Fridays (Cairo timezone)
- Competition rounds — weekly cycles, reset every Friday at midnight Cairo time

**Leaderboard**
- Top 10 players globally, ranked by score
- Country code shown per player (flag emoji + ISO code)
- Anonymous — display tag derived from last 6 chars of hashed device ID
- Refreshes every 30 minutes

**Achievements**
- Streak badges: 7-day streak, 30-day streak
- Rank badges: 1st–10th place (earned at round end)
- Achievement celebration dialog on new badge unlock
- Achievements history screen

**Engagement**
- Daily open streak tracking
- Notification opt-in with rationale dialog

### 4. Architecture

Mermaid diagram showing layer stack:

```
UI (Compose Multiplatform)
  ↓
Presentation (ViewModel, UiState)
  ↓
Domain (Repository, Models)
  ↓
Data (FirebaseClient, SessionStore, NetworkTimeProvider, CountryCodeProvider)
```

- One-way dependency: each layer only depends on the layer below
- Platform abstractions via `expect`/`interface` pattern (NetworkTimeProvider, CountryCodeProvider)
- KMP shared module compiled to Android AAR + iOS XCFramework
- Android shell: `MainActivity` starts Koin with `androidModule`
- iOS shell: `MainViewController` starts Koin with `iosModule`

### 5. C4 Models

Three Mermaid diagrams (C1, C2, C3).

**C1 — System Context**
- Actor: User (Android or iOS device)
- System: SaloAleh App
- External system: Firebase Realtime Database (leaderboard sync)
- External system: NTP server (accurate competition window timing, Android only)

**C2 — Container**
- Android App (Kotlin + Compose)
- iOS App (Swift shell + Compose Multiplatform)
- KMP Shared Module (business logic, UI, DI)
- Firebase RTDB (cloud data store)
- Device Storage (multiplatform-settings, SharedPreferences/NSUserDefaults)

**C3 — Component (KMP Shared Module)**
- UI: MohamedLoversScreen, AchievementsScreen
- Presentation: MohamedLoversViewModel, AchievementsViewModel
- Domain: MohamedLoversRepository
- Data/Firebase: MohamedLoversFirebaseClient
- Data/Session: MohamedLoversSessionStore, EngagementStore
- Data/Time: NetworkTimeProvider (platform impl)
- Data/Country: CountryCodeProvider (platform impl)
- Data/Crypto: Sha256 (device identity)
- DI: AppModule (Koin)

### 6. ADRs

Inline section. Each ADR as a `###` heading with: **Status**, **Context**, **Decision**, **Consequences**.

ADR list:

| # | Title |
|---|-------|
| 0001 | Kotlin Multiplatform over separate Android and iOS codebases |
| 0002 | Koin over Hilt for dependency injection |
| 0003 | SHA-256 hashed UUID over Firebase Anonymous Authentication |
| 0004 | No UseCase layer — Repository as orchestrator |
| 0005 | GitLive Firebase Kotlin SDK over platform-specific Firebase SDKs |
| 0006 | multiplatform-settings over SharedPreferences / NSUserDefaults directly |

Each ADR: 3–5 sentences max. Context explains the problem. Decision states what was chosen. Consequences lists trade-offs.

### 7. Build & Run

Keep existing content. Add:
- Prerequisites table (tool | version)
- CI badge note pointing to GitHub Actions

### 8. Project Structure

Keep existing tree. Expand `app/src/commonMain/kotlin/` to show full package layout:
- `data/`, `domain/`, `presentation/`, `ui/`, `analytics/`, `di/`

---

## Constraints

- All diagrams use Mermaid (GitHub renders natively)
- No external diagram services
- Screenshot paths relative to repo root: `docs/screenshots/`
- No emoji in section headings (GitHub renders them but inconsistently in dark mode)
- ADRs inline only (no separate `docs/adr/` files) per Option A decision
