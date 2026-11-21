package com.app.lira.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Elderly
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Work
import com.app.lira.data.local.DataMode
import com.app.lira.data.model.Recurring
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.TransactionRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// ───────────────────────────── Kinds of stable income ─────────────────────────────

/** A kind of steady money coming in (salary, CAF…): icon + tint, recognised from the entry's name. */
data class IncomeKind(val key: String, val label: String, val icon: ImageVector, val color: Color, val words: List<String>)

object IncomeKinds {
    val Salary = IncomeKind("salary", "Salary", Icons.Outlined.Work, Color(0xFF8FC79C), listOf("salary", "salaire", "paie", "paye", "wage", "job", "travail"))
    val Caf = IncomeKind("caf", "CAF", Icons.Outlined.FamilyRestroom, Color(0xFF7FB3E6), listOf("caf", "allocation", "prime d activite", "prime activite", "rsa"))
    val Apl = IncomeKind("apl", "APL", Icons.Outlined.House, Color(0xFF6AC9CE), listOf("apl", "aide au logement", "logement", "housing", "als", "alf"))
    val Grant = IncomeKind("grant", "Bourse", Icons.Outlined.School, Color(0xFFE9C46A), listOf("bourse", "crous", "grant", "scholarship", "student"))
    val Pension = IncomeKind("pension", "Pension", Icons.Outlined.Elderly, Color(0xFFD48AAD), listOf("pension", "retraite", "retirement"))
    val Freelance = IncomeKind("freelance", "Freelance", Icons.Outlined.Laptop, Color(0xFFB09DE9), listOf("freelance", "client", "facture", "invoice", "auto entrepreneur"))
    val Unemployment = IncomeKind("unemployment", "Chômage", Icons.Outlined.AccountBalance, Color(0xFFCB7A5D), listOf("chomage", "france travail", "pole emploi", "are", "unemployment"))
    val Other = IncomeKind("other", "Other", Icons.Outlined.Payments, Color(0xFF9E9EA4), emptyList())

    /** Order of the quick picks in onboarding and the add sheet. */
    val picks = listOf(Salary, Caf, Apl, Grant, Unemployment, Pension, Freelance, Other)

    fun of(name: String): IncomeKind {
        val n = java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
        val padded = " $n "
        // Longest matching keyword wins: "france travail" (unemployment) beats "travail" (salary).
        return picks.flatMap { k -> (k.words + k.label.lowercase()).map { w -> w to k } }
            .filter { (w, _) -> w.isNotBlank() && padded.contains(" $w ") }
            .maxByOrNull { (w, _) -> w.length }?.second ?: Other
    }
}

// ───────────────────────────── Bank balance (Supabase: bank_balance) ─────────────────────────────

@Serializable
private data class BalanceRow(
    @SerialName("user_id") val userId: String,
    val amount: Double,
    val currency: String,
    @SerialName("set_at") val setAt: String
)

/** What the user said was on their account, and when. Everything logged after moves it. */
data class BalanceAnchor(val amount: Double, val at: Long)

/**
 * The balance anchor lives in Supabase (`bank_balance`, one row per user) and is mirrored on the
 * phone so the Wallet opens instantly and keeps working offline or before the table exists.
 */
object BalanceRepository {
    private val client = SupabaseClient.client

    /** Bumped on every change so screens recompute. */
    var version by mutableStateOf(0)
        private set

    fun cached(): BalanceAnchor? = DataMode.userKey()?.let { u -> AppPrefs.bankBalance(u)?.let { BalanceAnchor(it.first, it.second) } }

    suspend fun load(): BalanceAnchor? = withContext(Dispatchers.IO) {
        val user = DataMode.userKey() ?: return@withContext null
        if (DataMode.isLocal) return@withContext cached()
        val remote = runCatching {
            client.postgrest["bank_balance"].select { filter { eq("user_id", user) }; limit(1) }.decodeList<BalanceRow>().firstOrNull()
        }
        remote.getOrNull()?.let { row ->
            val at = runCatching { java.time.OffsetDateTime.parse(row.setAt).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis())
            val local = AppPrefs.bankBalance(user)
            if (local == null || local.first != row.amount || local.second != at) {
                AppPrefs.setBankBalance(user, row.amount, at)
                version++
            }
            return@withContext BalanceAnchor(row.amount, at)
        }
        // Row missing (or table not created yet): keep whatever this phone knows, and push it up.
        val local = cached()
        if (remote.isSuccess && local != null) runCatching { upsert(user, local) }
        local
    }

