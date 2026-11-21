package com.app.lira.data.repository

import com.app.lira.data.local.DataMode
import com.app.lira.data.local.LocalDb
import com.app.lira.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

object AccountRepository {

    private val client = SupabaseClient.client

    /**
     * Deletes all of the current user's own data (transactions, recurring, daily limit,
     * profile row) and signs them out.
     *
     * NOTE: this does NOT delete their Supabase Auth credentials (auth.users). Doing that
     * requires the Admin API, which needs a service-role key — that key must never be shipped
     * inside an Android app, since it bypasses RLS and would expose the whole database to
     * anyone who decompiles the APK. Full credential deletion needs a server-side endpoint
     * (e.g. a Supabase Edge Function using the service role) that this app calls instead.
     */
    suspend fun deleteAccountData(): Result<Unit> = withContext(Dispatchers.IO) {
        if (DataMode.isLocal) { LocalDb.clear(); return@withContext Result.success(Unit) }
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            client.postgrest["transactions"].delete { filter { eq("user_id", userId) } }
            client.postgrest["recurring"].delete { filter { eq("user_id", userId) } }
            client.postgrest["daily_limit"].delete { filter { eq("user_id", userId) } }
            runCatching { client.postgrest["bank_balance"].delete { filter { eq("user_id", userId) } } }
            com.app.lira.data.AppPrefs.clearBankBalance(userId)
            client.postgrest["users"].delete { filter { eq("id", userId) } }
            client.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    private fun friendlyDbError(e: Throwable): String = when (e) {
        is PostgrestRestException -> e.error
        is HttpRequestException, is IOException -> "Can't reach the server. Check your internet connection."
        else -> "Something went wrong. Please try again."
    }
}
