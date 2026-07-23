package com.mapmonarchs.data.remote

import com.mapmonarchs.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.realtime.Realtime

/**
 * Single Supabase client for the app.
 *
 * Uses the ANON/publishable key only. The service-role key bypasses RLS and must
 * never exist in this codebase or the APK -- if it did, every anti-cheat gate
 * would be bypassable by anyone who unzipped the app.
 *
 * Values are already .trim()'d at the Gradle boundary (a BOM is truthy and would
 * silently defeat an isBlank()/fallback check).
 */
object SupabaseProvider {

    val client: SupabaseClient by lazy {
        require(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "SUPABASE_URL missing -- set it in local.properties"
        }
        require(BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            "SUPABASE_ANON_KEY missing -- set it in local.properties"
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Functions)
        }
    }
}