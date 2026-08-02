package com.app.lira.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formats [value] with a space as the thousands separator instead of a comma (e.g. "89 647.20"),
 * for readability on currencies with large-magnitude rates (like LBP). A fresh [DecimalFormat] is
 * built per call since it isn't thread-safe to share.
 */
fun formatWithSpaces(value: Double, decimals: Int = 2): String {
    val symbols = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = ' ' }
    val pattern = "#,##0" + if (decimals > 0) "." + "0".repeat(decimals) else ""
    return DecimalFormat(pattern, symbols).format(value)
}
