package com.app.lira.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Currency(val symbol: String, val label: String) {
    EUR("€", "Euro"),
    USD("$", "US Dollar")
}

/**
 * There's no currency column anywhere in the schema — this is purely a local display
 * preference, so it's stored on-device (SharedPreferences) rather than in Supabase.
 * `current` is Compose-observable: any composable reading it recomposes when it changes.
 */
object CurrencyPreference {
    private const val PREFS_NAME = "lira_prefs"
    private const val KEY_CURRENCY = "currency"

    private var prefs: SharedPreferences? = null

    var current by mutableStateOf(Currency.EUR)
        private set

    fun init(context: Context) {
        val store = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store
        val saved = store.getString(KEY_CURRENCY, Currency.EUR.name)
        current = runCatching { Currency.valueOf(saved ?: Currency.EUR.name) }.getOrDefault(Currency.EUR)
    }

    fun select(currency: Currency) {
        current = currency
        prefs?.edit()?.putString(KEY_CURRENCY, currency.name)?.apply()
    }
}
