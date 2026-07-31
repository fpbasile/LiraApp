package com.app.lira.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.app.lira.MainActivity
import com.app.lira.R
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.SpendingLimitRepository
import com.app.lira.data.repository.TransactionRepository
import io.github.jan.supabase.auth.auth
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val WidgetBackground = Color(0xFF15171B)
private val WidgetTextPrimary = Color.White
private val WidgetTextSecondary = Color(0x8AFFFFFF)
private val WidgetTextTertiary = Color(0x5CFFFFFF)
private val WidgetExpenseColor = Color(0xFFEF5350)
private val WidgetIncomeColor = Color(0xFF66BB6A)
private val WidgetProgressTrack = Color(0x1FFFFFFF)
private val WidgetAccentColor = Color(0xFF64B5F6)

private val PeriodPrefKey = stringPreferencesKey("selected_period")
private val TargetPeriodParam = ActionParameters.Key<String>("target_period")

private enum class WidgetPeriod(val tabLabel: String, val headline: String) {
    DAY("D", "Today's spending"),
    WEEK("W", "This week's spending"),
    MONTH("M", "This month's spending");

    companion object {
        fun fromName(name: String?): WidgetPeriod = entries.firstOrNull { it.name == name } ?: DAY
    }
}

private data class PeriodTotals(val spent: Double = 0.0, val received: Double = 0.0, val limit: Double? = null)

private data class WidgetData(
    val isSignedIn: Boolean,
    val dateLabel: String,
    val day: PeriodTotals = PeriodTotals(),
    val week: PeriodTotals = PeriodTotals(),
    val month: PeriodTotals = PeriodTotals()
) {
    fun totalsFor(period: WidgetPeriod): PeriodTotals = when (period) {
        WidgetPeriod.DAY -> day
        WidgetPeriod.WEEK -> week
        WidgetPeriod.MONTH -> month
    }
}

/** Switches the widget's displayed period in place (no app launch) — triggered by tapping D/W/M. */
class SwitchWidgetPeriodAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val target = parameters[TargetPeriodParam] ?: return
        updateAppWidgetState(context, glanceId) { prefs -> prefs[PeriodPrefKey] = target }
        LiraWidget().update(context, glanceId)
    }
}

class LiraWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetRefresher.init(context)
        CurrencyPreference.init(context)
        SupabaseClient.client.auth.awaitInitialization()

        val dateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
        val isSignedIn = SupabaseClient.client.auth.currentUserOrNull() != null
        val data = if (isSignedIn) {
            WidgetData(
                isSignedIn = true,
                dateLabel = dateLabel,
                day = PeriodTotals(
                    spent = TransactionRepository.getTodaySpent().getOrDefault(0.0),
                    received = TransactionRepository.getTodayReceived().getOrDefault(0.0),
                    limit = SpendingLimitRepository.getDailyLimit().getOrNull()
                ),
                week = PeriodTotals(
                    spent = TransactionRepository.getWeekSpent().getOrDefault(0.0),
                    received = TransactionRepository.getWeekReceived().getOrDefault(0.0),
                    limit = SpendingLimitRepository.getLimit(SpendingLimitRepository.PERIOD_WEEKLY).getOrNull()
                ),
                month = PeriodTotals(
                    spent = TransactionRepository.getMonthSpent().getOrDefault(0.0),
                    received = TransactionRepository.getMonthReceived().getOrDefault(0.0),
                    limit = SpendingLimitRepository.getLimit(SpendingLimitRepository.PERIOD_MONTHLY).getOrNull()
                )
            )
        } else {
            WidgetData(isSignedIn = false, dateLabel = dateLabel)
        }
        Log.d("LiraWidget", "provideGlance: $data")

        provideContent {
            LiraWidgetContent(data)
        }
    }
}

@Composable
private fun LiraWidgetContent(data: WidgetData) {
    val context = LocalContext.current
    val symbol = CurrencyPreference.current.symbol
    val selectedPeriod = WidgetPeriod.fromName(currentState(PeriodPrefKey))
    val totals = data.totalsFor(selectedPeriod)
    val limit = totals.limit
    val progress = if (limit != null && limit > 0.0) (totals.spent / limit).toFloat().coerceIn(0f, 1f) else 0f
    val remaining = if (limit != null) limit - totals.spent else null

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity(openAppIntent(context)))
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            // --- Header: Day/Week/Month switcher + quick-scan action ---
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (data.isSignedIn) {
                    Row(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        WidgetPeriod.entries.forEach { period ->
                            PeriodTab(period = period, isSelected = period == selectedPeriod)
                        }
                    }
                } else {
                    Text(
                        text = "LIRA",
                        style = TextStyle(color = ColorProvider(WidgetTextTertiary), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        modifier = GlanceModifier.defaultWeight()
                    )
                }

                Box(
                    modifier = GlanceModifier
                        .size(50.dp)
                        .cornerRadius(25.dp)
                        .background(WidgetAccentColor.copy(alpha = 0.16f))
                        .clickable(actionStartActivity(scanReceiptIntent(context))),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_camera),
                        contentDescription = "Scan receipt",
                        modifier = GlanceModifier.size(24.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(WidgetAccentColor))
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(14.dp))

            if (!data.isSignedIn) {
                Text(
                    text = "Sign in to see your spending",
                    style = TextStyle(color = ColorProvider(WidgetTextSecondary), fontSize = 14.sp)
                )
            } else {
                Text(
                    text = if (selectedPeriod == WidgetPeriod.DAY) data.dateLabel else selectedPeriod.headline,
                    style = TextStyle(color = ColorProvider(WidgetTextSecondary), fontSize = 12.sp)
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = String.format("%s%.2f", symbol, totals.spent),
                    style = TextStyle(color = ColorProvider(WidgetTextPrimary), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                )

                if (totals.received > 0.0) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = String.format("+%s%.2f received", symbol, totals.received),
                        style = TextStyle(color = ColorProvider(WidgetIncomeColor), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    )
                }

                if (limit != null && limit > 0.0 && remaining != null) {
                    Spacer(modifier = GlanceModifier.height(14.dp))

                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${(progress * 100).toInt()}% of ${String.format("%s%.0f", symbol, limit)} limit",
                            style = TextStyle(color = ColorProvider(WidgetTextTertiary), fontSize = 11.sp),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Text(
                            text = if (remaining >= 0)
                                String.format("%s%.2f left", symbol, remaining)
                            else
                                String.format("%s%.2f over", symbol, -remaining),
                            style = TextStyle(
                                color = ColorProvider(if (remaining >= 0) WidgetTextSecondary else WidgetExpenseColor),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = progress,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .cornerRadius(4.dp),
                        color = ColorProvider(WidgetExpenseColor),
                        backgroundColor = ColorProvider(WidgetProgressTrack)
                    )
                } else {
                    // No limit set for this period — nothing meaningful to show below the headline.
                    Spacer(modifier = GlanceModifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun PeriodTab(period: WidgetPeriod, isSelected: Boolean) {
    Box(
        modifier = GlanceModifier
            .size(26.dp)
            .cornerRadius(13.dp)
            .background(if (isSelected) WidgetAccentColor.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(
                actionRunCallback<SwitchWidgetPeriodAction>(
                    actionParametersOf(TargetPeriodParam to period.name)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = period.tabLabel,
            style = TextStyle(
                color = ColorProvider(if (isSelected) WidgetAccentColor else WidgetTextTertiary),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

private fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

private fun scanReceiptIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(MainActivity.EXTRA_SCAN_RECEIPT, true)
        data = Uri.parse("lira://widget/scan")
    }
