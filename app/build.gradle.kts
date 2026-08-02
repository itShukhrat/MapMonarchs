import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// Secrets come from local.properties (git-ignored) -> BuildConfig.
// .trim() is deliberate: a stray BOM/whitespace is truthy and silently breaks
// "value ?: fallback" checks at runtime.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
fun secret(key: String): String = (localProps.getProperty(key) ?: "").trim()

android {
    namespace = "com.mapmonarchs"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mapmonarchs"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MAPBOX_PUBLIC_TOKEN", "\"${secret("MAPBOX_PUBLIC_TOKEN")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
    }

    signingConfigs {
        // Release signing comes from OUTSIDE the repo. The keystore path and its
        // passwords live in local.properties (git-ignored) or the CI environment,
        // never here -- an upload key committed to git is an upload key anyone who
        // clones the repo can publish an update with. Absent, the release stays
        // unsigned so a fresh clone still builds; a real release supplies these
        // four values and is signed automatically. See RELEASE.md.
        create("release") {
            val storePath = secret("RELEASE_STORE_FILE")
            if (storePath.isNotEmpty()) {
                storeFile = rootProject.file(storePath)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 stays OFF, and that is a verified-safe choice rather than an
            // oversight. The release variant is otherwise identical to the debug
            // build that is device-verified, so it behaves the same. Turning R8
            // ON is a size optimisation whose keep rules are already written in
            // proguard-rules.pro -- but it must be re-verified against a SIGNED-IN
            // read (kotlinx-serialization resolves serializers reflectively on the
            // network path), and that path is not exercisable while sign-in is
            // gated. Enabling it unverified would ship a risk this project refuses.
            // Native Mapbox libraries dominate the artifact size anyway; the real
            // size lever is the AAB's per-ABI split, not R8.
            optimization {
                enable = false
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed only when a keystore was supplied; otherwise unsigned.
            if (secret("RELEASE_STORE_FILE").isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // android.util.Log and friends are stubs in host unit tests. Return
            // defaults (Log.w -> 0) instead of throwing "not mocked", so pure
            // logic that happens to log -- e.g. H3Boundary's degrade path -- can
            // be verified off-device without pulling in a logging shim.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(libs.mapbox.maps)
    implementation(libs.mapbox.maps.compose)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.play.services.location)

    // Uber H3 (official Java binding). The ONLY place client-side H3 lives, and
    // only to CONVERT a server-issued cell id into its boundary polygon for
    // drawing -- never to decide which cell the player is in (that stays
    // server-authoritative in the Edge Function). Ships android-arm/android-arm64
    // native libs (real devices) and windows/linux/darwin (host unit tests); it
    // does NOT ship android-x86_64, so it cannot load on an x86_64 emulator --
    // H3Boundary degrades to drawing nothing there rather than crashing.
    implementation(libs.h3)

    // Firebase — ADDITIVE ONLY. Supabase remains the backend of record (auth,
    // data, entitlements, server-authoritative capture). These add client-side
    // Analytics and crash reporting. Data collection defaults OFF in the
    // manifest and turns on only with player consent. The Crashlytics Gradle
    // plugin (applied above) is REQUIRED, not optional: it injects the build id
    // the SDK reads at startup, and without it FirebaseInitProvider crashes the
    // app on launch ("Crashlytics build ID is missing") regardless of the
    // collection flag. (It also uploads the R8 mapping when R8 is on; it is off.)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}