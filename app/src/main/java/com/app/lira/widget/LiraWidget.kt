package com.app.lira.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
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
import com.app.lira.util.Money
import io.github.jan.supabase.auth.auth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// Design tokens (mirrors ui/theme/Color.kt LiraColors — Glance can't read the Compose theme).
private val WidgetTextPrimary = Color(0xFFF5F5F4)
private val WidgetTextSecondary = Color(0xFFA8A8AD)
private val WidgetTextTertiary = Color(0xFF85858B)
private val WidgetLichen = Color(0xFF5D926D) // positive: money left
private val WidgetOverLimit = Color(0xFFFF5A5F) // only when a limit is actually crossed
private val WidgetRingTrack = Color(0x1AFFFFFF) // white 10%
private val WidgetOnPrimary = Color(0xFF0B0B0C)

private val PeriodPrefKey = stringPreferencesKey("selected_period")
private val TargetPeriodParam = ActionParameters.Key<String>("target_period")

private enum class WidgetPeriod(val tabLabel: String, val suffix: String) {
    DAY("Today", "today"),
    WEEK("Week", "this week"),
    MONTH("Month", "this month");

    val next: WidgetPeriod get() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): WidgetPeriod = entries.firstOrNull { it.name == name } ?: DAY
    }
}

private data class PeriodTotals(val spent: Double = 0.0, val limit: Double? = null)

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

/**
 * What the widget says for one period. Positive framing: "€18 left today" in Lichen; red only
 * when the limit is actually crossed, and then always with the word "over".
 */
private data class PeriodView(
    val amount: String,
    val caption: String,
    val detail: String,
    val amountColor: Color,
    val hasLimit: Boolean,
    val progress: Float,
    val isOver: Boolean
)

private fun periodView(period: WidgetPeriod, totals: PeriodTotals, code: String): PeriodView {
    val limit = totals.limit
    if (limit == null || limit <= 0.0) {
        return PeriodView(
            amount = Money.format(totals.spent, code, trimZeros = true),
            caption = "spent ${period.suffix}",
            detail = "No limit set",
            amountColor = WidgetTextPrimary,
            hasLimit = false,
            progress = 0f,
            isOver = false
        )
    }
    val remaining = limit - totals.spent
    val isOver = remaining < -0.005
    val detail = "${Money.format(totals.spent, code, trimZeros = true)} of ${Money.format(limit, code, trimZeros = true)}"
    return if (isOver) {
        PeriodView(
            amount = Money.format(-remaining, code, trimZeros = true),
            caption = "over ${period.suffix}",
            detail = detail,
            amountColor = WidgetOverLimit,
            hasLimit = true,
            progress = 1f,
            isOver = true
        )
    } else {
        val left = remaining.coerceAtLeast(0.0)
        PeriodView(
            amount = Money.format(left, code, trimZeros = true),
            caption = "left ${period.suffix}",
            detail = detail,
            amountColor = if (left > 0.005) WidgetLichen else WidgetTextPrimary,
            hasLimit = true,
            progress = (totals.spent / limit).toFloat().coerceIn(0f, 1f),
            isOver = false
        )
    }
}

/** Switches the widget's displayed period in place (no app launch) — Today/Week/Month toggles and the 2×2 cycle tap. */
class SwitchWidgetPeriodAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val target = parameters[TargetPeriodParam]
        Log.d("LiraWidget", "SwitchWidgetPeriodAction: target=$target")
        if (target == null) return
        updateAppWidgetState(context, glanceId) { prefs -> prefs[PeriodPrefKey] = target }
        LiraWidget().update(context, glanceId)
    }
}

class LiraWidget : GlanceAppWidget() {

