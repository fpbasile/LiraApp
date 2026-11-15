package com.app.lira.data.repository

import com.app.lira.data.model.Trip
import com.app.lira.data.local.DataMode
import com.app.lira.data.local.LocalDb
import com.app.lira.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.Serializable

@Serializable
private data class NewTrip(
    val user_id: String,
    val name: String,
    val currencies: List<String>
)

object TripRepository {

    private val client = SupabaseClient.client

    suspend fun getTrips(): Result<List<Trip>> = withContext(Dispatchers.IO) {
        if (DataMode.isLocal) return@withContext Result.success(LocalDb.trips())
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val rows = client.postgrest["trips"].select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
            }.decodeList<Trip>()
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    suspend fun getTrip(id: Int): Result<Trip?> = withContext(Dispatchers.IO) {
        if (DataMode.isLocal) return@withContext Result.success(LocalDb.trip(id))
        return@withContext try {
            val rows = client.postgrest["trips"].select {
                filter { eq("id", id) }
                limit(1)
            }.decodeList<Trip>()
            Result.success(rows.firstOrNull())
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    suspend fun addTrip(name: String, currencies: List<String>): Result<Trip> = withContext(Dispatchers.IO) {
        if (DataMode.isLocal) return@withContext Result.success(LocalDb.addTrip(name, currencies))
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val inserted = client.postgrest["trips"].insert(
                NewTrip(user_id = userId, name = name, currencies = currencies)
            ) { select() }.decodeSingle<Trip>()
            Result.success(inserted)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    suspend fun deleteTrip(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (DataMode.isLocal) return@withContext Result.success(LocalDb.deleteTrip(id))
        return@withContext try {
            client.postgrest["trips"].delete { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    /** Sets [id] as the only active trip (pass null to just clear the current active trip). */
    suspend fun setActiveTrip(id: Int?): Result<Unit> = withContext(Dispatchers.IO) {
        if (DataMode.isLocal) return@withContext Result.success(LocalDb.setActiveTrip(id))
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            client.postgrest["trips"].update({
                set("is_active", false)
            }) {
                filter { eq("user_id", userId) }
            }
            if (id != null) {
                client.postgrest["trips"].update({
                    set("is_active", true)
                }) {
                    filter { eq("id", id) }
                }
            }
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
