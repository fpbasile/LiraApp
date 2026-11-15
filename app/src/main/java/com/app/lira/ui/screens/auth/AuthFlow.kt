package com.app.lira.ui.screens.auth

import com.app.lira.data.AppPrefs
import com.app.lira.data.CsvImport
import com.app.lira.data.local.LocalDb
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.SpendingLimitRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.navigation.Routes
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

/** Post-auth routing and the one auth call AuthRepository doesn't have (password reset). */
internal object AuthFlow {

    /** Set right before navigating to LOGIN after a log-out, so the auth screen opens on "Log in" instead of the welcome. */
    var startAtLogin: Boolean = false

    /**
     * Where a signed-in user goes. A brand-new account (sign-up) always gets onboarding. On sign-in,
     * a user who already has entries or a limit is an existing user — mark onboarding done and go
     * straight Home; an account with nothing in it yet still gets the two onboarding questions.
     */
    suspend fun routeAfterSignIn(): String {
        if (migrateOfflineIfNeeded()) { AppPrefs.onboardingDone = true; return Routes.HOME }
        RecurringRepository.postDueOccurrences() // same catch-up the cold start does
        if (AppPrefs.onboardingDone) return Routes.HOME
        val hasEntries = TransactionRepository.getAllTransactions(1).getOrNull()?.isNotEmpty() == true
        val hasLimit = !hasEntries && SpendingLimitRepository.getLimit(SpendingLimitRepository.PERIOD_DAILY).getOrNull() != null
        return if (hasEntries || hasLimit) {
            AppPrefs.onboardingDone = true
            Routes.HOME
        } else Routes.ONBOARDING
    }

    /**
     * After leaving offline mode for an account: copies the offline entries (and limits) into the
     * account, then clears the offline store. Returns true when something was moved.
     */
    suspend fun migrateOfflineIfNeeded(): Boolean {
        if (!AppPrefs.migrateOffline) return false
        val home = com.app.lira.data.CurrencyPreference.code
        val count = LocalDb.transactions().size
        if (count > 0) {
            CsvImport.import(com.app.lira.notify.OfflineBackup.localCsv(), home).getOrElse { return false }
        }
        SpendingLimitRepository.PERIODS.forEach { p -> LocalDb.limit(p)?.let { SpendingLimitRepository.setLimit(p, it) } }
        LocalDb.clear()
        AppPrefs.migrateOffline = false
        return count > 0
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            SupabaseClient.client.auth.resetPasswordForEmail(email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    when (e) {
                        is AuthRestException -> e.errorDescription
                        is HttpRequestException, is IOException -> "Can't reach the server. Check your internet connection."
                        else -> "Something went wrong. Please try again."
                    }
                )
            )
        }
    }
}