    suspend fun set(amount: Double): Result<Unit> = withContext(Dispatchers.IO) {
        val user = DataMode.userKey() ?: return@withContext Result.failure(Exception("You're not signed in."))
        val anchor = BalanceAnchor(amount, System.currentTimeMillis())
        AppPrefs.setBankBalance(user, anchor.amount, anchor.at)
        version++
        if (DataMode.isLocal) return@withContext Result.success(Unit)
        // Saved on the phone either way; the server copy catches up on the next load if this fails.
        runCatching { upsert(user, anchor) }
        Result.success(Unit)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        val user = DataMode.userKey() ?: return@withContext
        AppPrefs.clearBankBalance(user)
        version++
        if (!DataMode.isLocal) runCatching { client.postgrest["bank_balance"].delete { filter { eq("user_id", user) } } }
    }

    private suspend fun upsert(user: String, a: BalanceAnchor) {
        client.postgrest["bank_balance"].upsert(
            BalanceRow(user, a.amount, CurrencyPreference.code, Instant.ofEpochMilli(a.at).toString())
        ) { onConflict = "user_id" }
    }
}

// ───────────────────────────── Maths ─────────────────────────────

data class IncomeDate(val date: LocalDate, val income: Recurring)

data class WalletSnapshot(
    /** Balance now: anchor ± everything logged after it (null when no anchor). */
    val balance: Double?,
    val anchorAt: Long?,
    val incomes: List<Recurring>,
    /** Stable income per month (weekly ones × 52/12…). */
    val monthlyIncome: Double,
    val nextPayday: LocalDate?,
    val nextPaydayItems: List<Recurring>,
    /** Bills (recurring expenses) due after today up to and including the next payday's eve. */
    val billsBeforePayday: Double,
    val daysToPayday: Int?,
    /** Per day you can spend until payday, or null without a balance or a payday. */
    val perDayUntilPayday: Double?,
    /** Balance at the end of this month once remaining incomes and bills land. */
    val endOfMonth: Double?,
    val receivedThisMonth: Double,
    val expectedThisMonth: Double,
    val spentThisMonth: Double,
    /** Previous payday (or start of month) → next payday, for the cycle bar. */
    val cycleStart: LocalDate?,
    val upcoming: List<IncomeDate>
)

object CashFlow {

    fun isIncome(r: Recurring): Boolean = r.type == TransactionRepository.TYPE_RECEIVED

    /** Stable incomes still running (not ended). */
    fun stableIncomes(all: List<Recurring>, today: LocalDate = LocalDate.now()): List<Recurring> =
        all.filter { r ->
            val start = runCatching { LocalDate.parse(r.start_date) }.getOrNull()
            isIncome(r) && (start?.let { RecurrenceMath.finalDate(it, r.interval, r.occurrences) }?.let { end -> !end.isBefore(today) } ?: true)
        }

    fun perMonth(r: Recurring): Double = r.amount * when (r.interval) {
        "daily" -> 365.0 / 12
        "weekly" -> 52.0 / 12
        "yearly" -> 1.0 / 12
        else -> 1.0
    }

