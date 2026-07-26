package com.app.lira.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.HomeDataCache
import com.app.lira.data.model.Recurring
import com.app.lira.data.repository.CategoryColorPalette
import com.app.lira.data.repository.MonthlyTotal
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.TransactionDetail
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.navigation.Routes
import com.app.lira.ui.components.AppHeader
import com.app.lira.ui.components.DonutChart
import com.app.lira.ui.components.EditTransactionDialog
import com.app.lira.ui.components.ExpenseColor
import com.app.lira.ui.components.IncomeColor
import com.app.lira.ui.components.LiraBottomNavBar
import com.app.lira.ui.components.RecurringColor
import com.app.lira.ui.components.TransactionRow
import com.app.lira.ui.components.TrendChart
import com.app.lira.ui.components.localDate
import com.app.lira.ui.theme.*
import kotlin.math.abs
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private enum class CalendarTab(val label: String) {
    CALENDAR("Calendar"),
    STATS("Stats")
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CalendarScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var tab by remember { mutableStateOf(CalendarTab.CALENDAR) }

    var transactions by remember { mutableStateOf<List<TransactionDetail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Independent of `currentMonth` (which month's grid/day-list you're browsing) — this always
    // reflects the real last 6 months, for the Stats trend charts.
    var monthlyTotals by remember { mutableStateOf<List<MonthlyTotal>>(emptyList()) }

    // All recurring entries (not month-scoped) — occurrence dates for the *displayed* month are
    // computed from these below.
    var recurringEntries by remember { mutableStateOf<List<Recurring>>(emptyList()) }

    suspend fun refresh() {
        isLoading = true
        loadError = null
        TransactionRepository.getTransactionsForMonth(currentMonth)
            .onSuccess { transactions = it }
            .onFailure { loadError = it.message }
        TransactionRepository.getMonthlyTotals(monthsBack = 6).onSuccess { monthlyTotals = it }
        RecurringRepository.getRecurring().onSuccess { recurringEntries = it }
        isLoading = false
    }

    LaunchedEffect(currentMonth) {
        // Default to today's date when viewing the current month, so its transactions show
        // immediately instead of requiring an extra tap. Other months start unselected since
        // "today" isn't a meaningful default there.
        selectedDate = if (currentMonth == YearMonth.now()) LocalDate.now() else null
        refresh()
    }

    // Refresh whenever this tab regains focus (e.g. after logging a transaction on Home) —
    // popping back to an existing entry doesn't recompose it, so LaunchedEffect(currentMonth)
    // alone would only ever fetch once per month.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { refresh() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val dailySpent = remember(transactions) {
        transactions
            .filter { it.type == TransactionRepository.TYPE_PAYED }
            .mapNotNull { txn -> txn.localDate()?.let { it to txn.amount } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
    }

    val dailyReceived = remember(transactions) {
        transactions
            .filter { it.type == TransactionRepository.TYPE_RECEIVED }
            .mapNotNull { txn -> txn.localDate()?.let { it to txn.amount } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
    }

    val selectedDayTransactions = remember(transactions, selectedDate) {
        selectedDate?.let { date -> transactions.filter { it.localDate() == date } } ?: emptyList()
    }

    val recurringDueDates = remember(recurringEntries, currentMonth) {
        val map = mutableMapOf<LocalDate, MutableList<Recurring>>()
        recurringEntries.forEach { entry ->
            RecurringRepository.occurrencesInMonth(entry, currentMonth).forEach { date ->
                map.getOrPut(date) { mutableListOf() }.add(entry)
            }
        }
        map
    }

    // Days that already have a posted recurring-sourced transaction — kept separate from
    // recurringDueDates (which only covers not-yet-posted occurrences) so the day cell can still
    // show the blue "recurring" dot after the occurrence has posted, alongside the red spend dot.
    val recurringTransactionDates = remember(transactions) {
        transactions.filter { it.recurring_id != null }.mapNotNull { it.localDate() }.toSet()
    }

    val selectedDayRecurring = remember(recurringDueDates, selectedDate) {
        selectedDate?.let { recurringDueDates[it] } ?: emptyList()
    }

    var editingTransaction by remember { mutableStateOf<TransactionDetail?>(null) }
    var isSavingEdit by remember { mutableStateOf(false) }
    var isDeletingEdit by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }

    editingTransaction?.let { txn ->
        EditTransactionDialog(
            transaction = txn,
            categories = HomeDataCache.categories,
            isSaving = isSavingEdit,
            isDeleting = isDeletingEdit,
            errorMessage = editError,
            onDismiss = { editingTransaction = null; editError = null },
            onSave = { amount, categoryId, source, description ->
                scope.launch {
                    isSavingEdit = true
                    editError = null
                    val result = TransactionRepository.updateTransaction(txn.id, amount, categoryId, source, description)
                    isSavingEdit = false
                    result.onSuccess {
                        editingTransaction = null
                        refresh()
                    }.onFailure { e ->
                        editError = e.message ?: "Couldn't save"
                    }
                }
            },
            onDeleteClick = {
                scope.launch {
                    isDeletingEdit = true
                    editError = null
                    val result = TransactionRepository.deleteTransaction(txn.id)
                    isDeletingEdit = false
                    result.onSuccess {
                        editingTransaction = null
                        refresh()
                    }.onFailure { e ->
                        editError = e.message ?: "Couldn't delete"
                    }
                }
            }
        )
    }

    Scaffold(
        containerColor = LiraBackground,
        bottomBar = {
            LiraBottomNavBar(
                currentRoute = Routes.CALENDAR,
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 15.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AppHeader(onSettingsClick = { navController.navigate(Routes.SETTINGS) })

            Spacer(Modifier.height(8.dp))

            MonthNavigator(
                yearMonth = currentMonth,
                onPrevious = { currentMonth = currentMonth.minusMonths(1) },
                onNext = { currentMonth = currentMonth.plusMonths(1) }
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CalendarTab.entries.forEach { option ->
                    CalendarTabChip(
                        label = option.label,
                        isSelected = tab == option,
                        onClick = { tab = option }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            when {
                isLoading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiraTextPrimary)
                }
                loadError != null -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(loadError ?: "", color = ExpenseColor, fontSize = 14.sp)
                }
                tab == CalendarTab.CALENDAR -> {
                    CalendarGrid(
                        yearMonth = currentMonth,
                        dailySpent = dailySpent,
                        dailyReceived = dailyReceived,
                        recurringDueDates = recurringDueDates,
                        recurringTransactionDates = recurringTransactionDates,
                        selectedDate = selectedDate,
                        onSelectDate = { date -> selectedDate = if (selectedDate == date) null else date }
                    )

                    selectedDate?.let { date ->
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = LiraBorder)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            color = LiraTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(14.dp))

                        if (selectedDayRecurring.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                selectedDayRecurring.forEach { entry -> RecurringDueRow(entry, date) }
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        if (selectedDayTransactions.isEmpty()) {
                            if (selectedDayRecurring.isEmpty()) {
                                Text("No transactions this day", color = LiraTextSecondary, fontSize = 14.sp)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                selectedDayTransactions.forEach { txn ->
                                    TransactionRow(txn, onClick = { editingTransaction = txn })
                                }
                            }
                        }
                    }
                }
                tab == CalendarTab.STATS -> {
                    StatsView(
                        monthlyTotals = monthlyTotals,
                        categoryTransactions = transactions,
                        yearMonth = currentMonth
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MonthNavigator(yearMonth: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "‹",
            color = LiraTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onPrevious() }.padding(8.dp)
        )
        Text(
            text = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            color = LiraTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "›",
            color = LiraTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onNext() }.padding(8.dp)
        )
    }
}

@Composable
private fun CalendarTabChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color.White else LiraBorder.copy(alpha = 0.4f))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else LiraTextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    dailySpent: Map<LocalDate, Double>,
    dailyReceived: Map<LocalDate, Double>,
    recurringDueDates: Map<LocalDate, List<Recurring>>,
    recurringTransactionDates: Set<LocalDate>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }
    val firstOfMonth = yearMonth.atDay(1)
    // Monday-start grid, matching the week convention used elsewhere in the app.
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
    val daysInMonth = yearMonth.lengthOfMonth()

    val cells: List<LocalDate?> = buildList {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..daysInMonth) add(yearMonth.atDay(day))
        while (size % 7 != 0) add(null)
    }
    val rows = cells.chunked(7)

    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Text(
                    text = label,
                    color = LiraTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { date ->
                    DayCell(
                        date = date,
                        isToday = date == today,
                        isSelected = date != null && date == selectedDate,
                        amountSpent = date?.let { dailySpent[it] },
                        amountReceived = date?.let { dailyReceived[it] },
                        hasRecurring = date != null &&
                            (recurringDueDates.containsKey(date) || recurringTransactionDates.contains(date)),
                        onClick = { date?.let { onSelectDate(it) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isToday: Boolean,
    isSelected: Boolean,
    amountSpent: Double?,
    amountReceived: Double?,
    hasRecurring: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected -> Color.White
                    isToday -> LiraBorder.copy(alpha = 0.6f)
                    else -> Color.Transparent
                }
            )
            .then(if (date != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = date.dayOfMonth.toString(),
                    color = if (isSelected) Color.Black else LiraTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                )
                val hasSpent = amountSpent != null && amountSpent > 0
                val hasReceived = amountReceived != null && amountReceived > 0
                if (hasSpent || hasReceived || hasRecurring) {
                    Spacer(Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (hasSpent) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(LiraProgressBarIndicator)
                            )
                        }
                        if (hasReceived) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(IncomeColor)
                            )
                        }
                        if (hasRecurring) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(RecurringColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatRecurringDate(isoDate: String): String = try {
    LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
} catch (e: Exception) {
    isoDate
}

@Composable
private fun RecurringDueRow(entry: Recurring, date: LocalDate) {
    val isReceived = entry.type == TransactionRepository.TYPE_RECEIVED

    val rangeLabel = remember(entry, date) {
        val startLabel = formatRecurringDate(entry.start_date)
        val endDate = RecurringRepository.finalOccurrenceDate(entry)
        if (endDate != null) {
            val index = RecurringRepository.occurrenceIndex(entry, date)
            val ordinal = if (index != null) "$index of ${entry.occurrences} · " else ""
            "${ordinal}$startLabel → ${endDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        } else {
            "Started $startLabel · ongoing"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RecurringColor.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(RecurringColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "↻", color = RecurringColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(rangeLabel, color = LiraTextSecondary, fontSize = 11.sp)
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = String.format(if (isReceived) "+%s%.2f" else "-%s%.2f", CurrencyPreference.current.symbol, entry.amount),
            color = if (isReceived) IncomeColor else ExpenseColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatsView(
    monthlyTotals: List<MonthlyTotal>,
    categoryTransactions: List<TransactionDetail>,
    yearMonth: YearMonth
) {
    val symbol = CurrencyPreference.current.symbol
    val monthLabel = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    val payed = categoryTransactions.filter { it.type == TransactionRepository.TYPE_PAYED }
    val byCategory = payed
        .groupBy { it.category?.name ?: "Uncategorized" }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    val daysElapsed = if (yearMonth == YearMonth.now()) LocalDate.now().dayOfMonth else yearMonth.lengthOfMonth()
    val avgPerDay = if (daysElapsed > 0) payed.sumOf { it.amount } / daysElapsed else 0.0

    // Monday-first, matching the calendar grid's week convention.
    val weekdayTotals = DayOfWeek.entries.map { dow ->
        payed.filter { it.localDate()?.dayOfWeek == dow }.sumOf { it.amount }
    }

    Column {
        QuickStatsRow(
            avgPerDay = avgPerDay,
            transactionCount = payed.size,
            topCategory = byCategory.firstOrNull()?.first,
            symbol = symbol
        )

        Spacer(Modifier.height(28.dp))

        if (monthlyTotals.size >= 2) {
            val monthLabels = monthlyTotals.map { it.yearMonth.format(DateTimeFormatter.ofPattern("MMM")) }
            val current = monthlyTotals.last()
            val previous = monthlyTotals[monthlyTotals.size - 2]

            TrendCard(
                title = "Monthly Spending",
                subtitle = "Last ${monthlyTotals.size} Months",
                headline = String.format("%s%.2f", symbol, current.spent),
                deltaPercent = percentChange(previous.spent, current.spent),
                higherIsGood = false, // spending going up is bad, not good
                values = monthlyTotals.map { it.spent.toFloat() },
                labels = monthLabels,
                lineColor = ExpenseColor
            )

            Spacer(Modifier.height(24.dp))

            val currentNet = current.received - current.spent
            val previousNet = previous.received - previous.spent

            TrendCard(
                title = "Net Balance",
                subtitle = "Income minus Expenses",
                headline = String.format("%s%.2f", symbol, currentNet),
                deltaPercent = percentChange(previousNet, currentNet),
                higherIsGood = true,
                values = monthlyTotals.map { (it.received - it.spent).toFloat() },
                labels = monthLabels,
                lineColor = IncomeColor
            )

            Spacer(Modifier.height(28.dp))
        }

        Text("Spending by Category", color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, LiraBorder, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text(monthLabel, color = LiraTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            if (byCategory.isEmpty()) {
                Text("No spending this month", color = LiraTextSecondary, fontSize = 14.sp)
            } else {
                val slices = byCategory.mapIndexed { index, (_, amount) ->
                    Color(CategoryColorPalette[index % CategoryColorPalette.size]) to amount.toFloat()
                }
                val total = byCategory.sumOf { it.second }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        slices = slices,
                        modifier = Modifier.size(110.dp)
                    )
                    Spacer(Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        byCategory.take(5).forEachIndexed { index, (name, amount) ->
                            CategoryLegendRow(
                                color = Color(CategoryColorPalette[index % CategoryColorPalette.size]),
                                name = name,
                                percent = (amount / total * 100).toInt()
                            )
                        }
                        if (byCategory.size > 5) {
                            Text("+${byCategory.size - 5} more", color = LiraTextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = LiraBorder)
                Spacer(Modifier.height(20.dp))

                val maxAmount = byCategory.maxOf { it.second }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    byCategory.forEach { (name, amount) ->
                        CategoryBarRow(
                            name = name,
                            amount = amount,
                            fraction = (amount / maxAmount).toFloat(),
                            symbol = symbol
                        )
                    }
                }
            }
        }

        if (byCategory.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))

            Text("Spending by Day of Week", color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, LiraBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Text(monthLabel, color = LiraTextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                WeekdayBarChart(amounts = weekdayTotals)
            }
        }
    }
}

@Composable
private fun QuickStatsRow(avgPerDay: Double, transactionCount: Int, topCategory: String?, symbol: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Avg / Day", String.format("%s%.2f", symbol, avgPerDay), Modifier.weight(1f))
        StatTile("Transactions", transactionCount.toString(), Modifier.weight(1f))
        StatTile("Top Category", topCategory ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(1.dp, LiraBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 8.dp)
    ) {
        Text(
            label,
            color = LiraTextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = LiraTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CategoryLegendRow(color: Color, name: String, percent: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(name, color = LiraTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("$percent%", color = LiraTextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun WeekdayBarChart(amounts: List<Double>) {
    val maxAmount = (amounts.maxOrNull() ?: 0.0).takeIf { it > 0.0 } ?: 1.0
    val barAreaHeight = 90.dp
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barAreaHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            amounts.forEach { amount ->
                val fraction = (amount / maxAmount).toFloat().coerceIn(0.03f, 1f)
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            LiraProgressBarIndicator.copy(alpha = if (amount == amounts.max()) 1f else 0.5f)
                        )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEach { label ->
                Text(label, color = LiraTextSecondary, fontSize = 11.sp)
            }
        }
    }
}

/** Percent change from [previous] to [current], or null if [previous] is zero (undefined %). */
private fun percentChange(previous: Double, current: Double): Double? =
    if (previous == 0.0) null else (current - previous) / abs(previous) * 100

@Composable
private fun TrendCard(
    title: String,
    subtitle: String,
    headline: String,
    deltaPercent: Double?,
    higherIsGood: Boolean,
    values: List<Float>,
    labels: List<String>,
    lineColor: Color
) {
    Column {
        Text(title, color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, LiraBorder, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text(subtitle, color = LiraTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(headline, color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                deltaPercent?.let { delta ->
                    Spacer(Modifier.width(8.dp))
                    val improved = if (higherIsGood) delta >= 0 else delta <= 0
                    Text(
                        text = "${if (delta >= 0) "+" else ""}${"%.0f".format(delta)}%",
                        color = if (improved) IncomeColor else ExpenseColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            TrendChart(
                values = values,
                lineColor = lineColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            )

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEach { label ->
                    Text(label, color = LiraTextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun CategoryBarRow(name: String, amount: Double, fraction: Float, symbol: String) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = LiraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                text = String.format("%s%.2f", symbol, amount),
                color = LiraTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(LiraBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(LiraProgressBarIndicator)
            )
        }
    }
}