    companion object {
        /** 2×1: ring + number (= quick add) and one Scan button. */
        val SIZE_ROW = DpSize(110.dp, 40.dp)
        /** 2×2: ring + number (tap cycles period) above two 48dp icon actions. */
        val SIZE_SQUARE = DpSize(110.dp, 130.dp)
        /** 4×2: period toggles, ring + "€18 left today", Add expense + Scan. */
        val SIZE_MEDIUM = DpSize(250.dp, 170.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SIZE_ROW, SIZE_SQUARE, SIZE_MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetRefresher.init(context)
        CurrencyPreference.init(context)
        com.app.lira.data.AppPrefs.init(context)
        com.app.lira.data.local.LocalDb.init(context)
        SupabaseClient.client.auth.awaitInitialization()

        val dateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM"))
        val isSignedIn = com.app.lira.data.local.DataMode.userKey() != null
        val data = if (isSignedIn) {
            WidgetData(
                isSignedIn = true,
                dateLabel = dateLabel,
                day = PeriodTotals(
                    spent = TransactionRepository.getTodaySpent().getOrDefault(0.0),
                    limit = SpendingLimitRepository.getDailyLimit().getOrNull()
                ),
                week = PeriodTotals(
                    spent = TransactionRepository.getWeekSpent().getOrDefault(0.0),
                    limit = SpendingLimitRepository.getLimit(SpendingLimitRepository.PERIOD_WEEKLY).getOrNull()
                ),
                month = PeriodTotals(
                    spent = TransactionRepository.getMonthSpent().getOrDefault(0.0),
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
    val size = LocalSize.current

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(R.drawable.lira_widget_bg))
            .cornerRadius(28.dp)
            .clickable(actionStartActivity(openAppIntent(context)))
    ) {
        val period = WidgetPeriod.fromName(currentState(PeriodPrefKey))
        val view = periodView(period, data.totalsFor(period), CurrencyPreference.code)
        when {
            !data.isSignedIn -> SignedOutContent()
            size.width >= SIZE_MEDIUM_WIDTH && size.height >= SIZE_MEDIUM_HEIGHT -> MediumContent(data, period, view, showDate = size.width >= 300.dp)
            size.height >= SIZE_SQUARE_HEIGHT -> SquareContent(period, view, compact = size.width < 160.dp)
            else -> RowContent(view, showAdd = size.width >= SIZE_MEDIUM_WIDTH)
        }
    }
}

private val SIZE_MEDIUM_WIDTH = 250.dp
private val SIZE_MEDIUM_HEIGHT = 170.dp
private val SIZE_SQUARE_HEIGHT = 130.dp

// ---------- 4×2 medium ----------

@Composable
private fun MediumContent(data: WidgetData, period: WidgetPeriod, view: PeriodView, showDate: Boolean) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize().padding(start = 14.dp, end = 16.dp, top = 2.dp, bottom = 14.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetPeriod.entries.forEach { p -> PeriodToggle(period = p, isSelected = p == period) }
            Spacer(modifier = GlanceModifier.defaultWeight())
            if (showDate) {
                Text(
                    text = data.dateLabel,
                    style = TextStyle(color = ColorProvider(WidgetTextTertiary), fontSize = 11.sp),
                    maxLines = 1
                )
            }
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(start = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (view.hasLimit) {
                Ring(view, sizeDp = 54, strokeDp = 6f)
                Spacer(modifier = GlanceModifier.width(14.dp))
            }
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    AmountText(view, 30.sp)
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = view.caption,
                        style = TextStyle(color = ColorProvider(WidgetTextSecondary), fontSize = 13.sp),
                        maxLines = 1,
                        modifier = GlanceModifier.padding(bottom = 4.dp)
                    )
                }
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = view.detail,
                    style = TextStyle(color = ColorProvider(WidgetTextTertiary), fontSize = 12.sp),
                    maxLines = 1
                )
            }
        }

        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActionPill(
                label = "Add expense",
                iconRes = R.drawable.ic_widget_add,
                primary = true,
                action = actionStartActivity(quickAddIntent(context)),
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            ActionPill(
                label = "Scan",
                iconRes = R.drawable.ic_widget_scan,
                primary = false,
                action = actionStartActivity(scanReceiptIntent(context)),
                modifier = GlanceModifier.width(104.dp)
            )
        }
    }
}

