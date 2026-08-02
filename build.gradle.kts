// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Additive only — processes app/google-services.json. Supabase stays the backend.
    alias(libs.plugins.google.services) apply false
    // Required by the Crashlytics SDK: injects the build id it reads at startup.
    alias(libs.plugins.firebase.crashlytics) apply false
}