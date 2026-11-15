package com.app.lira.data.local

import android.content.Context
import android.util.Log
import com.app.lira.data.AppPrefs
import com.app.lira.data.model.Recurring
import com.app.lira.data.model.Trip
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.CategoryRow
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Which backend the app talks to. Offline mode keeps everything in [LocalDb] on this phone —
 * no account, nothing backed up.
 */
object DataMode {
    val isLocal: Boolean get() = AppPrefs.offlineMode

    /** Identity of whoever's data is on screen: the Supabase user id, "local" offline, or null when signed out. */
    fun userKey(): String? = if (isLocal) LOCAL_USER else SupabaseClient.client.auth.currentUserOrNull()?.id

    const val LOCAL_USER = "local"
}

/** One transaction as stored offline. Same columns as the `transactions` table. */
@Serializable
data class LocalTxn(
    val id: Int,
    val amount: Double,
    val type: String,
    val date: String,
    val category_id: Int? = null,
    val source: String? = null,
    val description: String? = null,
    val currency: String? = null,
    val trip_id: Int? = null,
    val recurring_id: Int? = null
)

@Serializable
private data class LocalState(
    val nextId: Int = 1,
    val transactions: List<LocalTxn> = emptyList(),
    val categories: List<CategoryRow> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val limits: Map<String, Double> = emptyMap()
)

/**
 * Offline store: the whole dataset lives in memory and is written to one JSON file in app storage
 * after each change (atomically, off the main thread). Small by design — a few thousand rows.
 * Every accessor is synchronized; callers already run on Dispatchers.IO.
 */
object LocalDb {
    private const val TAG = "LocalDb"
    private const val FILE = "lira_offline.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writer = Executors.newSingleThreadExecutor()
    private val lock = Any()

    private var file: File? = null
    private var state = LocalState()
    private var loaded = false

    val DEFAULT_CATEGORIES = listOf("Groceries", "Food", "Coffee", "Transport", "Shopping", "Bills", "Fun", "Health", "Other")

    fun init(context: Context) = synchronized(lock) {
        if (file != null) return
        file = File(context.applicationContext.filesDir, FILE)
        load()
    }

    private fun load() {
        val f = file ?: return
        state = if (f.exists()) {
            runCatching { json.decodeFromString<LocalState>(f.readText()) }
                .onFailure { Log.e(TAG, "Couldn't read offline data", it) }
                .getOrDefault(LocalState())
        } else LocalState()
        loaded = true
    }

    private fun persist() {
        val f = file ?: return
        val snapshot = state
        writer.execute {
            runCatching {
                val tmp = File(f.parentFile, "$FILE.tmp")
                tmp.writeText(json.encodeToString(LocalState.serializer(), snapshot))
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            }.onFailure { Log.e(TAG, "Couldn't save offline data", it) }
        }
    }

    private inline fun <T> mutate(block: () -> T): T = synchronized(lock) { block().also { persist() } }

    private fun newId(): Int { val id = state.nextId; state = state.copy(nextId = id + 1); return id }

    /** Seeds the starter categories the first time offline mode is used. */
    fun ensureSeeded() = mutate {
        if (state.categories.isEmpty()) {
            val cats = DEFAULT_CATEGORIES.map { CategoryRow(newId(), it) }
            state = state.copy(categories = cats)
        }
    }

    /** Wipes everything stored offline. */
    fun clear() = mutate { state = LocalState() }

    // ───────────── transactions ─────────────

    fun transactions(): List<LocalTxn> = synchronized(lock) { state.transactions }

    fun transaction(id: Int): LocalTxn? = synchronized(lock) { state.transactions.firstOrNull { it.id == id } }

    fun insertTransaction(t: LocalTxn): Int = mutate {
        val id = newId()
        state = state.copy(transactions = state.transactions + t.copy(id = id))
        id
    }

    fun insertTransactions(rows: List<LocalTxn>): Int = mutate {
        val withIds = rows.map { it.copy(id = newId()) }
        state = state.copy(transactions = state.transactions + withIds)
        withIds.size
    }

    fun updateTransaction(id: Int, change: (LocalTxn) -> LocalTxn) = mutate {
        state = state.copy(transactions = state.transactions.map { if (it.id == id) change(it) else it })
    }

