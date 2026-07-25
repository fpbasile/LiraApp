package com.app.lira.data.remote

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseClient {

    // Link to api key in local.properties
    private val SUPABASE_URL = com.app.lira.BuildConfig.SUPABASE_URL
    private val SUPABASE_KEY = com.app.lira.BuildConfig.SUPABASE_KEY

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Postgrest)
        install(Auth) {

            sessionManager = SettingsSessionManager()
            alwaysAutoRefresh = true
        }
        install(Storage)
        install(Functions)
    }
}
