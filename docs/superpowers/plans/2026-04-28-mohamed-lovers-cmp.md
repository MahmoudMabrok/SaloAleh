# Mohamed Lovers CMP Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the existing Android-only Mohamed Lovers feature into a Compose Multiplatform `app` module targeting Android and iOS, restructured with clean architecture.

**Architecture:** `ui → presentation → domain → data` with no UseCase layer — ViewModel calls Repository directly. Repository is a legitimate orchestrator (bootstrap + flush). Platform-specific concerns (NTP, country code) are abstracted behind interfaces injected via Koin.

**Tech Stack:** Kotlin Multiplatform 2.1.20, Compose Multiplatform 1.7.3, Koin 4.0.0, GitLive firebase-kotlin-sdk 2.1.0, multiplatform-settings 1.2.0, kotlinx-datetime 0.6.1, Kronos (androidMain only)

---

## File Map

```
app/src/
  commonMain/kotlin/tools/mo3ta/salo/
    data/
      firebase/   MohamedLoversFirebaseClient.kt
      session/    MohamedLoversSessionStore.kt
      time/       NetworkTimeProvider.kt
                  CompetitionWindowUtils.kt
      country/    CountryCodeProvider.kt
    domain/
      MohamedLoversModels.kt
      MohamedLoversRepository.kt
    presentation/
      MohamedLoversUiState.kt
      MohamedLoversViewModel.kt
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
  commonTest/kotlin/tools/mo3ta/salo/
    domain/       MohamedLoversModelsTest.kt
    data/time/    CompetitionWindowUtilsTest.kt
    data/session/ MohamedLoversSessionStoreTest.kt

gradle/
  libs.versions.toml       (rewritten)
app/
  build.gradle.kts         (rewritten for KMP)
build.gradle.kts           (root — add KMP + CMP plugins)
settings.gradle.kts        (unchanged)
```

---

## Task 1: CMP Build Infrastructure

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Rewrite: `app/build.gradle.kts`
- Create source set directories

- [ ] **Step 1: Replace `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.13.2"
kotlin = "2.1.20"
composeMultiplatform = "1.7.3"
koin = "4.0.0"
gitliveFirebase = "2.1.0"
multiplatformSettings = "1.2.0"
kotlinxDatetime = "0.6.1"
kotlinxCoroutines = "1.10.1"
lifecycleViewmodel = "2.9.0"
kronosAndroid = "0.0.1-alpha11"
coreKtx = "1.16.0"
activityCompose = "1.10.1"
googleServices = "4.4.2"
junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.6.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
koin-core = { group = "io.insert-koin", name = "koin-core", version.ref = "koin" }
koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
koin-compose = { group = "io.insert-koin", name = "koin-compose", version.ref = "koin" }
koin-compose-viewmodel = { group = "io.insert-koin", name = "koin-compose-viewmodel", version.ref = "koin" }
gitlive-firebase-auth = { group = "dev.gitlive", name = "firebase-auth", version.ref = "gitliveFirebase" }
gitlive-firebase-database = { group = "dev.gitlive", name = "firebase-database", version.ref = "gitliveFirebase" }
multiplatform-settings = { group = "com.russhwolf", name = "multiplatform-settings", version.ref = "multiplatformSettings" }
multiplatform-settings-test = { group = "com.russhwolf", name = "multiplatform-settings-test", version.ref = "multiplatformSettings" }
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
androidx-lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel", version.ref = "lifecycleViewmodel" }
kronos-android = { group = "com.lyft.kronos", name = "kronos-android", version.ref = "kronosAndroid" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

- [ ] **Step 2: Replace root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.google.services) apply false
}
```

- [ ] **Step 3: Replace `app/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.database)
            implementation(libs.multiplatform.settings)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.lifecycle.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.kronos.android)
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }
    }
}

android {
    namespace = "tools.mo3ta.salo"
    compileSdk = 36

    defaultConfig {
        applicationId = "tools.mo3ta.salo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
```

- [ ] **Step 4: Create KMP source set directories**

```bash
mkdir -p app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase
mkdir -p app/src/commonMain/kotlin/tools/mo3ta/salo/data/session
mkdir -p app/src/commonMain/kotlin/tools/mo3ta/salo/data/time
mkdir -p app/src/commonMain/kotlin/tools/mo3ta/salo/data/country
mkdir -p app/src/commonMain/kotlin/tools/mo3ta/salo/domain
mkdir -p app/src/commonMain/kotlin/tools/mo3ta/salo/presentation
mkdir -p app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components
mkdir -p app/src/commonMain/kotlin/tools/mo3ta/salo/analytics
mkdir -p app/src/commonMain/kotlin/tools/mo3ta/salo/di
mkdir -p app/src/androidMain/kotlin/tools/mo3ta/salo/data/time
mkdir -p app/src/androidMain/kotlin/tools/mo3ta/salo/data/country
mkdir -p app/src/androidMain/kotlin/tools/mo3ta/salo/di
mkdir -p app/src/iosMain/kotlin/tools/mo3ta/salo/data/time
mkdir -p app/src/iosMain/kotlin/tools/mo3ta/salo/data/country
mkdir -p app/src/iosMain/kotlin/tools/mo3ta/salo/di
mkdir -p app/src/commonTest/kotlin/tools/mo3ta/salo/domain
mkdir -p app/src/commonTest/kotlin/tools/mo3ta/salo/data/time
mkdir -p app/src/commonTest/kotlin/tools/mo3ta/salo/data/session
```

- [ ] **Step 5: Verify Gradle sync**

```bash
./gradlew :app:compileKotlinAndroid --stacktrace 2>&1 | tail -30
```

Expected: build succeeds or fails only on missing source files (not on plugin/version issues).

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
git commit -m "build: convert app module to Compose Multiplatform (Android + iOS)"
```

---

## Task 2: Domain Models

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversModels.kt`
- Create: `app/src/commonTest/kotlin/tools/mo3ta/salo/domain/MohamedLoversModelsTest.kt`

- [ ] **Step 1: Write failing test**

Create `app/src/commonTest/kotlin/tools/mo3ta/salo/domain/MohamedLoversModelsTest.kt`:

```kotlin
package tools.mo3ta.salo.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MohamedLoversModelsTest {

    @Test
    fun displayTag_uses_last6_of_uid_and_uppercased_country() {
        assertEquals("EG • ABCD12", buildMohamedLoversDisplayTag("xxxxxABCD12", "eg"))
    }

    @Test
    fun displayTag_blank_uid_uses_dashes() {
        assertEquals("EG • ------", buildMohamedLoversDisplayTag("", "eg"))
    }

    @Test
    fun displayTag_blank_country_uses_NA() {
        assertEquals("NA • ABCDEF", buildMohamedLoversDisplayTag("xxxxxxABCDEF", ""))
    }
}
```

- [ ] **Step 2: Run test to see it fail**

```bash
./gradlew :app:commonTest --tests "tools.mo3ta.salo.domain.MohamedLoversModelsTest" 2>&1 | tail -20
```

Expected: FAIL — `buildMohamedLoversDisplayTag` not found.

- [ ] **Step 3: Create `MohamedLoversModels.kt`**