    fun updateTransactions(where: (LocalTxn) -> Boolean, change: (LocalTxn) -> LocalTxn) = mutate {
        state = state.copy(transactions = state.transactions.map { if (where(it)) change(it) else it })
    }

    fun deleteTransaction(id: Int) = mutate {
        state = state.copy(transactions = state.transactions.filterNot { it.id == id })
    }

    /** Rows whose instant falls in [start, end), newest first. */
    fun transactionsBetween(start: Instant?, end: Instant?): List<LocalTxn> =
        transactions().filter { t ->
            val i = instantOf(t.date) ?: return@filter false
            (start == null || !i.isBefore(start)) && (end == null || i.isBefore(end))
        }.sortedByDescending { instantOf(it.date) }

    // ───────────── categories ─────────────

    fun categories(): List<CategoryRow> = synchronized(lock) { state.categories }

    fun addCategory(name: String): CategoryRow = mutate {
        val row = CategoryRow(newId(), name)
        state = state.copy(categories = state.categories + row)
        row
    }

    fun renameCategory(id: Int, name: String): CategoryRow = mutate {
        state = state.copy(categories = state.categories.map { if (it.id == id) it.copy(name = name) else it })
        state.categories.first { it.id == id }
    }

    fun deleteCategory(id: Int) = mutate {
        state = state.copy(
            categories = state.categories.filterNot { it.id == id },
            transactions = state.transactions.map { if (it.category_id == id) it.copy(category_id = null) else it },
            recurring = state.recurring.map { if (it.category_id == id) it.copy(category_id = null) else it }
        )
    }

    fun categoryName(id: Int?): String? = id?.let { cid -> categories().firstOrNull { it.id == cid }?.name }

    // ───────────── recurring ─────────────

    fun recurring(): List<Recurring> = synchronized(lock) { state.recurring }

    fun addRecurring(r: Recurring): Recurring = mutate {
        val row = r.copy(id = newId(), user_id = DataMode.LOCAL_USER, created_at = OffsetDateTime.now().toString())
        state = state.copy(recurring = state.recurring + row)
        row
    }

    fun updateRecurring(id: Int, change: (Recurring) -> Recurring) = mutate {
        state = state.copy(recurring = state.recurring.map { if (it.id == id) change(it) else it })
    }

    fun updateRecurringWhere(where: (Recurring) -> Boolean, change: (Recurring) -> Recurring) = mutate {
        state = state.copy(recurring = state.recurring.map { if (where(it)) change(it) else it })
    }

    fun deleteRecurring(id: Int) = mutate { state = state.copy(recurring = state.recurring.filterNot { it.id == id }) }

    // ───────────── trips ─────────────

    fun trips(): List<Trip> = synchronized(lock) { state.trips.sortedByDescending { it.created_at } }

    fun trip(id: Int): Trip? = synchronized(lock) { state.trips.firstOrNull { it.id == id } }

    fun addTrip(name: String, currencies: List<String>): Trip = mutate {
        val trip = Trip(id = newId(), user_id = DataMode.LOCAL_USER, name = name, currencies = currencies, created_at = OffsetDateTime.now().toString())
        state = state.copy(trips = state.trips + trip)
        trip
    }

    fun updateTrip(id: Int, change: (Trip) -> Trip) = mutate {
        state = state.copy(trips = state.trips.map { if (it.id == id) change(it) else it })
    }

    fun setActiveTrip(id: Int?) = mutate {
        state = state.copy(trips = state.trips.map { it.copy(is_active = it.id == id) })
    }

    fun deleteTrip(id: Int) = mutate {
        state = state.copy(
            trips = state.trips.filterNot { it.id == id },
            transactions = state.transactions.map { if (it.trip_id == id) it.copy(trip_id = null) else it }
        )
    }

    // ───────────── limits ─────────────

    fun limit(period: String): Double? = synchronized(lock) { state.limits[period] }

    fun setLimit(period: String, amount: Double) = mutate { state = state.copy(limits = state.limits + (period to amount)) }

    // ───────────── dates ─────────────

    fun instantOf(iso: String): Instant? =
        runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
            ?: runCatching { LocalDate.parse(iso.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()
}
