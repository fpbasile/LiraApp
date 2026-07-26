package com.app.lira.data.repository

import com.app.lira.data.remote.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class ParseReceiptRequest(
    val image: String,
    val mimeType: String,
    val categories: List<String>
)

@Serializable
data class ReceiptResult(
    val merchant: String? = null,
    val amount: Double? = null,
    val date: String? = null,
    val suggestedCategory: String? = null,
    val description: String? = null
)

@Serializable
private data class EdgeFunctionErrorBody(val error: String? = null)

object ReceiptRepository {

    private val client = SupabaseClient.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun parseReceipt(
        imageBase64: String,
        mimeType: String,
        categoryNames: List<String>
    ): Result<ReceiptResult> = withContext(Dispatchers.IO) {
        return@withContext try {
            val requestBody = json.encodeToString(
                ParseReceiptRequest(image = imageBase64, mimeType = mimeType, categories = categoryNames)
            )
            // Supabase invokes functions by their URL slug, not their dashboard display name —
            // this project's "parse-receipt" function is actually deployed under "rapid-endpoint".
            val response = client.functions.invoke("rapid-endpoint") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(requestBody)
            }
            Result.success(json.decodeFromString<ReceiptResult>(response.bodyAsText()))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    // The SDK wraps the raw Edge Function response body (our {"error": "..."} JSON) as-is into
    // RestException.error on any non-2xx status — dig the actual message back out of it. If the
    // body isn't our JSON shape (e.g. a platform-level rejection before our code even ran), fall
    // back to showing the raw body instead of a canned message that hides what actually happened.
    private fun friendlyError(e: Throwable): String = when (e) {
        is RestException -> runCatching { json.decodeFromString<EdgeFunctionErrorBody>(e.error).error }
            .getOrNull() ?: e.error.take(200).ifBlank { "Couldn't read the receipt." }
        is HttpRequestException, is IOException -> "Can't reach the server. Check your internet connection."
        else -> e.message?.take(200) ?: "Something went wrong reading the receipt."
    }
}
