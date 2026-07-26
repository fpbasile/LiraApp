package com.app.lira.data.repository


import com.app.lira.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.HttpRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

object AuthRepository {

    private val client = SupabaseClient.client

    // --- Sign up function ---
    suspend fun signUp(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success("Account created successfully!")
        } catch (e: Exception) {
            Result.failure(Exception(friendlyAuthError(e)))
        }
    }

    // --- Sign in function ---
    suspend fun signIn(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success("Logged in successfully!")
        } catch (e: Exception) {
            Result.failure(Exception(friendlyAuthError(e)))
        }
    }

    // --- Sign out function  ---
    suspend fun signOut(): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            client.auth.signOut()
            Result.success("Signed out successfully!")
        } catch (e: Exception) {
            Result.failure(Exception(friendlyAuthError(e)))
        }
    }

    // --- Change password for the current session ---
    suspend fun updatePassword(newPassword: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            client.auth.updateUser {
                password = newPassword
            }
            Result.success("Password updated successfully!")
        } catch (e: Exception) {
            Result.failure(Exception(friendlyAuthError(e)))
        }
    }

    // --- Change email for the current session ---
    // Supabase sends a confirmation email (to the new address, and usually the old one too)
    // before the change actually takes effect — this call succeeding does not mean the
    // email has changed yet.
    suspend fun updateEmail(newEmail: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            client.auth.updateUser {
                email = newEmail
            }
            Result.success("Confirmation email sent.")
        } catch (e: Exception) {
            Result.failure(Exception(friendlyAuthError(e)))
        }
    }

    // Supabase's raw exception messages include the error code, URL, headers and
    // HTTP method — not fit to show a user. Map known exception types to a short,
    // human-readable sentence instead.
    private fun friendlyAuthError(e: Throwable): String = when (e) {
        is AuthRestException -> e.errorDescription
        is HttpRequestException, is IOException -> "Can't reach the server. Check your internet connection."
        else -> "Something went wrong. Please try again."
    }
}