    /** All occurrence dates of [r] in [from, to] — includes ones already posted (unlike occurrencesInMonth). */
    fun datesBetween(r: Recurring, from: LocalDate, to: LocalDate): List<LocalDate> {
        val start = runCatching { LocalDate.parse(r.start_date) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<LocalDate>()
        var n = 0L
        while (n < 5000) {
            if (r.occurrences != null && n >= r.occurrences) break
            val d = RecurrenceMath.nth(start, r.interval, n)
            if (d.isAfter(to)) break
            if (!d.isBefore(from)) out += d
            n++
        }
        return out
    }

    private fun nextDate(r: Recurring, after: LocalDate): LocalDate? =
        datesBetween(r, after.plusDays(1), after.plusYears(1)).firstOrNull()

    private fun prevDate(r: Recurring, onOrBefore: LocalDate): LocalDate? =
        datesBetween(r, onOrBefore.minusYears(1), onOrBefore).lastOrNull()

    /** First date after [from] with day-of-month [day] (31 → last day of short months). */
    fun nextDayOfMonth(day: Int, from: LocalDate = LocalDate.now()): LocalDate {
        var ym = YearMonth.from(from)
        repeat(3) {
            val d = ym.atDay(day.coerceAtMost(ym.lengthOfMonth()))
            if (d.isAfter(from)) return d
            ym = ym.plusMonths(1)
        }
        return from.plusMonths(1)
    }

    fun snapshot(
        anchor: BalanceAnchor?,
        recurring: List<Recurring>,
        txns: List<HomeTxn>,
        home: String,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        /** Row amount in [home] (null while a rate is unknown — counted as 0). */
        amountOf: (HomeTxn, String) -> Double?
    ): WalletSnapshot {
        val incomes = stableIncomes(recurring, today).sortedBy { it.name.lowercase() }
        val bills = recurring.filter { !isIncome(it) }

        val balance = anchor?.let { a ->
            val since = LocalDateTime.ofInstant(Instant.ofEpochMilli(a.at), zone)
            a.amount + txns.sumOf { t ->
                val at = t.local(zone) ?: return@sumOf 0.0
                if (!at.isAfter(since)) return@sumOf 0.0
                val v = amountOf(t, home) ?: 0.0
                if (t.isIncome) v else -v
            }
        }

        val next = incomes.mapNotNull { r -> nextDate(r, today)?.let { IncomeDate(it, r) } }.sortedBy { it.date }
        val nextPayday = next.firstOrNull()?.date
        val nextItems = next.filter { it.date == nextPayday }.map { it.income }
        val days = nextPayday?.let { ChronoUnit.DAYS.between(today, it).toInt() }
        val billsBefore = if (nextPayday == null) 0.0 else bills.sumOf { b -> datesBetween(b, today.plusDays(1), nextPayday.minusDays(1)).size * b.amount }
        val perDay = if (balance != null && days != null && days > 0) ((balance - billsBefore) / days).coerceAtLeast(0.0) else null

        val monthEnd = YearMonth.from(today).atEndOfMonth()
        val monthStart = today.withDayOfMonth(1)
        val incomeRest = incomes.sumOf { r -> datesBetween(r, today.plusDays(1), monthEnd).size * r.amount }
        val billsRest = bills.sumOf { b -> datesBetween(b, today.plusDays(1), monthEnd).size * b.amount }
        val endOfMonth = balance?.let { it + incomeRest - billsRest }

        val monthTx = txns.filter { t -> t.local(zone)?.toLocalDate()?.let { !it.isBefore(monthStart) && !it.isAfter(today) } == true }
        val received = monthTx.filter { it.isIncome }.sumOf { amountOf(it, home) ?: 0.0 }
        val spent = monthTx.filter { !it.isIncome }.sumOf { amountOf(it, home) ?: 0.0 }
        val expected = incomes.sumOf { r -> datesBetween(r, monthStart, monthEnd).size * r.amount }

        val cycleStart = nextPayday?.let { np ->
            nextItems.mapNotNull { prevDate(it, today) }.maxOrNull() ?: np.minusMonths(1)
        }

        return WalletSnapshot(
            balance = balance,
            anchorAt = anchor?.at,
            incomes = incomes,
            monthlyIncome = incomes.sumOf { perMonth(it) },
            nextPayday = nextPayday,
            nextPaydayItems = nextItems,
            billsBeforePayday = billsBefore,
            daysToPayday = days,
            perDayUntilPayday = perDay,
            endOfMonth = endOfMonth,
            receivedThisMonth = received,
            expectedThisMonth = expected,
            spentThisMonth = spent,
            cycleStart = cycleStart,
            upcoming = next.take(6)
        )
    }

    /** Rows needed to move the balance since [anchor] (plus this month, for the totals). */
    fun daysNeeded(anchor: BalanceAnchor?, today: LocalDate = LocalDate.now()): Long {
        val sinceMonth = today.dayOfMonth.toLong() + 1
        val sinceAnchor = anchor?.let {
            ChronoUnit.DAYS.between(Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate(), today) + 2
        } ?: 0
        return maxOf(sinceMonth, sinceAnchor, 35)
    }
}
