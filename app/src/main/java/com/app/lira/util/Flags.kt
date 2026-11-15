package com.app.lira.util

import java.util.Locale

/**
 * Flag emoji for a currency, from the country that issues it. ISO 4217 codes start with the
 * ISO 3166 country code (LBP → LB, JPY → JP), so that covers almost all of them; shared
 * currencies get the obvious flag (EUR → EU, XOF → Senegal…). Metals, crypto and fund codes
 * get none — callers fall back to the symbol.
 */
object Flags {
    private val countries: Set<String> by lazy { Locale.getISOCountries().toSet() }

    private val special = mapOf(
        "EUR" to "EU", "XAF" to "CM", "XOF" to "SN", "XPF" to "PF", "XCD" to "AG",
        "ANG" to "CW", "XCG" to "CW", "USN" to "US", "CHE" to "CH", "CHW" to "CH"
    )
    private val none = setOf("XAU", "XAG", "XPT", "XPD", "XDR", "XTS", "XXX", "XBA", "XBB", "XBC", "XBD", "XSU", "XUA", "BTC", "ETH")

    /** Two-letter region for [code] (EU allowed), or null. */
    fun region(code: String): String? {
        val c = code.uppercase()
        if (c.length != 3 || c in none) return null
        special[c]?.let { return it }
        val cc = c.take(2)
        return cc.takeIf { it in countries }
    }

    /** "🇯🇵" for "JPY", null when there's no sensible flag. */
    fun forCurrency(code: String): String? = region(code)?.let { emoji(it) }

    /** Regional-indicator pair for a two-letter region code. */
    fun emoji(region: String): String {
        val r = region.uppercase()
        val base = 0x1F1E6 - 'A'.code
        return String(Character.toChars(base + r[0].code)) + String(Character.toChars(base + r[1].code))
    }
}
