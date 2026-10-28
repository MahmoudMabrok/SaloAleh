import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

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
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // mushaf-ui publishes Compose as runtime scope, so our own composables
    // need it on the compile classpath. Versions match the library BOM.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}

// mushaf-ui AARs still ship assets/quran-images. Those pages belong in the
// on-demand :mushaf-assets pack; BundleTool rejects the same path in both.
afterEvaluate {
    tasks.matching { task ->
        val n = task.name
        n.contains("merge", ignoreCase = true) && n.contains("Assets", ignoreCase = true)
    }.configureEach {
        doLast {
            outputs.files.forEach { root ->
                val qi = File(root, "quran-images")
                if (qi.isDirectory) {
                    logger.lifecycle("Stripping ${qi.absolutePath} from :mushaf (owned by :mushaf-assets)")
                    qi.deleteRecursively()
                }
            }
        }
    }
}
