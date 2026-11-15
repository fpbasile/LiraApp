package com.app.lira.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.app.lira.util.Money

/** Legacy two-currency enum, kept for older call sites. Prefer [CurrencyPreference.code]. */
enum class Currency(val symbol: String, val label: String) {
    EUR("€", "Euro"),
    USD("$", "US Dollar")
}

/**
 * The user's home currency (any ISO 4217 code). There's no currency column for home amounts in
 * the schema — this is a local display preference stored on-device, not a conversion.
 * Compose-observable: readers recompose when it changes.
 */
object CurrencyPreference {
    private const val PREFS_NAME = "lira_prefs"
    private const val KEY_CURRENCY = "currency"

    private var prefs: SharedPreferences? = null

    /** Home currency ISO code, e.g. "EUR". */
    var code by mutableStateOf("EUR")
        private set

    val symbol: String get() = Money.symbol(code)

    /** Legacy accessor: EUR/USD enum (anything else maps to EUR). */
    val current: Currency get() = runCatching { Currency.valueOf(code) }.getOrDefault(Currency.EUR)

    fun init(context: Context) {
        val store = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store
        code = store.getString(KEY_CURRENCY, "EUR") ?: "EUR"
    }

    fun select(newCode: String) {
        code = newCode.uppercase()
        prefs?.edit()?.putString(KEY_CURRENCY, code)?.apply()
    }

    fun select(currency: Currency) = select(currency.name)
}
