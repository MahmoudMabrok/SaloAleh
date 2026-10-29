import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.dynamic-feature")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "tools.mo3ta.salo.mushaf"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

configurations.matching { it.name.contains("compileOnly", ignoreCase = true) }.configureEach {
    exclude(group = "com.google.j2objc", module = "j2objc-annotations")
}

dependencies {
    implementation(project(":app"))
    implementation(libs.mushaf.imad.ui)
    implementation(libs.feature.delivery)
    implementation(libs.asset.delivery)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // mushaf-ui publishes Compose as runtime scope, so our own composables
    // need it on the compile classpath. Versions match the library BOM.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation(libs.junit)
}

// NOTE: since mushaf-ui 0.4.0 the page line PNGs are not in the AAR. They ship in
// the on-demand :mushaf-assets pack (app.assetPacks). MushafPackController
// downloads it and sets MushafLineImages.source = FileLineImageSource(pack dir).
// Do not add the mushaf-images artifact here: it would put the images back into
// this module and duplicate asset paths with the pack.

tasks.withType<Test>().configureEach {
    systemProperty(
        "mushaf.assets.dir",
        rootProject.file("mushaf-assets/src/main/assets/quran-images").absolutePath,
    )
    systemProperty(
        "mushaf.assets.gradle",
        rootProject.file("mushaf-assets/build.gradle.kts").absolutePath,
    )
}