```kotlin
package tools.mo3ta.salo.domain

import kotlinx.datetime.Instant

data class MohamedLoversPlayer(
    val uid: String = "",
    val totalCount: Int = 0,
    val isWinner: Boolean = false,
    val winnerCode: String = "",
    val countryCode: String = "",
    val updatedAt: Long = 0L,
)

data class MohamedLoversPendingSession(
    val roundKey: String? = null,
    val clickCount: Int = 0,
)

data class MohamedLoversCompetitionWindow(
    val networkNow: Instant? = null,
    val isFridayBonus: Boolean = false,
    val roundKey: String? = null,
    val roundEnd: Instant? = null,
    val message: String? = null,
)

data class MohamedLoversBootstrap(
    val firebaseConfigured: Boolean,
    val countryCode: String,
    val competitionWindow: MohamedLoversCompetitionWindow,
    val pendingSession: MohamedLoversPendingSession,
)

const val MOHAMED_LOVERS_TOP_LIMIT = 10
const val MOHAMED_LOVERS_FRIDAY_MULTIPLIER = 2
const val MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE = "NA"

fun buildMohamedLoversDisplayTag(uid: String, countryCode: String): String {
    val tag = uid.takeLast(6).uppercase().ifBlank { "------" }
    val country = countryCode.uppercase().ifBlank { MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE }
    return "$country • $tag"
}
```

- [ ] **Step 4: Run test to see it pass**

```bash
./gradlew :app:commonTest --tests "tools.mo3ta.salo.domain.MohamedLoversModelsTest" 2>&1 | tail -20
```

Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversModels.kt \
        app/src/commonTest/kotlin/tools/mo3ta/salo/domain/MohamedLoversModelsTest.kt
git commit -m "feat: add domain models with kotlinx-datetime"
```

---

## Task 3: Interfaces + Analytics No-Op

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/time/NetworkTimeProvider.kt`
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/country/CountryCodeProvider.kt`
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/analytics/AnalyticsManager.kt`
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/analytics/NoOpAnalyticsManager.kt`

- [ ] **Step 1: Create `NetworkTimeProvider.kt`**

```kotlin
package tools.mo3ta.salo.data.time

import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow

interface NetworkTimeProvider {
    fun prime()
    fun getCompetitionWindow(): MohamedLoversCompetitionWindow
}
```

- [ ] **Step 2: Create `CountryCodeProvider.kt`**

```kotlin
package tools.mo3ta.salo.data.country

interface CountryCodeProvider {
    fun get(): String
}
```

- [ ] **Step 3: Create `AnalyticsManager.kt`**

```kotlin
package tools.mo3ta.salo.analytics

interface AnalyticsManager {
    fun logAction(name: String, params: Map<String, String> = emptyMap())
}
```

- [ ] **Step 4: Create `NoOpAnalyticsManager.kt`**

```kotlin
package tools.mo3ta.salo.analytics

class NoOpAnalyticsManager : AnalyticsManager {
    override fun logAction(name: String, params: Map<String, String>) = Unit
}
```

- [ ] **Step 5: Verify compile**

```bash
./gradlew :app:compileKotlinAndroid 2>&1 | grep -E "error:|warning:" | head -20
```

Expected: no errors from these files.

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/time/NetworkTimeProvider.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/data/country/CountryCodeProvider.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/analytics/AnalyticsManager.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/analytics/NoOpAnalyticsManager.kt
git commit -m "feat: add NetworkTimeProvider, CountryCodeProvider, AnalyticsManager interfaces"
```

---

## Task 4: Competition Window Shared Logic

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/time/CompetitionWindowUtils.kt`
- Create: `app/src/commonTest/kotlin/tools/mo3ta/salo/data/time/CompetitionWindowUtilsTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package tools.mo3ta.salo.data.time

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompetitionWindowUtilsTest {

    private val cairo = TimeZone.of("Africa/Cairo")

    @Test
    fun roundKey_is_next_friday_date_string() {
        // Monday 2026-04-27 10:00 Cairo
        val instant = Instant.parse("2026-04-27T08:00:00Z") // UTC = Cairo - 2h
        val window = buildCompetitionWindow(instant)
        assertEquals("2026-05-01", window.roundKey) // next Friday
    }

    @Test
    fun friday_before_18h_is_bonus_and_same_day_round() {
        // Friday 2026-05-01 10:00 Cairo (UTC 08:00)
        val instant = Instant.parse("2026-05-01T08:00:00Z")
        val window = buildCompetitionWindow(instant)
        assertTrue(window.isFridayBonus)
        assertEquals("2026-05-01", window.roundKey)
    }

    @Test
    fun friday_after_18h_is_not_bonus_and_next_week_round() {
        // Friday 2026-05-01 19:00 Cairo (UTC 17:00)
        val instant = Instant.parse("2026-05-01T17:00:00Z")
        val window = buildCompetitionWindow(instant)
        assertFalse(window.isFridayBonus)
        assertEquals("2026-05-08", window.roundKey)
    }
}
```

- [ ] **Step 2: Run test to see it fail**

```bash
./gradlew :app:commonTest --tests "tools.mo3ta.salo.data.time.CompetitionWindowUtilsTest" 2>&1 | tail -20
```

Expected: FAIL — `buildCompetitionWindow` not found.

- [ ] **Step 3: Create `CompetitionWindowUtils.kt`**

```kotlin
package tools.mo3ta.salo.data.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow

internal const val ROUND_BOUNDARY_HOUR = 18
private val cairoZone = TimeZone.of("Africa/Cairo")

internal fun buildCompetitionWindow(networkNow: Instant): MohamedLoversCompetitionWindow {
    val localNow = networkNow.toLocalDateTime(cairoZone)
    val isFridayBonus = localNow.dayOfWeek == DayOfWeek.FRIDAY && localNow.hour < ROUND_BOUNDARY_HOUR
    val roundEnd = nextRoundBoundary(localNow)
    return MohamedLoversCompetitionWindow(
        networkNow = networkNow,
        isFridayBonus = isFridayBonus,
        roundKey = roundEnd.toLocalDateTime(cairoZone).date.toString(),
        roundEnd = roundEnd,
        message = null,
    )
}

private fun nextRoundBoundary(now: LocalDateTime): Instant {
    val daysUntilFriday = ((DayOfWeek.FRIDAY.isoDayNumber - now.dayOfWeek.isoDayNumber + 7) % 7).toLong()
    val candidate = now.date
        .plus(daysUntilFriday, DateTimeUnit.DAY)
        .atTime(ROUND_BOUNDARY_HOUR, 0)
        .toInstant(cairoZone)
    val nowInstant = now.toInstant(cairoZone)
    return if (nowInstant < candidate) candidate else candidate.plus(7, DateTimeUnit.DAY, cairoZone)
}
```

- [ ] **Step 4: Run tests to see them pass**

```bash
./gradlew :app:commonTest --tests "tools.mo3ta.salo.data.time.CompetitionWindowUtilsTest" 2>&1 | tail -20
```

Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/time/CompetitionWindowUtils.kt \
        app/src/commonTest/kotlin/tools/mo3ta/salo/data/time/CompetitionWindowUtilsTest.kt
git commit -m "feat: add competition window calculation logic (kotlinx-datetime)"
```

---