/** Period toggle: a 48dp-tall tap target around a small chip. */
@Composable
private fun PeriodToggle(period: WidgetPeriod, isSelected: Boolean) {
    Box(
        modifier = GlanceModifier
            .height(48.dp)
            .padding(horizontal = 2.dp)
            .clickable(
                actionRunCallback<SwitchWidgetPeriodAction>(actionParametersOf(TargetPeriodParam to period.name))
            ),
        contentAlignment = Alignment.Center
    ) {
        val chip = GlanceModifier.padding(horizontal = 12.dp, vertical = 6.dp)
        Box(
            modifier = if (isSelected) chip.background(ImageProvider(R.drawable.lira_widget_chip_selected)) else chip,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = period.tabLabel,
                style = TextStyle(
                    color = ColorProvider(if (isSelected) WidgetTextPrimary else WidgetTextTertiary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

// ---------- 2×2 small ----------

@Composable
private fun SquareContent(period: WidgetPeriod, view: PeriodView, compact: Boolean) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
        // Tap the number to cycle Today → Week → Month.
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .clickable(
                    actionRunCallback<SwitchWidgetPeriodAction>(actionParametersOf(TargetPeriodParam to period.next.name))
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (view.hasLimit) {
                Ring(view, sizeDp = if (compact) 36 else 46, strokeDp = if (compact) 4f else 5f)
                Spacer(modifier = GlanceModifier.width(if (compact) 8.dp else 12.dp))
            }
            Column {
                AmountText(view, if (compact) 22.sp else 28.sp)
                Text(
                    text = view.caption,
                    style = TextStyle(color = ColorProvider(WidgetTextSecondary), fontSize = 12.sp),
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            IconPill(
                iconRes = R.drawable.ic_widget_add,
                contentDescription = "Quick add",
                primary = true,
                action = actionStartActivity(quickAddIntent(context)),
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            IconPill(
                iconRes = R.drawable.ic_widget_scan,
                contentDescription = "Scan receipt",
                primary = false,
                action = actionStartActivity(scanReceiptIntent(context)),
                modifier = GlanceModifier.defaultWeight()
            )
        }
    }
}

// ---------- 2×1 small (and 4×1) ----------

@Composable
private fun RowContent(view: PeriodView, showAdd: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Body (ring + number) = quick add.
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .clickable(actionStartActivity(quickAddIntent(context))),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (view.hasLimit) {
                Ring(view, sizeDp = 34, strokeDp = 4f)
                Spacer(modifier = GlanceModifier.width(10.dp))
            }
            Column {
                AmountText(view, 20.sp)
                Text(
                    text = view.caption,
                    style = TextStyle(color = ColorProvider(WidgetTextSecondary), fontSize = 11.sp),
                    maxLines = 1
                )
            }
        }
        if (showAdd) {
            Spacer(modifier = GlanceModifier.width(8.dp))
            IconPill(
                iconRes = R.drawable.ic_widget_add,
                contentDescription = "Quick add",
                primary = true,
                action = actionStartActivity(quickAddIntent(context)),
                modifier = GlanceModifier.width(48.dp)
            )
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        IconPill(
            iconRes = R.drawable.ic_widget_scan,
            contentDescription = "Scan receipt",
            primary = false,
            action = actionStartActivity(scanReceiptIntent(context)),
            modifier = GlanceModifier.width(48.dp)
        )
    }
}

// ---------- Signed out ----------

@Composable
private fun SignedOutContent() {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.lira_mark),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp)
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column {
            Text(
                text = "Lira",
                style = TextStyle(color = ColorProvider(WidgetTextPrimary), fontSize = 15.sp, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
            Text(
                text = "Sign in to see what's left",
                style = TextStyle(color = ColorProvider(WidgetTextSecondary), fontSize = 12.sp),
                maxLines = 2
            )
        }
    }
}

// ---------- Pieces ----------

@Composable
private fun AmountText(view: PeriodView, fontSize: TextUnit) {
    Text(
        text = view.amount,
        style = TextStyle(color = ColorProvider(view.amountColor), fontSize = fontSize, fontWeight = FontWeight.Bold),
        maxLines = 1
    )
}

/** Budget ring: Glance can't draw arcs, so it's rendered to a bitmap on each update. */
@Composable
private fun Ring(view: PeriodView, sizeDp: Int, strokeDp: Float) {
    val context = LocalContext.current
    Image(
        provider = ImageProvider(ringBitmap(context, sizeDp, strokeDp, view.progress, view.isOver)),
        contentDescription = null,
        modifier = GlanceModifier.size(sizeDp.dp)
    )
}

@Composable
private fun ActionPill(label: String, iconRes: Int, primary: Boolean, action: Action, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(ImageProvider(if (primary) R.drawable.lira_widget_pill_primary else R.drawable.lira_widget_pill_secondary))
            .cornerRadius(24.dp)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(provider = ImageProvider(iconRes), contentDescription = null, modifier = GlanceModifier.size(18.dp))
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(if (primary) WidgetOnPrimary else WidgetTextPrimary),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun IconPill(iconRes: Int, contentDescription: String, primary: Boolean, action: Action, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(ImageProvider(if (primary) R.drawable.lira_widget_pill_primary else R.drawable.lira_widget_pill_secondary))
            .cornerRadius(24.dp)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Image(provider = ImageProvider(iconRes), contentDescription = contentDescription, modifier = GlanceModifier.size(22.dp))
    }
}

private fun ringBitmap(context: Context, sizeDp: Int, strokeDp: Float, progress: Float, isOver: Boolean): Bitmap {
    val density = context.resources.displayMetrics.density
    val px = (sizeDp * density).roundToInt().coerceAtLeast(1)
    val stroke = strokeDp * density
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    val inset = stroke / 2f
    val rect = RectF(inset, inset, px - inset, px - inset)
    paint.color = WidgetRingTrack.toArgb()
    canvas.drawOval(rect, paint)
    val sweep = if (isOver) 360f else 360f * progress.coerceIn(0f, 1f)
    if (sweep > 0f) {
        paint.color = (if (isOver) WidgetOverLimit else WidgetLichen).toArgb()
        canvas.drawArc(rect, -90f, sweep, false, paint)
    }
    return bitmap
}

// ---------- Intents ----------

private fun baseIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

private fun openAppIntent(context: Context): Intent = baseIntent(context)

private fun quickAddIntent(context: Context): Intent =
    baseIntent(context).apply {
        putExtra(WidgetActions.EXTRA_QUICK_ADD, true)
        data = Uri.parse("lira://widget/add")
    }

private fun scanReceiptIntent(context: Context): Intent =
    baseIntent(context).apply {
        putExtra(MainActivity.EXTRA_SCAN_RECEIPT, true)
        data = Uri.parse("lira://widget/scan")
    }
