package com.app.lira.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.app.lira.data.AppPrefs
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.HomeStats
import com.app.lira.data.HomeStatsRepository
import com.app.lira.data.LiraSettings
import com.app.lira.data.local.DataMode
import com.app.lira.data.local.LocalDb
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.SpendingLimitRepository
import com.app.lira.util.Money
import io.github.jan.supabase.auth.auth
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.IsoFields
import java.util.concurrent.TimeUnit

/**
 * Runs about once an hour. Each notification has a time window and fires at most once per
 * day (or week): evening reminder, limit alert, recurring heads-up, Monday summary. Also writes
 * the offline auto-backup when offline data changed.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        AppPrefs.init(ctx)
        CurrencyPreference.init(ctx)
        LocalDb.init(ctx)
        LiraSettings.init(ctx)

        if (OfflineBackup.isDue()) OfflineBackup.run(ctx)

        if (!LiraSettings.anyNotification || !LiraNotifications.canPost(ctx)) return Result.success()
        if (!DataMode.isLocal) SupabaseClient.client.auth.awaitInitialization()
        if (DataMode.userKey() == null || !AppPrefs.onboardingDone) return Result.success()

        val now = LocalTime.now()
        val today = LocalDate.now()
        val home = CurrencyPreference.code
        val txns = HomeStatsRepository.recent(15).getOrNull() ?: return Result.retry()
        val spentToday = HomeStats.spent(txns, home, today, today.plusDays(1))

        // Evening: nothing logged today.
        val h = LiraSettings.eveningHour
        if (LiraSettings.eveningReminder && now.hour in h until h + 2 && LiraSettings.sentOn("evening") != today.toString()) {
            LiraSettings.markSent("evening", today.toString())
            val loggedToday = txns.any { it.local()?.toLocalDate() == today }
            if (!loggedToday) {
                LiraNotifications.evening(ctx, "Anything to log today?", "Nothing logged yet today. Takes three seconds — tap to add it.")
            }
        }

        // Limit: close to (80%) or over today's limit, once each per day, daytime only.
        if (LiraSettings.limitAlerts && now.hour in 8..22) {
            val limit = SpendingLimitRepository.getDailyLimit().getOrNull()
            if (limit != null && limit > 0) {
                val left = limit - spentToday
                when {
                    left < 0 && LiraSettings.sentOn("limit_over") != today.toString() -> {
                        LiraSettings.markSent("limit_over", today.toString())
                        LiraSettings.markSent("limit_near", today.toString())
                        LiraNotifications.limit(ctx, "Over today's limit", "You're ${Money.format(-left, home)} over today's ${Money.format(limit, home, trimZeros = true)}. Tomorrow starts fresh.")
                    }
                    left >= 0 && spentToday >= limit * 0.8 && LiraSettings.sentOn("limit_near") != today.toString() -> {
                        LiraSettings.markSent("limit_near", today.toString())
                        LiraNotifications.limit(ctx, "${Money.format(left, home)} left today", "You've used ${(spentToday / limit * 100).toInt()}% of today's ${Money.format(limit, home, trimZeros = true)}.")
                    }
                }
            }
        }

        // Recurring: the day before something is logged automatically.
        if (LiraSettings.recurringHeadsUp && now.hour in 9..12 && LiraSettings.sentOn("recurring") != today.toString()) {
            LiraSettings.markSent("recurring", today.toString())
            val tomorrow = today.plusDays(1)
            val due = RecurringRepository.getRecurring().getOrNull().orEmpty()
                .filter { tomorrow in RecurringRepository.occurrencesInMonth(it, YearMonth.from(tomorrow)) }
            if (due.isNotEmpty()) {
                val list = due.joinToString(", ") { "${it.name} ${Money.format(it.amount, home, trimZeros = true)}" }
                val title = if (due.size == 1) "Tomorrow: ${due[0].name}" else "Tomorrow: ${due.size} recurring items"
                LiraNotifications.recurring(ctx, title, "$list — Lira logs ${if (due.size == 1) "it" else "them"} for you.")
            }
        }

        // Monday summary of last week.
        val weekKey = "${today.get(IsoFields.WEEK_BASED_YEAR)}-W${today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
        if (LiraSettings.weeklySummary && today.dayOfWeek == java.time.DayOfWeek.MONDAY && now.hour in 9..12 &&
            LiraSettings.sentOn("weekly") != weekKey
        ) {
            LiraSettings.markSent("weekly", weekKey)
            val start = HomeStats.weekStart(today).minusWeeks(1)
            val last = HomeStats.spent(txns, home, start, start.plusDays(7))
            val before = HomeStats.spent(txns, home, start.minusWeeks(1), start)
            val days = txns.mapNotNull { it.local()?.toLocalDate() }.filter { !it.isBefore(start) && it.isBefore(start.plusDays(7)) }.toSet().size
            val change = if (before > 0) {
                val pct = ((last - before) / before * 100).toInt()
                when {
                    pct <= -5 -> " — ${-pct}% less than the week before"
                    pct >= 5 -> " — $pct% more than the week before"
                    else -> " — about the same as the week before"
                }
            } else ""
            LiraNotifications.weekly(ctx, "Last week: ${Money.format(last, home)}", "Spent ${Money.format(last, home)}$change. Logged on $days of 7 days.")
        }
        return Result.success()
    }

    companion object {
        private const val WORK = "lira-reminders"

        /** Idempotent: keeps the existing schedule if there is one. */
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