## Task 5: Session Store

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStore.kt`
- Create: `app/src/commonTest/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStoreTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package tools.mo3ta.salo.data.session

import com.russhwolf.settings.MapSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MohamedLoversSessionStoreTest {

    private lateinit var store: MohamedLoversSessionStore

    @BeforeTest
    fun setup() {
        store = MohamedLoversSessionStore(MapSettings())
    }

    @Test
    fun pendingSession_initially_empty() {
        val session = store.getPendingSession()
        assertNull(session.roundKey)
        assertEquals(0, session.clickCount)
    }

    @Test
    fun incrementPendingClick_accumulates_in_same_round() {
        store.incrementPendingClick("2026-05-01", 1)
        store.incrementPendingClick("2026-05-01", 2)
        val session = store.getPendingSession()
        assertEquals("2026-05-01", session.roundKey)
        assertEquals(3, session.clickCount)
    }

    @Test
    fun incrementPendingClick_resets_on_new_round() {
        store.incrementPendingClick("2026-05-01", 5)
        store.incrementPendingClick("2026-05-08", 1)
        val session = store.getPendingSession()
        assertEquals("2026-05-08", session.roundKey)
        assertEquals(1, session.clickCount)
    }

    @Test
    fun clearPendingSession_removes_stored_session() {
        store.incrementPendingClick("2026-05-01", 3)
        store.clearPendingSession()
        val session = store.getPendingSession()
        assertNull(session.roundKey)
        assertEquals(0, session.clickCount)
    }

    @Test
    fun getOrCreateAlias_stable_across_calls() {
        val first = store.getOrCreateAlias()
        val second = store.getOrCreateAlias()
        assertEquals(first, second)
        assertTrue(first.startsWith("محب محمد "))
    }
}
```

- [ ] **Step 2: Run test to see it fail**

```bash
./gradlew :app:commonTest --tests "tools.mo3ta.salo.data.session.MohamedLoversSessionStoreTest" 2>&1 | tail -20
```

Expected: FAIL — `MohamedLoversSessionStore` not found.

- [ ] **Step 3: Create `MohamedLoversSessionStore.kt`**

```kotlin
package tools.mo3ta.salo.data.session

import com.russhwolf.settings.Settings
import tools.mo3ta.salo.domain.MohamedLoversPendingSession

class MohamedLoversSessionStore(private val settings: Settings) {

    fun getOrCreateAlias(): String {
        settings.getStringOrNull(KEY_ALIAS)?.takeIf { it.isNotBlank() }?.let { return it }
        val suffix = (1..4).map { ALIAS_CHARS[kotlin.random.Random.nextInt(ALIAS_CHARS.length)] }.joinToString("")
        val alias = "محب محمد $suffix"
        settings.putString(KEY_ALIAS, alias)
        return alias
    }

    fun getPendingSession(): MohamedLoversPendingSession = MohamedLoversPendingSession(
        roundKey = settings.getStringOrNull(KEY_PENDING_ROUND),
        clickCount = settings.getIntOrDefault(KEY_PENDING_COUNT, 0),
    )

    fun incrementPendingClick(roundKey: String, delta: Int = 1): MohamedLoversPendingSession {
        val currentRoundKey = settings.getStringOrNull(KEY_PENDING_ROUND)
        val currentCount = if (currentRoundKey == roundKey) settings.getIntOrDefault(KEY_PENDING_COUNT, 0) else 0
        val updated = MohamedLoversPendingSession(
            roundKey = roundKey,
            clickCount = currentCount + delta.coerceAtLeast(1),
        )
        settings.putString(KEY_PENDING_ROUND, roundKey)
        settings.putInt(KEY_PENDING_COUNT, updated.clickCount)
        return updated
    }

    fun clearPendingSession() {
        settings.remove(KEY_PENDING_ROUND)
        settings.remove(KEY_PENDING_COUNT)
    }

    private companion object {
        const val KEY_ALIAS = "alias"
        const val KEY_PENDING_ROUND = "pending_round_key"
        const val KEY_PENDING_COUNT = "pending_click_count"
        const val ALIAS_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }
}
```

- [ ] **Step 4: Run tests to see them pass**

```bash
./gradlew :app:commonTest --tests "tools.mo3ta.salo.data.session.MohamedLoversSessionStoreTest" 2>&1 | tail -20
```

Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStore.kt \
        app/src/commonTest/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStoreTest.kt
git commit -m "feat: add MohamedLoversSessionStore (multiplatform-settings)"
```

---

## Task 6: Firebase Client

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseClient.kt`

Note: Firebase requires `google-services.json` in `app/` (Android) and `GoogleService-Info.plist` in the Xcode project (iOS). These are project-specific files not included here — add them before running the app.

- [ ] **Step 1: Create `MohamedLoversFirebaseClient.kt`**

```kotlin
package tools.mo3ta.salo.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_TOP_LIMIT
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE
import tools.mo3ta.salo.domain.MohamedLoversPlayer

class MohamedLoversFirebaseClient {

    private val authMutex = Mutex()

    fun isConfigured(): Boolean = runCatching { Firebase.app }.isSuccess

    suspend fun ensureSignedInAnonymously(): Result<String> = authMutex.withLock {
        runCatching {
            Firebase.auth.currentUser?.uid ?: run {
                Firebase.auth.signInAnonymously().user?.uid
                    ?: error("Firebase anonymous sign-in returned no user.")
            }
        }
    }

    fun observeTopPlayers(
        roundKey: String,
        limit: Int = MOHAMED_LOVERS_TOP_LIMIT,
    ): Flow<Result<List<MohamedLoversPlayer>>> =
        Firebase.database.reference(playersPath(roundKey))
            .orderByChild(TOTAL_COUNT_KEY)
            .limitToLast(limit)
            .valueEvents
            .map { snapshot -> runCatching { snapshot.children.mapNotNull { it.toPlayer() } } }

    fun observeSelfPlayer(
        roundKey: String,
        uid: String,
    ): Flow<Result<MohamedLoversPlayer?>> =
        Firebase.database.reference(playersPath(roundKey)).child(uid)
            .valueEvents
            .map { snapshot -> runCatching { snapshot.takeIf { it.exists }?.toPlayer() } }

    suspend fun incrementSession(
        roundKey: String,
        uid: String,
        delta: Int,
        countryCode: String,
    ): Result<Unit> = runCatching {
        val safeCode = countryCode.takeIf { it.length >= 2 } ?: MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE
        Firebase.database.reference(playersPath(roundKey)).child(uid).runTransaction {
            val current = value as? Map<*, *> ?: emptyMap<String, Any?>()
            val existingTotal = (current[TOTAL_COUNT_KEY] as? Number)?.toInt() ?: 0
            value = mapOf(
                UID_KEY to uid,
                COUNTRY_CODE_KEY to safeCode,
                TOTAL_COUNT_KEY to (existingTotal + delta),
                IS_WINNER_KEY to (current[IS_WINNER_KEY] as? Boolean ?: false),
                WINNER_CODE_KEY to (current[WINNER_CODE_KEY] as? String ?: ""),
                UPDATED_AT_KEY to ServerValue.TIMESTAMP,
            )
        }
    }

    private fun playersPath(roundKey: String) = "$ROOT_PATH/$roundKey/$PLAYERS_PATH"

