package com.bloomcycle.app.data.supabase

import com.bloomcycle.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseConfig {

    val url: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_KEY.trim()

    val isConfigured: Boolean
        get() = url.startsWith("https://", ignoreCase = true) && anonKey.isNotBlank()

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = anonKey
        ) {
            install(Postgrest)
            install(Auth)
            install(Realtime)
        }
    }
}
