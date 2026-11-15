package com.app.lira.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/**
 * Locale-aware money formatting (Components board · currency formats). Separators and symbol
 * position follow the device locale; decimals follow the currency's minor unit (JPY/KRW have none).
 */
object Money {

    /**
     * Currencies whose unit is worth so little that cents are never used in practice (prices run in
     * thousands or millions, e.g. LBP ≈ 90,000 per USD). They get no decimals and a "000" key.
     */
    private val HIGH_DENOMINATION = setOf(
        "LBP", "IRR", "VND", "IDR", "UZS", "LAK", "GNF", "SLL", "SLE", "COP", "MMK", "KHR", "MGA",
        "UGX", "TZS", "IQD", "SYP", "STD", "STN", "KPW", "SOS", "YER", "BIF", "PYG", "LRD", "MWK",
        "NGN", "AMD", "HUF", "KZT", "CLP", "KRW", "JPY", "ISK", "RWF", "XAF", "XOF", "TMT", "SDG"
    )

    fun isHighDenomination(code: String): Boolean = code.uppercase() in HIGH_DENOMINATION

    /** Decimals shown and typed for [code]: 0 for high-denomination currencies, else ISO minor unit. */
    fun minorDigits(code: String): Int =
        if (isHighDenomination(code)) 0
        else runCatching { Currency.getInstance(code.uppercase()).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)

    fun symbol(code: String, locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(code.uppercase()).getSymbol(locale) }.getOrDefault(code.uppercase())

    /** "€1,234.56". [trimZeros] drops ".00" for whole amounts ("€18"). */
    fun format(amount: Double, code: String, trimZeros: Boolean = false, locale: Locale = Locale.getDefault()): String {
        val fmt = NumberFormat.getCurrencyInstance(locale)
        runCatching { fmt.currency = Currency.getInstance(code.uppercase()) }
        val digits = if (trimZeros && abs(amount - Math.round(amount)) < 0.005) 0 else minorDigits(code)
        fmt.minimumFractionDigits = digits
        fmt.maximumFractionDigits = digits
        return spaceLetterCode(fmt.format(amount))
    }

    /**
     * Compact form for tight spots once numbers get long: "LBP 4.1M", "€86.1K".
     * Below [threshold] it's the normal [format].
     */
    fun formatCompact(amount: Double, code: String, threshold: Double = 1_000_000.0, locale: Locale = Locale.getDefault()): String {
        val a = abs(amount)
        if (a < threshold) return format(amount, code, trimZeros = true, locale = locale)
        val (div, suffix) = when {
            a >= 1e9 -> 1e9 to "B"
            a >= 1e6 -> 1e6 to "M"
            else -> 1e3 to "K"
        }
        val n = java.text.DecimalFormat("0.#", java.text.DecimalFormatSymbols.getInstance(locale)).format(amount / div)
        val sym = symbol(code, locale)
        return if (sym.length > 1 && sym.all { it.isLetter() }) "$sym $n$suffix" else "$sym$n$suffix"
    }

    /** "LBP4,100,000" → "LBP 4,100,000": a letter code glued to digits is hard to read. */
    private fun spaceLetterCode(s: String): String {
        val m = Regex("^(-?)([A-Za-z]{2,})(\\d)").find(s) ?: return s
        return s.replaceRange(m.range, m.groupValues[1] + m.groupValues[2] + " " + m.groupValues[3])
    }

    /** Signed version: "+€12.00" for income, "€3.20" for spending. */
    fun formatSigned(amount: Double, code: String, positive: Boolean): String =
        (if (positive) "+" else "") + format(amount, code)

    /** Decimal separator of the device locale — the keypad relabels "." to "," where needed. */
    fun decimalSeparator(locale: Locale = Locale.getDefault()): Char =
        java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator

    /** Groups the integer part of a raw keypad string, e.g. "1234567.8" → "1,234,567.8". */
    fun groupRaw(raw: String, locale: Locale = Locale.getDefault()): String {
        if (raw.isEmpty()) return "0"
        val symbols = java.text.DecimalFormatSymbols.getInstance(locale)
        val parts = raw.split('.')
        val intPart = parts[0].ifEmpty { "0" }
        val grouped = intPart.reversed().chunked(3).joinToString(symbols.groupingSeparator.toString()).reversed()
        return if (parts.size > 1) grouped + symbols.decimalSeparator + parts[1] else grouped
    }
}
