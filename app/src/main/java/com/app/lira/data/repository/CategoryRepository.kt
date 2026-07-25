package com.app.lira.data.repository

import com.app.lira.data.remote.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.Serializable

@Serializable
private data class NewCategoryRequest(val name: String)

// Mirrors the "category" table exactly: it only has id/name, no color column.
@Serializable
data class CategoryRow(val id: Int, val name: String)

object CategoryRepository {

    private val client = SupabaseClient.client

    suspend fun getCategories(): Result<List<CategoryRow>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val rows = client.postgrest["category"]
                .select()
                .decodeList<CategoryRow>()
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    suspend fun addCategory(name: String): Result<CategoryRow> = withContext(Dispatchers.IO) {
        return@withContext try {
            val inserted = client.postgrest["category"]
                .insert(NewCategoryRequest(name = name)) {
                    select()
                }
                .decodeSingle<CategoryRow>()
            Result.success(inserted)
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
