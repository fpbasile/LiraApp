package com.app.lira.data.repository

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ExchangeRateResult(
    val amount: Double,
    val base: String,
    val date: String,
    val rates: Map<String, Double>
)

/**
 * Live currency conversion via the free, keyless currency-api project
 * (https://github.com/fawazahmed0/currency-api), served off a CDN with no request limits.
 * ~380 currencies, including regional ones ECB-reference-rate providers like Frankfurter don't
 * track (e.g. LBP). Independent of [com.app.lira.data.CurrencyPreference], which is just the
 * on-device display symbol for logged transactions, not a real conversion.
 */
object CurrencyConverterRepository {

    private const val BASE_URL = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1"

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /** Currency code -> display name (e.g. "USD" -> "US Dollar"). */
    suspend fun getSupportedCurrencies(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val raw: Map<String, String> = client.get("$BASE_URL/currencies.json").body()
            Result.success(raw.mapKeys { it.key.uppercase() })
        } catch (e: Exception) {
            Log.e("CurrencyConverter", "getSupportedCurrencies failed", e)
            Result.failure(Exception("Couldn't load currencies (${e.javaClass.simpleName}: ${e.message})."))
        }
    }

    /** Converts [amount] from [from] to [to]. */
    suspend fun convert(amount: Double, from: String, to: String): Result<ExchangeRateResult> = withContext(Dispatchers.IO) {
        try {
            val fromLower = from.lowercase()
            val toLower = to.lowercase()

            val response = client.get("$BASE_URL/currencies/$fromLower.json").body<JsonObject>()
            val date = response["date"]?.jsonPrimitive?.content ?: ""
            val ratesForBase = response[fromLower] as? JsonObject
                ?: return@withContext Result.failure(Exception("No rates found for $from"))
            val rate = ratesForBase[toLower]?.jsonPrimitive?.doubleOrNull
                ?: return@withContext Result.failure(Exception("No rate found for $to"))

            Result.success(
                ExchangeRateResult(
                    amount = amount,
                    base = from.uppercase(),
                    date = date,
                    rates = mapOf(to.uppercase() to amount * rate)
                )
            )
        } catch (e: Exception) {
            Log.e("CurrencyConverter", "convert failed", e)
            Result.failure(Exception("Couldn't fetch the exchange rate (${e.javaClass.simpleName}: ${e.message})."))
        }
    }
}