    private fun dev.gitlive.firebase.database.DataSnapshot.toPlayer(): MohamedLoversPlayer? {
        val map = value as? Map<*, *> ?: return null
        val uid = map[UID_KEY] as? String ?: key ?: return null
        return MohamedLoversPlayer(
            uid = uid,
            totalCount = (map[TOTAL_COUNT_KEY] as? Number)?.toInt() ?: 0,
            isWinner = map[IS_WINNER_KEY] as? Boolean ?: false,
            winnerCode = map[WINNER_CODE_KEY] as? String ?: "",
            countryCode = map[COUNTRY_CODE_KEY] as? String ?: "",
            updatedAt = (map[UPDATED_AT_KEY] as? Number)?.toLong() ?: 0L,
        )
    }

    private companion object {
        const val ROOT_PATH = "mohamed_lovers"
        const val PLAYERS_PATH = "players"
        const val UID_KEY = "uid"
        const val TOTAL_COUNT_KEY = "totalCount"
        const val IS_WINNER_KEY = "isWinner"
        const val WINNER_CODE_KEY = "winnerCode"
        const val COUNTRY_CODE_KEY = "countryCode"
        const val UPDATED_AT_KEY = "updatedAt"
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :app:compileKotlinAndroid 2>&1 | grep -E "error:" | head -20
```

Expected: no errors from `MohamedLoversFirebaseClient`.

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseClient.kt
git commit -m "feat: add MohamedLoversFirebaseClient (GitLive firebase-kotlin-sdk)"
```

---

## Task 7: Android Platform Implementations

**Files:**
- Create: `app/src/androidMain/kotlin/tools/mo3ta/salo/data/time/KronosNetworkTimeProvider.kt`
- Create: `app/src/androidMain/kotlin/tools/mo3ta/salo/data/country/AndroidCountryCodeProvider.kt`

- [ ] **Step 1: Create `KronosNetworkTimeProvider.kt`**

```kotlin
package tools.mo3ta.salo.data.time

import android.content.Context
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import kotlinx.datetime.Instant
import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow

class KronosNetworkTimeProvider(context: Context) : NetworkTimeProvider {

    private val kronosClock: KronosClock = AndroidClockFactory.createKronosClock(
        context = context,
        ntpHosts = listOf("time.google.com", "0.africa.pool.ntp.org", "1.africa.pool.ntp.org"),
    )

    override fun prime() {
        kronosClock.syncInBackground()
    }

    override fun getCompetitionWindow(): MohamedLoversCompetitionWindow {
        val ms = kronosClock.getCurrentNtpTimeMs() ?: run {
            prime()
            return MohamedLoversCompetitionWindow(message = "جارٍ مزامنة الوقت من الشبكة.")
        }
        return buildCompetitionWindow(Instant.fromEpochMilliseconds(ms))
    }
}
```

- [ ] **Step 2: Create `AndroidCountryCodeProvider.kt`**

```kotlin
package tools.mo3ta.salo.data.country

import android.content.Context
import android.telephony.TelephonyManager
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE

class AndroidCountryCodeProvider(private val context: Context) : CountryCodeProvider {

    override fun get(): String {
        val telephonyIso = runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.networkCountryIso?.takeIf { it.isNotBlank() }
                ?: tm?.simCountryIso?.takeIf { it.isNotBlank() }
        }.getOrNull()

        val localeIso = context.resources.configuration.locales[0].country
            .takeIf { it.isNotBlank() }

        val resolved = (telephonyIso ?: localeIso)?.uppercase()
        return resolved?.takeIf { it.length >= 2 } ?: MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE
    }
}
```

- [ ] **Step 3: Verify Android compile**

```bash
./gradlew :app:compileKotlinAndroid 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/data/time/KronosNetworkTimeProvider.kt \
        app/src/androidMain/kotlin/tools/mo3ta/salo/data/country/AndroidCountryCodeProvider.kt
git commit -m "feat: add Android NTP (Kronos) and country code implementations"
```

---

## Task 8: iOS Platform Implementations

**Files:**
- Create: `app/src/iosMain/kotlin/tools/mo3ta/salo/data/time/IosNetworkTimeProvider.kt`
- Create: `app/src/iosMain/kotlin/tools/mo3ta/salo/data/country/IosCountryCodeProvider.kt`

- [ ] **Step 1: Create `IosNetworkTimeProvider.kt`**

```kotlin
package tools.mo3ta.salo.data.time

import kotlinx.datetime.Clock
import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow

class IosNetworkTimeProvider : NetworkTimeProvider {
    override fun prime() = Unit
    override fun getCompetitionWindow(): MohamedLoversCompetitionWindow =
        buildCompetitionWindow(Clock.System.now())
}
```

- [ ] **Step 2: Create `IosCountryCodeProvider.kt`**

```kotlin
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
```

- [ ] **Step 3: Verify iOS compile**

```bash
./gradlew :app:compileKotlinIosSimulatorArm64 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add app/src/iosMain/kotlin/tools/mo3ta/salo/data/time/IosNetworkTimeProvider.kt \
        app/src/iosMain/kotlin/tools/mo3ta/salo/data/country/IosCountryCodeProvider.kt
git commit -m "feat: add iOS device-clock NTP and NSLocale country code implementations"
```

---

## Task 9: Repository

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversRepository.kt`

- [ ] **Step 1: Create `MohamedLoversRepository.kt`**

```kotlin
package tools.mo3ta.salo.domain

import kotlinx.coroutines.flow.Flow
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseClient
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.data.time.NetworkTimeProvider

class MohamedLoversRepository(
    private val firebaseClient: MohamedLoversFirebaseClient,
    private val networkTimeProvider: NetworkTimeProvider,
    private val sessionStore: MohamedLoversSessionStore,
    private val countryCodeProvider: CountryCodeProvider,
) {
    suspend fun bootstrap(): MohamedLoversBootstrap = MohamedLoversBootstrap(
        firebaseConfigured = firebaseClient.isConfigured(),
        countryCode = countryCodeProvider.get(),
        competitionWindow = networkTimeProvider.getCompetitionWindow(),
        pendingSession = sessionStore.getPendingSession(),
    )

    suspend fun ensureAnonymousUser(): Result<String> = firebaseClient.ensureSignedInAnonymously()

    fun observeTopPlayers(roundKey: String): Flow<Result<List<MohamedLoversPlayer>>> =
        firebaseClient.observeTopPlayers(roundKey)

    fun observeSelfPlayer(roundKey: String, uid: String): Flow<Result<MohamedLoversPlayer?>> =
        firebaseClient.observeSelfPlayer(roundKey, uid)

    fun registerLocalTap(roundKey: String, delta: Int = 1): MohamedLoversPendingSession =
        sessionStore.incrementPendingClick(roundKey, delta)

    fun getPendingSession(): MohamedLoversPendingSession = sessionStore.getPendingSession()

    suspend fun flushPendingSession(
        countryCode: String,
        fallbackRoundKey: String? = null,
    ): Result<Unit> {
        val pending = sessionStore.getPendingSession()
        val roundKey = pending.roundKey?.takeIf { it.isNotBlank() }
            ?: fallbackRoundKey?.takeIf { it.isNotBlank() }
            ?: return Result.success(Unit)

        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }

        val result = firebaseClient.incrementSession(
            roundKey = roundKey,
            uid = uid,
            delta = pending.clickCount.coerceAtLeast(0),
            countryCode = countryCode,
        )

        result.onSuccess { if (pending.clickCount > 0) sessionStore.clearPendingSession() }
        return result
    }

    fun refreshNetworkTime() = networkTimeProvider.prime()
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :app:compileKotlinAndroid 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversRepository.kt
git commit -m "feat: add MohamedLoversRepository (clean arch orchestrator)"
```

---

## Task 10: Presentation Layer

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt`
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt`

- [ ] **Step 1: Create `MohamedLoversUiState.kt`**

```kotlin
package tools.mo3ta.salo.presentation

data class MohamedLoversLeaderboardEntry(
    val rank: Int,
    val displayTag: String,
    val totalCount: Int,
    val isCurrentUser: Boolean,
)

enum class MohamedLoversStatus { WaitingNetwork, FirebaseOff, Open }

sealed interface MohamedLoversError {
    data object Connection : MohamedLoversError
    data class Raw(val message: String) : MohamedLoversError
}

data class MohamedLoversUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSavingSession: Boolean = false,
    val countryCode: String = "",
    val selfDisplayTag: String = "",
    val status: MohamedLoversStatus = MohamedLoversStatus.WaitingNetwork,
    val firebaseConfigured: Boolean = true,
    val isFridayBonus: Boolean = false,
    val roundKey: String? = null,
    val roundEndLabel: String = "",
    val networkTimeLabel: String = "",
    val canCount: Boolean = false,
    val syncedTotal: Int = 0,
    val sessionClicks: Int = 0,
    val isWinner: Boolean = false,
    val winnerCode: String = "",
    val selfEntry: MohamedLoversLeaderboardEntry? = null,
    val selfInTop: Boolean = false,
    val topPlayers: List<MohamedLoversLeaderboardEntry> = emptyList(),
    val error: MohamedLoversError? = null,
)
```

- [ ] **Step 2: Create `MohamedLoversViewModel.kt`**

```kotlin
package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_FRIDAY_MULTIPLIER
import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow
import tools.mo3ta.salo.domain.MohamedLoversPlayer
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.domain.buildMohamedLoversDisplayTag

class MohamedLoversViewModel(
    private val repository: MohamedLoversRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MohamedLoversUiState())
    val state: StateFlow<MohamedLoversUiState> = _state.asStateFlow()

