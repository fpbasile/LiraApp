package com.app.lira.data

import com.app.lira.data.local.DataMode
import com.app.lira.data.local.LocalDb
import com.app.lira.data.local.LocalTxn
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.widget.WidgetRefresher
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToLong

data class ImportResult(val added: Int, val duplicates: Int, val unreadable: Int, val newCategories: Int) {
    fun summary(): String = buildString {
        append(if (added == 1) "Imported 1 entry" else "Imported $added entries")
        val extra = listOfNotNull(
            duplicates.takeIf { it > 0 }?.let { "$it already there" },
            unreadable.takeIf { it > 0 }?.let { "$it unreadable" },
            newCategories.takeIf { it > 0 }?.let { if (it == 1) "1 new category" else "$it new categories" }
        )
        if (extra.isNotEmpty()) append(" · ").append(extra.joinToString(" · "))
        append('.')
    }
}

@Serializable
private data class ImportedTxn(
    @SerialName("user_id") val userId: String,
    @SerialName("category_id") val categoryId: Int? = null,
    val amount: Double,
    val type: String,
    val source: String? = null,
    val description: String? = null,
    val date: String,
    val currency: String? = null
)

/**
 * Reads a CSV made by More → Export (date,type,amount,currency,category,note,from) back into the
 * current backend — the account or the offline store. Columns are matched by header name, so
 * reordered or partial files work. Rows already present (same minute, type, amount and note)
 * are skipped, so importing the same file twice is harmless. Missing categories are created.
 */
object CsvImport {

    private data class Parsed(
        val dateIso: String,
        val minute: Long,
        val type: String,
        val amount: Double,
        val currency: String?,
        val category: String?,
        val note: String?,
        val from: String?
    )

    suspend fun import(csv: String, homeCurrency: String): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val table = parseCsv(csv.removePrefix("﻿"))
            require(table.isNotEmpty()) { "That file is empty." }
            val header = table.first().map { it.trim().lowercase() }
            fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
            val iDate = col("date") ?: error("This doesn't look like a Lira export (no date column).")
            val iAmount = col("amount") ?: error("This doesn't look like a Lira export (no amount column).")
            val iType = col("type")
            val iCurrency = col("currency")
            val iCategory = col("category")
            val iNote = col("note", "description")
            val iFrom = col("from", "source")

            var unreadable = 0
            val rows = table.drop(1).filter { r -> r.any { it.isNotBlank() } }.mapNotNull { r ->
                fun at(i: Int?) = i?.let { r.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
                val instant = at(iDate)?.let { parseDate(it) }
                val rawAmount = at(iAmount)?.let { parseAmount(it) }
                if (instant == null || rawAmount == null) { unreadable++; return@mapNotNull null }
                val typeRaw = at(iType)?.lowercase()
                val income = typeRaw == "income" || typeRaw == TransactionRepository.TYPE_RECEIVED
                val currency = at(iCurrency)?.uppercase()?.takeIf { it.length == 3 && !it.equals(homeCurrency, true) }
                Parsed(
                    dateIso = instant.toString(),
                    minute = instant.toEpochSecond() / 60,
                    type = if (income) TransactionRepository.TYPE_RECEIVED else TransactionRepository.TYPE_PAYED,
                    amount = abs(rawAmount),
                    currency = currency,
                    category = at(iCategory),
                    note = at(iNote),
                    from = at(iFrom)
                )
            }

            // Skip what's already stored.
            val existing = TransactionRepository.getAllTransactions(1_000_000).getOrThrow()
                .mapNotNull { e ->
                    val i = parseDate(e.date) ?: return@mapNotNull null
                    key(i.toEpochSecond() / 60, e.type, e.amount, e.description ?: e.source)
                }.toHashSet()
            val fresh = rows.filter { p -> existing.add(key(p.minute, p.type, p.amount, p.note ?: p.from)) }

            // Categories: match by name, create the missing ones.
            val cats = CategoryRepository.getCategories().getOrThrow().toMutableList()
            var created = 0
            val idByName = HashMap<String, Int>()
            cats.forEach { idByName[it.name.trim().lowercase()] = it.id }
            fresh.mapNotNull { it.category?.trim() }.distinctBy { it.lowercase() }.forEach { name ->
                if (idByName[name.lowercase()] == null) {
                    CategoryRepository.addCategory(name).onSuccess { idByName[name.lowercase()] = it.id; created++ }
                }
            }

            if (DataMode.isLocal) {
                LocalDb.insertTransactions(fresh.map { p ->
                    LocalTxn(
                        id = 0, amount = p.amount, type = p.type, date = p.dateIso,
                        category_id = p.category?.let { idByName[it.trim().lowercase()] },
                        source = p.from, description = p.note, currency = p.currency
                    )
                })
            } else {
                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: error("You're not signed in.")
                fresh.chunked(500).forEach { chunk ->
                    SupabaseClient.client.postgrest["transactions"].insert(chunk.map { p ->
                        ImportedTxn(
                            userId = userId,
                            categoryId = p.category?.let { idByName[it.trim().lowercase()] },
                            amount = p.amount, type = p.type, source = p.from, description = p.note,
                            date = p.dateIso, currency = p.currency
                        )
                    })
                }
            }
            if (fresh.isNotEmpty()) WidgetRefresher.refresh()
            ImportResult(fresh.size, rows.size - fresh.size, unreadable, created)
        }.recoverCatching { e ->
            throw Exception(
                when (e) {
                    is IllegalArgumentException, is IllegalStateException -> e.message
                    is java.io.IOException -> "Can't reach the server. Check your internet connection."
                    else -> e.message?.take(160) ?: "Couldn't import that file."
                }
            )
        }
    }

    /** "1234.50", "1,234.50", "12,50" (comma decimal) → number. */
    private fun parseAmount(s: String): Double? {
        val t = s.replace(" ", "").replace(" ", "").filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
        val normalized = if (',' in t && '.' !in t && t.substringAfterLast(',').length != 3) t.replace(',', '.') else t.replace(",", "")
        return normalized.toDoubleOrNull()
    }

    private fun key(minute: Long, type: String, amount: Double, note: String?) =
        "$minute|$type|${(amount * 100).roundToLong()}|${note?.trim()?.lowercase().orEmpty()}"

    private fun parseDate(s: String): OffsetDateTime? {
        val zone = ZoneId.systemDefault()
        return runCatching { OffsetDateTime.parse(s) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s).atZone(zone).toOffsetDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s.replace(' ', 'T')).atZone(zone).toOffsetDateTime() }.getOrNull()
            ?: runCatching { LocalDate.parse(s.take(10)).atStartOfDay(zone).toOffsetDateTime() }.getOrNull()
    }

    /** RFC 4180-ish: quoted fields, doubled quotes, commas and newlines inside quotes, CRLF. */
    internal fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\r' -> Unit
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
