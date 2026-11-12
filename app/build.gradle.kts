import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
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
            implementation(libs.gitlive.firebase.database)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.config)
            implementation(libs.multiplatform.settings)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(compose.materialIconsExtended)
            implementation(libs.kermit)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            implementation(project(":dhikr-model"))
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.messaging)
            implementation(libs.gms.code.scanner)
            implementation(libs.billing)
            implementation(libs.install.referrer)
            // Madinah Mushaf drawn as text (about 1.7 MB, offline, no download).
            implementation(libs.mushaf.text)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}

compose.resources {
    packageOfResClass = "tools.mo3ta.salo.generated.resources"
    generateResClass = always
}

// Debug-only override for the Deno experiences API origin. Release builds ignore it
// and read Firebase Remote Config key experiences_api_base_url. See community-api/README.md.
val experiencesApiBaseUrl: String = run {
    val fromProject = (findProperty("experiencesApiBaseUrl") as String?)?.trim()?.trimEnd('/').orEmpty()
    val raw = if (fromProject.isNotEmpty()) {
        fromProject
    } else {
        val localFile = rootProject.file("local.properties")
        if (!localFile.exists()) {
            ""
        } else {
            val properties = Properties()
            localFile.inputStream().use { properties.load(it) }
            properties.getProperty("experiencesApiBaseUrl").orEmpty().trim().trimEnd('/')
        }
    }
    if (raw.isNotEmpty() && !raw.matches(Regex("""https?://[A-Za-z0-9.-]+(?::\d+)?"""))) {
        error(
            "experiencesApiBaseUrl must be an http(s) origin with no path, or unset. " +
                "See community-api/README.md.",
        )
    }
    raw
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
    namespace = "tools.mo3ta.salo"
    buildFeatures { buildConfig = true }
    compileSdk = 36

    defaultConfig {
        applicationId = "tools.mo3ta.salo"
        minSdk = 24
        targetSdk = 36
        versionCode = 182
        versionName = "4.2.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "EXPERIENCES_API_BASE_URL", "\"$experiencesApiBaseUrl\"")
        }
        release {
            buildConfigField("String", "EXPERIENCES_API_BASE_URL", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    dependencies {
        // Remote Config (firebase-config 22, via GitLive) uses java.time.
        coreLibraryDesugaring(libs.desugar.jdk.libs)
        implementation(libs.firebase.appcheck.playintegrity)
        implementation(libs.firebase.appcheck.debug)
        testImplementation(libs.robolectric)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
        androidTestImplementation(libs.androidx.test.core)
        // GitLive's androidTest variant omits Firebase versions and expects this BOM.
        androidTestImplementation(platform("com.google.firebase:firebase-bom:33.2.0"))
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            res.srcDirs("src/main/res")
            assets.srcDirs("src/main/assets")
        }
    }

    lint {
        error += "HardcodedText"
    }
}

// On-demand modules cannot depend on a compileOnly library. This annotation
// jar is pulled in that way by a transitive dependency and is not needed at runtime.
configurations.matching { it.name.contains("compileOnly", ignoreCase = true) }.configureEach {
    exclude(group = "com.google.j2objc", module = "j2objc-annotations")
}
