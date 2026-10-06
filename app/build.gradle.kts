plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Required from Kotlin 2.0 onward: the Compose compiler ships as a Kotlin
    // compiler plugin, so you no longer set kotlinCompilerExtensionVersion.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ss.tvcompose"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ss.tvcompose"
        // Android TV's practical floor. Compose itself needs 21+, tv-material
        // and media3 need 21+; 23 avoids a pile of legacy TLS/codec quirks on
        // ancient TV boxes that nobody ships to any more.
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Kotlin settings live in the Kotlin plugin's own extension rather than in
// `android { kotlinOptions { } }`. Both work on AGP 8.x; this form is the one
// that survives the move to AGP 9, where kotlinOptions is gone.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            // media3's Compose UI helpers (PlayerSurface, presentation state) are
            // still annotated @UnstableApi. Opting in here rather than sprinkling
            // @OptIn on every call site.
            "-opt-in=androidx.media3.common.util.UnstableApi",
            // tv-material marks a few of its APIs experimental for the same reason.
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            // Modifier.focusRestorer() and friends are still gated behind this.
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
        )
    }
}

dependencies {
    // ── AndroidX base ────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ── Compose (versions come from the BOM) ─────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // ── Compose for TV ───────────────────────────────────────────────────────
    // This brings its own MaterialTheme/Surface/Card/Button tuned for a 10-foot
    // UI: focus scale, glow, border and D-pad semantics are built in. We do NOT
    // add androidx.compose.material3 — mixing the two MaterialThemes is the
    // single most common way to end up with unstyled, unfocusable TV widgets.
    implementation(libs.androidx.tv.material)

    // ── Playback ─────────────────────────────────────────────────────────────
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)   // .m3u8
    implementation(libs.androidx.media3.exoplayer.dash)  // .mpd
    implementation(libs.androidx.media3.ui.compose)      // PlayerSurface + state holders
    implementation(libs.androidx.media3.common)

    // ── Images / DI ──────────────────────────────────────────────────────────
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // ── Tooling / test ───────────────────────────────────────────────────────
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