    private val flushMutex = Mutex()
    private var topJob: Job? = null
    private var selfJob: Job? = null
    private var remoteTopPlayers: List<MohamedLoversPlayer> = emptyList()
    private var remoteSelfPlayer: MohamedLoversPlayer? = null
    private var authUid: String? = null
    private var currentWindow: MohamedLoversCompetitionWindow = MohamedLoversCompetitionWindow()

    init { refresh() }

    fun refresh() {
        repository.refreshNetworkTime()
        topJob?.cancel()
        selfJob?.cancel()
        remoteTopPlayers = emptyList()
        remoteSelfPlayer = null
        authUid = null

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true, isRefreshing = true, error = null,
                    topPlayers = emptyList(), selfEntry = null, selfInTop = false,
                    winnerCode = "", syncedTotal = 0,
                )
            }

            val bootstrap = repository.bootstrap()
            currentWindow = bootstrap.competitionWindow

            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    countryCode = bootstrap.countryCode,
                    firebaseConfigured = bootstrap.firebaseConfigured,
                    isFridayBonus = bootstrap.competitionWindow.isFridayBonus,
                    roundKey = bootstrap.competitionWindow.roundKey,
                    roundEndLabel = bootstrap.competitionWindow.roundEnd?.formatDisplay().orEmpty(),
                    networkTimeLabel = bootstrap.competitionWindow.networkNow?.formatDisplay().orEmpty(),
                    status = resolveStatus(bootstrap.firebaseConfigured, bootstrap.competitionWindow),
                    canCount = bootstrap.competitionWindow.networkNow != null,
                    sessionClicks = bootstrap.pendingSession.clickCount,
                    error = null,
                )
            }

            flushPendingSession()
            connectToLeaderboardIfPossible()
        }
    }

    fun onCountClick() {
        val current = state.value
        val roundKey = current.roundKey ?: return
        if (!current.canCount) return

        val delta = if (current.isFridayBonus) MOHAMED_LOVERS_FRIDAY_MULTIPLIER else 1
        val pending = repository.registerLocalTap(roundKey, delta)
        _state.update { it.copy(sessionClicks = pending.clickCount, error = null) }
        applyLeaderboard()
    }

    fun flushPendingSession() {
        viewModelScope.launch {
            flushMutex.withLock {
                val roundKey = state.value.roundKey
                if (!state.value.firebaseConfigured) {
                    _state.update { it.copy(isSavingSession = false) }
                    applyLeaderboard()
                    return@withLock
                }
                if (roundKey.isNullOrBlank()) {
                    _state.update { it.copy(isSavingSession = false) }
                    applyLeaderboard()
                    return@withLock
                }

                _state.update { it.copy(isSavingSession = true, error = null) }

                val result = repository.flushPendingSession(
                    countryCode = state.value.countryCode,
                    fallbackRoundKey = roundKey,
                )
                val latestPending = repository.getPendingSession()

                _state.update {
                    it.copy(
                        isSavingSession = false,
                        sessionClicks = latestPending.clickCount,
                        error = result.exceptionOrNull()?.message
                            ?.takeIf { msg -> msg.isNotBlank() }
                            ?.let(MohamedLoversError::Raw),
                    )
                }
                applyLeaderboard()
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun connectToLeaderboardIfPossible() {
        val roundKey = state.value.roundKey
        if (!state.value.firebaseConfigured || roundKey.isNullOrBlank()) {
            topJob?.cancel(); selfJob?.cancel()
            remoteTopPlayers = emptyList(); remoteSelfPlayer = null
            applyLeaderboard()
            return
        }

        viewModelScope.launch {
            val uid = repository.ensureAnonymousUser().getOrElse {
                _state.update {
                    it.copy(
                        error = it.error ?: it.extractError(it.error)
                            ?: MohamedLoversError.Connection,
                    )
                }
                applyLeaderboard()
                return@launch
            }

            authUid = uid
            _state.update { it.copy(selfDisplayTag = buildMohamedLoversDisplayTag(uid, it.countryCode)) }

            topJob?.cancel()
            topJob = launch {
                repository.observeTopPlayers(roundKey).collectLatest { result ->
                    result.onSuccess { players -> remoteTopPlayers = players; applyLeaderboard() }
                        .onFailure { t -> _state.update { it.copy(error = t.toLoversError()) } }
                }
            }

            selfJob?.cancel()
            selfJob = launch {
                repository.observeSelfPlayer(roundKey, uid).collectLatest { result ->
                    result.onSuccess { player -> remoteSelfPlayer = player; applyLeaderboard() }
                        .onFailure { t -> _state.update { it.copy(error = t.toLoversError()) } }
                }
            }
        }
    }

    private fun applyLeaderboard() {
        val uid = authUid
        val selfRemoteTotal = remoteSelfPlayer?.totalCount ?: 0
        val selfProjectedTotal = selfRemoteTotal + state.value.sessionClicks

        val sortedTop = remoteTopPlayers.sortedWith(
            compareByDescending<MohamedLoversPlayer> { it.totalCount }
                .thenByDescending { it.updatedAt }
                .thenBy { it.uid },
        )

        val topEntries = sortedTop.mapIndexed { index, player ->
            val isCurrentUser = player.uid == uid
            MohamedLoversLeaderboardEntry(
                rank = index + 1,
                displayTag = buildMohamedLoversDisplayTag(player.uid, player.countryCode),
                totalCount = if (isCurrentUser) selfProjectedTotal else player.totalCount,
                isCurrentUser = isCurrentUser,
            )
        }

        val selfEntry = when {
            uid == null || selfProjectedTotal <= 0 -> null
            else -> MohamedLoversLeaderboardEntry(
                rank = 0,
                displayTag = buildMohamedLoversDisplayTag(
                    uid,
                    remoteSelfPlayer?.countryCode?.ifBlank { state.value.countryCode }
                        ?: state.value.countryCode,
                ),
                totalCount = selfProjectedTotal,
                isCurrentUser = true,
            )
        }

        _state.update {
            it.copy(
                syncedTotal = selfRemoteTotal,
                isWinner = remoteSelfPlayer?.isWinner == true,
                winnerCode = remoteSelfPlayer?.winnerCode.orEmpty(),
                topPlayers = topEntries,
                selfEntry = selfEntry,
                selfInTop = uid != null && topEntries.any { e -> e.isCurrentUser },
            )
        }
    }

    private fun resolveStatus(
        firebaseConfigured: Boolean,
        window: MohamedLoversCompetitionWindow,
    ) = when {
        window.networkNow == null -> MohamedLoversStatus.WaitingNetwork
        !firebaseConfigured -> MohamedLoversStatus.FirebaseOff
        else -> MohamedLoversStatus.Open
    }

    private fun MohamedLoversUiState.extractError(e: MohamedLoversError?) = e
    private fun Throwable.toLoversError(): MohamedLoversError =
        message?.takeIf { it.isNotBlank() }?.let(MohamedLoversError::Raw) ?: MohamedLoversError.Connection
}

private fun kotlinx.datetime.Instant.formatDisplay(): String {
    val local = toLocalDateTime(TimeZone.of("Africa/Cairo"))
    val hour = local.hour
    val ampm = if (hour < 12) "AM" else "PM"
    val h12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
    val m = local.minute.toString().padStart(2, '0')
    val mo = local.monthNumber.toString().padStart(2, '0')
    val d = local.dayOfMonth.toString().padStart(2, '0')
    return "${local.year}/$mo/$d - $h12:$m $ampm"
}
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew :app:compileKotlinAndroid 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt
git commit -m "feat: add presentation layer (UiState + ViewModel)"
```

---

## Task 11: UI Components

**Files (create all in `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/`):**
- `MohamedLoversPalette.kt`
- `MohamedLoversFonts.kt`
- `MohamedLoversCounter.kt`
- `MohamedLoversArchShrine.kt`
- `MohamedLoversHadithBanner.kt`
- `MohamedLoversInfoSheet.kt`
- `MohamedLoversPrayerOverlay.kt`
- `MohamedLoversSkyBackground.kt`

These files are migrated from `app/src/main/java/tools/mo3ta/salo/ui/mohamedlovers/components/`. The main changes are: (1) update `package` to `tools.mo3ta.salo.ui.components`, (2) fix imports to `tools.mo3ta.salo.*`, (3) remove `com.elsharif.dailyseventy.R` references — CMP uses `stringResource` from compose resources, not Android `R`.

- [ ] **Step 1: Create `MohamedLoversPalette.kt`**

Copy `MohamedLoversColors.kt` content, rename to `MohamedLoversPalette.kt`, fix package:

```kotlin
package tools.mo3ta.salo.ui.components

import androidx.compose.ui.graphics.Color

object MohamedLoversPalette {
    val NightSky = Color(0xFF0A0A1A)
    val DeepNight = Color(0xFF060612)
    val StarGlow = Color(0xFFFFF8E7)
    val GoldGlow = Color(0xFFFFD700)
    val GoldHighlight = Color(0xFFFFF0A0)
    val MoonSilver = Color(0xFFE8E8FF)
    val ArcLight = Color(0xFF8888FF)
    val ShrineBase = Color(0xFF1A1A3A)
}
```

(Use whatever color values are in the existing `MohamedLoversColors.kt` — copy them exactly.)

- [ ] **Step 2: Create `MohamedLoversFonts.kt`**

Copy from existing file, fix package and imports:

```kotlin
package tools.mo3ta.salo.ui.components

// Copy exact content from:
// app/src/main/java/tools/mo3ta/salo/ui/mohamedlovers/components/MohamedLoversFonts.kt
// Change only: package declaration and any R.font.* references to use compose resources
```

- [ ] **Step 3: Create remaining 6 components**

For each file in `app/src/main/java/tools/mo3ta/salo/ui/mohamedlovers/components/`:
- `MohamedLoversCounter.kt`
- `MohamedLoversArchShrine.kt`
- `MohamedLoversHadithBanner.kt`
- `MohamedLoversInfoSheet.kt`
- `MohamedLoversPrayerOverlay.kt`
- `MohamedLoversSkyBackground.kt`

Create the corresponding file in `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/` with these changes:
1. Package: `tools.mo3ta.salo.ui.components`
2. Imports from `com.elsharif.dailyseventy.*` → `tools.mo3ta.salo.*`
3. `MohamedLoversPalette` (was `MohamedLoversColors` object) — update references
4. `stringResource(R.string.*)` → keep using `stringResource` but the resource IDs must come from CMP's `composeResources` (see note below)
5. `MohamedLoversUiState` import → `tools.mo3ta.salo.presentation.MohamedLoversUiState`

**Note on string resources:** CMP uses `composeResources/` instead of `res/values/strings.xml`. String resources used in components need to be added to `app/src/commonMain/composeResources/values/strings.xml`. Create this file with all strings from the existing `app/src/main/res/values/strings.xml` that are referenced in these components.

- [ ] **Step 4: Create `app/src/commonMain/composeResources/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Copy all mohamed_lovers_* strings from app/src/main/res/values/strings.xml -->
</resources>
```

- [ ] **Step 5: Verify compile**

```bash
./gradlew :app:compileKotlinAndroid 2>&1 | grep -E "error:" | head -30
```

Fix any import errors. Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/ \
        app/src/commonMain/composeResources/
git commit -m "feat: migrate UI components to commonMain (CMP)"
```

---

## Task 12: Screen + App Entry Composable

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt`
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt`

- [ ] **Step 1: Create `MohamedLoversScreen.kt`**

Migrate from `app/src/main/java/tools/mo3ta/salo/ui/mohamedlovers/MohamedLoversScreen.kt` with these changes:

1. Package: `tools.mo3ta.salo.ui`
2. Remove `EntryPointAccessors` / Hilt — inject `AnalyticsManager` via `koinInject()`
3. Remove `hiltViewModel()` — use `koinViewModel()`
4. `LocalLifecycleOwner` / `LifecycleEventObserver` — these are AndroidX, available in CMP via `androidx.lifecycle:lifecycle-runtime-compose`; keep as-is
5. Fix all import paths to `tools.mo3ta.salo.*`

```kotlin
package tools.mo3ta.salo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.presentation.MohamedLoversError
import tools.mo3ta.salo.presentation.MohamedLoversStatus
import tools.mo3ta.salo.presentation.MohamedLoversViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversArchShrine
import tools.mo3ta.salo.ui.components.MohamedLoversCounter
import tools.mo3ta.salo.ui.components.MohamedLoversHadithBanner
import tools.mo3ta.salo.ui.components.MohamedLoversInfoSheet
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.components.MohamedLoversPrayerOverlay
import tools.mo3ta.salo.ui.components.MohamedLoversSkyBackground
import mohamedlovers.composeapp.generated.resources.Res
import mohamedlovers.composeapp.generated.resources.mohamed_lovers_back_cd
import mohamedlovers.composeapp.generated.resources.mohamed_lovers_blocked_firebase_off
import mohamedlovers.composeapp.generated.resources.mohamed_lovers_blocked_waiting_network
import mohamedlovers.composeapp.generated.resources.mohamed_lovers_code_copied
import mohamedlovers.composeapp.generated.resources.mohamed_lovers_connection_error
import mohamedlovers.composeapp.generated.resources.mohamed_lovers_info_cd
import mohamedlovers.composeapp.generated.resources.mohamed_lovers_prayer_text
import mohamedlovers.composeapp.generated.resources.mohamed_lovers_reward_text

@Composable
fun MohamedLoversScreen(
    onBackClick: () -> Unit,
    viewModel: MohamedLoversViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyticsManager: AnalyticsManager = koinInject()

    val codeCopiedLabel = stringResource(Res.string.mohamed_lovers_code_copied)
    val connectionErrorLabel = stringResource(Res.string.mohamed_lovers_connection_error)
    val prayerText = stringResource(Res.string.mohamed_lovers_prayer_text)
    val rewardText = stringResource(Res.string.mohamed_lovers_reward_text)
    val waitingNetworkLabel = stringResource(Res.string.mohamed_lovers_blocked_waiting_network)
    val firebaseOffLabel = stringResource(Res.string.mohamed_lovers_blocked_firebase_off)
    val backCd = stringResource(Res.string.mohamed_lovers_back_cd)
    val infoCd = stringResource(Res.string.mohamed_lovers_info_cd)

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushPendingSession()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushPendingSession()
        }
    }

    // Error toast — implement per-platform via expect/actual or use a Compose snackbar
    LaunchedEffect(state.error) {
        val message = when (val err = state.error) {
            MohamedLoversError.Connection -> connectionErrorLabel
            is MohamedLoversError.Raw -> err.message
            null -> null
        }
        if (!message.isNullOrBlank()) {
            showPlatformToast(message) // expect fun — see Task 12 Step 2
            viewModel.clearError()
        }
    }

    var archCenter by remember { mutableStateOf<Offset?>(null) }
    var isLit by remember { mutableStateOf(false) }
    var infoSheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isLit) {
        if (isLit) { delay(1600); isLit = false }
    }

    val blockedMessage = when (state.status) {
        MohamedLoversStatus.WaitingNetwork -> waitingNetworkLabel
        MohamedLoversStatus.FirebaseOff -> firebaseOffLabel
        MohamedLoversStatus.Open -> ""
    }
    val tapsEnabled = state.status == MohamedLoversStatus.Open && state.canCount && !state.isLoading

    Box(modifier = Modifier.fillMaxSize()) {
        MohamedLoversSkyBackground()
        MohamedLoversPrayerOverlay(
            archCenter = archCenter,
            enabled = tapsEnabled,
            prayerText = prayerText,
            rewardText = rewardText,
            blockedMessage = blockedMessage,
            onBlessing = { isLit = true; viewModel.onCountClick() },
            onTap = {
                analyticsManager.logAction(
                    "mohamed_lovers_sky_tap",
                    mapOf("status" to state.status.name, "enabled" to tapsEnabled.toString()),
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            MohamedLoversHadithBanner(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
            )
            MohamedLoversArchShrine(
                isLit = isLit,
                onArchCenterPositioned = { archCenter = it },
                modifier = Modifier.align(Alignment.Center),
            )
            MohamedLoversCounter(
                total = state.syncedTotal + state.sessionClicks,
                pending = state.sessionClicks,
                isFridayBonus = state.isFridayBonus,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            )
            Box(modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 36.dp)) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backCd,
                        tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                    )
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 36.dp)) {
                IconButton(onClick = { infoSheetOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = infoCd,
                        tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                    )
                }
            }
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MohamedLoversPalette.GoldHighlight,
                )
            }
        }
        MohamedLoversInfoSheet(
            isOpen = infoSheetOpen,
            state = state,
            onDismiss = { infoSheetOpen = false },
            onCopyWinnerCode = { code ->
                copyToClipboard(code)    // expect fun — see Task 12 Step 2
                showPlatformToast(codeCopiedLabel)
            },
        )
    }
}
```

- [ ] **Step 2: Add `expect fun` declarations for platform-specific screen actions**

Create `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.kt`:

```kotlin
package tools.mo3ta.salo.ui

expect fun showPlatformToast(message: String)
expect fun copyToClipboard(text: String)
```

Create `app/src/androidMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.android.kt`:

```kotlin
package tools.mo3ta.salo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import tools.mo3ta.salo.AndroidAppContext

actual fun showPlatformToast(message: String) {
    Toast.makeText(AndroidAppContext.get(), message, Toast.LENGTH_SHORT).show()
}

actual fun copyToClipboard(text: String) {
    val cm = AndroidAppContext.get()
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("winner_code", text))
}
```

Create `app/src/androidMain/kotlin/tools/mo3ta/salo/AndroidAppContext.kt`:

```kotlin
package tools.mo3ta.salo

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
object AndroidAppContext {
    private lateinit var context: Context
    fun init(context: Context) { this.context = context.applicationContext }
    fun get(): Context = context
}
```

Create `app/src/iosMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.ios.kt`:

```kotlin
package tools.mo3ta.salo.ui

import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard

actual fun showPlatformToast(message: String) {
    val alert = UIAlertController.alertControllerWithTitle(null, message, 0)
    alert.addAction(UIAlertAction.actionWithTitle("OK", UIAlertActionStyleDefault, null))
    UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(alert, true, null)
}

actual fun copyToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}
```

- [ ] **Step 3: Create `App.kt`**

```kotlin
package tools.mo3ta.salo

import androidx.compose.runtime.Composable
import tools.mo3ta.salo.ui.MohamedLoversScreen

@Composable
fun App() {
    MohamedLoversScreen(onBackClick = {})
}
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew :app:compileKotlinAndroid 2>&1 | grep -E "error:" | head -30
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/ \
        app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt \
        app/src/androidMain/kotlin/tools/mo3ta/salo/ui/ \
        app/src/androidMain/kotlin/tools/mo3ta/salo/AndroidAppContext.kt \
        app/src/iosMain/kotlin/tools/mo3ta/salo/ui/
git commit -m "feat: add MohamedLoversScreen + App.kt with expect/actual platform actions"
```

---

## Task 13: Koin Modules + Entry Points

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt`
- Create: `app/src/androidMain/kotlin/tools/mo3ta/salo/di/AndroidModule.kt`
- Create: `app/src/iosMain/kotlin/tools/mo3ta/salo/di/IosModule.kt`
- Rewrite: `app/src/androidMain/kotlin/tools/mo3ta/salo/MainActivity.kt`
- Create: `app/src/iosMain/kotlin/tools/mo3ta/salo/MainViewController.kt`

- [ ] **Step 1: Create `AppModule.kt`**

```kotlin
package tools.mo3ta.salo.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.NoOpAnalyticsManager
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseClient
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.presentation.MohamedLoversViewModel

val appModule = module {
    single { MohamedLoversFirebaseClient() }
    single { MohamedLoversSessionStore(get()) }
    single<AnalyticsManager> { NoOpAnalyticsManager() }
    single { MohamedLoversRepository(get(), get(), get(), get()) }
    viewModel { MohamedLoversViewModel(get()) }
}
```

- [ ] **Step 2: Create `AndroidModule.kt`**

```kotlin
package tools.mo3ta.salo.di

import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tools.mo3ta.salo.data.country.AndroidCountryCodeProvider
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.time.KronosNetworkTimeProvider
import tools.mo3ta.salo.data.time.NetworkTimeProvider

val androidModule = module {
    single<NetworkTimeProvider> { KronosNetworkTimeProvider(androidContext()) }
    single<CountryCodeProvider> { AndroidCountryCodeProvider(androidContext()) }
    single { SharedPreferencesSettings(androidContext().getSharedPreferences("ml_session", android.content.Context.MODE_PRIVATE)) }
}
```

Note: `MohamedLoversSessionStore` in `AppModule` calls `get()` for `Settings` — `AndroidModule` provides `SharedPreferencesSettings` (implements `Settings`). Koin resolves it by type.

- [ ] **Step 3: Create `IosModule.kt`**

```kotlin
package tools.mo3ta.salo.di

import com.russhwolf.settings.NSUserDefaultsSettings
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.country.IosCountryCodeProvider
import tools.mo3ta.salo.data.time.IosNetworkTimeProvider
import tools.mo3ta.salo.data.time.NetworkTimeProvider

val iosModule = module {
    single<NetworkTimeProvider> { IosNetworkTimeProvider() }
    single<CountryCodeProvider> { IosCountryCodeProvider() }
    single { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
}
```

- [ ] **Step 4: Rewrite `MainActivity.kt`**

```kotlin
package tools.mo3ta.salo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import tools.mo3ta.salo.di.AndroidModule
import tools.mo3ta.salo.di.appModule
import tools.mo3ta.salo.di.androidModule

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAppContext.init(this)
        startKoin {
            androidContext(this@MainActivity)
            modules(appModule, androidModule)
        }
        enableEdgeToEdge()
        setContent { App() }
    }
}
```

- [ ] **Step 5: Create `MainViewController.kt`**

```kotlin
package tools.mo3ta.salo

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
import tools.mo3ta.salo.di.appModule
import tools.mo3ta.salo.di.iosModule

fun MainViewController() = ComposeUIViewController(
    configure = {
        startKoin { modules(appModule, iosModule) }
    },
) { App() }
```

- [ ] **Step 6: Full compile check**

```bash
./gradlew :app:compileKotlinAndroid :app:compileKotlinIosSimulatorArm64 2>&1 | grep -E "error:" | head -30
```

Expected: no errors.

- [ ] **Step 7: Run Android app**

```bash
./gradlew :app:installDebug 2>&1 | tail -10
```

Expected: app installs and opens `MohamedLoversScreen`.

- [ ] **Step 8: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/di/ \
        app/src/androidMain/kotlin/tools/mo3ta/salo/di/ \
        app/src/androidMain/kotlin/tools/mo3ta/salo/MainActivity.kt \
        app/src/iosMain/kotlin/tools/mo3ta/salo/di/ \
        app/src/iosMain/kotlin/tools/mo3ta/salo/MainViewController.kt
git commit -m "feat: wire Koin DI modules and platform entry points"
```

---

## Task 14: Remove Old Source Files

**Files to delete** (wrong packages, replaced by commonMain equivalents):

```
app/src/main/java/tools/mo3ta/salo/mohamedlovers/MohamedLoversModels.kt
app/src/main/java/tools/mo3ta/salo/mohamedlovers/MohamedLoversRepository.kt
app/src/main/java/tools/mo3ta/salo/mohamedlovers/MohamedLoversFirebaseClient.kt
app/src/main/java/tools/mo3ta/salo/mohamedlovers/MohamedLoversNetworkTimeProvider.kt
app/src/main/java/tools/mo3ta/salo/mohamedlovers/MohamedLoversSessionStore.kt
app/src/main/java/tools/mo3ta/salo/ui/mohamedlovers/MohamedLoversScreen.kt
app/src/main/java/tools/mo3ta/salo/ui/mohamedlovers/MohamedLoversViewModel.kt
app/src/main/java/tools/mo3ta/salo/ui/mohamedlovers/components/
app/src/main/java/tools/mo3ta/salo/MainActivity.kt  (replaced in androidMain)
app/src/main/java/tools/mo3ta/salo/ui/theme/        (replaced by CMP theme)
```

- [ ] **Step 1: Delete old files**

```bash
rm -rf app/src/main/java/tools/mo3ta/salo/mohamedlovers/
rm -rf app/src/main/java/tools/mo3ta/salo/ui/
rm app/src/main/java/tools/mo3ta/salo/MainActivity.kt
```

- [ ] **Step 2: Verify full build**

```bash
./gradlew :app:compileKotlinAndroid :app:compileKotlinIosSimulatorArm64 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: remove old Android-only source files (replaced by commonMain)"
```

---

## Self-Review Checklist

**Spec coverage:**
- ✅ CMP Android + iOS — Tasks 1, 7, 8, 13
- ✅ Koin DI — Task 13
- ✅ Clean arch, no UseCase layer — Tasks 9, 10
- ✅ No sinkhole — Repository delegates directly; ViewModel calls Repository
- ✅ NetworkTimeProvider interface + platform impls — Tasks 3, 7, 8
- ✅ CountryCodeProvider interface + platform impls — Tasks 3, 7, 8
- ✅ AnalyticsManager no-op via Koin — Tasks 3, 13
- ✅ SessionStore (multiplatform-settings) — Task 5
- ✅ Firebase client (GitLive) — Task 6
- ✅ kotlinx-datetime replacing java.time — Tasks 2, 4, 10
- ✅ UI components migrated — Task 11
- ✅ Screen + App.kt — Task 12
- ✅ Entry points (MainActivity + MainViewController) — Task 13
- ✅ Old files removed — Task 14

**Type consistency:**
- `MohamedLoversCompetitionWindow.networkNow: Instant?` — used consistently in ViewModel (`formatDisplay()`)
- `MohamedLoversCompetitionWindow.roundEnd: Instant?` — used consistently
- `SessionStore` receives `Settings` (interface) — provided as `SharedPreferencesSettings` / `NSUserDefaultsSettings` by platform modules ✅
- `buildCompetitionWindow(Instant)` defined in Task 4, called in Tasks 7 + 8 ✅
