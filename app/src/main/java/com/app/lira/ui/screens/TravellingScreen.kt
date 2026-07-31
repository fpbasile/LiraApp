package com.app.lira.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.lira.R
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.HomeDataCache
import com.app.lira.data.model.Category
import com.app.lira.data.model.Trip
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.CurrencyConverterRepository
import com.app.lira.data.repository.ExchangeRateResult
import com.app.lira.data.repository.TransactionDetail
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.data.repository.TripRepository
import com.app.lira.data.repository.toUiCategories
import com.app.lira.navigation.Routes
import com.app.lira.ui.components.AppHeader
import com.app.lira.ui.components.ConfirmationDialog
import com.app.lira.ui.components.ExpenseColor
import com.app.lira.ui.components.IncomeColor
import com.app.lira.ui.components.LiraBottomNavBar
import com.app.lira.ui.components.LiraTextField
import com.app.lira.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val ErrorColor = Color(0xFFE53935)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TravellingScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var isLoadingTrips by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var showAddTripDialog by remember { mutableStateOf(false) }
    var isSavingTrip by remember { mutableStateOf(false) }
    var addTripError by remember { mutableStateOf<String?>(null) }

    var tripToDelete by remember { mutableStateOf<Trip?>(null) }
    var isDeletingTrip by remember { mutableStateOf(false) }
    var deleteTripError by remember { mutableStateOf<String?>(null) }

    suspend fun refreshTrips() {
        TripRepository.getTrips().onSuccess { trips = it }.onFailure { e -> loadError = e.message }
    }

    LaunchedEffect(Unit) {
        isLoadingTrips = true
        refreshTrips()
        isLoadingTrips = false
    }

    val activeTrip = remember(trips) { trips.firstOrNull { it.is_active } }

    // --- Transaction form state (mirrors AddPayedScreen) ---
    var categories by remember { mutableStateOf(HomeDataCache.categories) }
    LaunchedEffect(Unit) {
        CategoryRepository.getCategories().onSuccess { categories = it.toUiCategories() }
    }

    var amount by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var fromWho by remember { mutableStateOf(TextFieldValue("")) }
    var isReceivedMode by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }
    var selectedCurrency by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTime by remember { mutableStateOf(LocalTime.now()) }
    var isSavingTransaction by remember { mutableStateOf(false) }
    var transactionError by remember { mutableStateOf<String?>(null) }

    // Default to the trip's first currency whenever the active trip changes.
    LaunchedEffect(activeTrip?.id) {
        selectedCurrency = activeTrip?.currencies?.firstOrNull()
    }

    var todayTripTransactions by remember { mutableStateOf<List<TransactionDetail>>(emptyList()) }

    suspend fun refreshToday(tripId: Int?) {
        if (tripId == null) {
            todayTripTransactions = emptyList()
            return
        }
        TransactionRepository.getTodayTransactions().onSuccess { all ->
            todayTripTransactions = all.filter { it.trip_id == tripId }
        }
    }

    LaunchedEffect(activeTrip?.id) {
        refreshToday(activeTrip?.id)
    }

    val onSubmitTransaction: () -> Unit = {
        val parsedAmount = amount.text.toDoubleOrNull()
        val trip = activeTrip
        when {
            trip == null -> transactionError = "Select a trip first"
            parsedAmount == null || parsedAmount <= 0.0 -> transactionError = "Enter a valid amount"
            !isReceivedMode && selectedCategoryId == null -> transactionError = "Pick a category"
            selectedCurrency == null -> transactionError = "Pick a currency"
            else -> {
                scope.launch {
                    isSavingTransaction = true
                    transactionError = null
                    val result = TransactionRepository.addTransaction(
                        amount = parsedAmount,
                        type = if (isReceivedMode) TransactionRepository.TYPE_RECEIVED else TransactionRepository.TYPE_PAYED,
                        categoryId = selectedCategoryId,
                        source = if (isReceivedMode) fromWho.text else null,
                        description = description.text,
                        date = LocalDateTime.of(selectedDate, selectedTime).atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                        tripId = trip.id,
                        currency = selectedCurrency
                    )
                    isSavingTransaction = false
                    result.onSuccess {
                        amount = TextFieldValue("")
                        description = TextFieldValue("")
                        fromWho = TextFieldValue("")
                        selectedCategoryId = null
                        selectedDate = LocalDate.now()
                        selectedTime = LocalTime.now()
                        refreshToday(trip.id)
                    }.onFailure { e ->
                        transactionError = e.message ?: "Couldn't save"
                    }
                }
            }
        }
    }

    if (showAddTripDialog) {
        AddTripDialog(
            isSaving = isSavingTrip,
            errorMessage = addTripError,
            onDismiss = { showAddTripDialog = false; addTripError = null },
            onConfirm = { name, currencies ->
                scope.launch {
                    isSavingTrip = true
                    addTripError = null
                    val result = TripRepository.addTrip(name, currencies)
                    isSavingTrip = false
                    result.onSuccess {
                        showAddTripDialog = false
                        refreshTrips()
                    }.onFailure { e ->
                        addTripError = e.message ?: "Couldn't add trip"
                    }
                }
            }
        )
    }

    tripToDelete?.let { trip ->
        ConfirmationDialog(
            title = "Delete \"${trip.name}\"?",
            message = deleteTripError ?: "This trip will be removed. Its past transactions stay in your history, just unlinked from it.",
            confirmLabel = "Delete",
            isLoading = isDeletingTrip,
            onDismiss = { tripToDelete = null; deleteTripError = null },
            onConfirm = {
                scope.launch {
                    isDeletingTrip = true
                    deleteTripError = null
                    val id = trip.id
                    val result = if (id != null) TripRepository.deleteTrip(id) else Result.failure(Exception("Missing id"))
                    isDeletingTrip = false
                    result.onSuccess {
                        tripToDelete = null
                        refreshTrips()
                    }.onFailure { e ->
                        deleteTripError = e.message ?: "Couldn't delete"
                    }
                }
            }
        )
    }

    Scaffold(
        containerColor = LiraBackground,
        bottomBar = {
            LiraBottomNavBar(
                currentRoute = Routes.TRAVELLING,
                onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 15.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            AppHeader(onSettingsClick = { navController.navigate(Routes.SETTINGS) })
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Travelling", color = LiraTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { showAddTripDialog = true }
                ) {
                    Text(text = "Add", color = LiraTextPrimary.copy(alpha = 0.6f), fontSize = 13.sp)
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = "Add Trip",
                        tint = LiraTextPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when {
                isLoadingTrips -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiraTextPrimary)
                }
                loadError != null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(loadError ?: "", color = ErrorColor, fontSize = 14.sp)
                }
                trips.isEmpty() -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("Add a country you're travelling to, to start.", color = LiraTextSecondary, fontSize = 14.sp)
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    trips.forEach { trip ->
                        TripRow(
                            trip = trip,
                            onOpenDetail = {
                                trip.id?.let { navController.navigate(Routes.tripDetail(it)) }
                            },
                            onToggleActive = {
                                scope.launch {
                                    TripRepository.setActiveTrip(if (trip.is_active) null else trip.id)
                                    refreshTrips()
                                }
                            },
                            onDelete = { tripToDelete = trip }
                        )
                    }
                }
            }

            if (activeTrip != null) {
                Spacer(Modifier.height(28.dp))

                TransactionForm(
                    isReceivedMode = isReceivedMode,
                    amount = amount,
                    onAmountChange = { amount = it },
                    desc = description,
                    onDescChange = { description = it },
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelect = { id -> selectedCategoryId = id },
                    onAddCategoryClick = { navController.navigate(Routes.MANAGE_CATEGORIES) },
                    fromWho = fromWho,
                    onFromWhoChange = { fromWho = it },
                    selectedDate = selectedDate,
                    onDateChange = { selectedDate = it },
                    selectedTime = selectedTime,
                    onTimeChange = { selectedTime = it },
                    isSavingTransaction = isSavingTransaction,
                    transactionError = transactionError,
                    onSubmitTransaction = onSubmitTransaction,
                    isProcessingReceipt = false,
                    receiptError = null,
                    receiptInfo = null,
                    onScanReceiptClick = {},
                    tripCurrencies = activeTrip.currencies,
                    selectedCurrency = selectedCurrency,
                    onCurrencyChange = { selectedCurrency = it }
                )

                Spacer(Modifier.height(24.dp))

                TripOverviewCard(
                    title = "Today in ${activeTrip.name}",
                    trip = activeTrip,
                    transactions = todayTripTransactions,
                    emptyMessage = "No spending logged for this trip today"
                )
            }

            Spacer(Modifier.height(28.dp))

            QuickConvertCard()

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickConvertCard() {
    var amount by remember { mutableStateOf(TextFieldValue("1")) }
    var fromCode by remember { mutableStateOf(CurrencyPreference.current.name) }
    var toCode by remember { mutableStateOf(if (CurrencyPreference.current.name == "EUR") "USD" else "EUR") }
    var currencies by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var result by remember { mutableStateOf<ExchangeRateResult?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    var pickerFor by remember { mutableStateOf<CurrencyField?>(null) }

    LaunchedEffect(Unit) {
        CurrencyConverterRepository.getSupportedCurrencies().onSuccess { currencies = it }
    }

    LaunchedEffect(amount.text, fromCode, toCode) {
        val parsed = amount.text.toDoubleOrNull()
        if (parsed == null || parsed <= 0.0) {
            result = null
            return@LaunchedEffect
        }
        delay(400)
        isConverting = true
        CurrencyConverterRepository.convert(parsed, fromCode, toCode).onSuccess { result = it }
        isConverting = false
    }

    pickerFor?.let { field ->
        CurrencyPickerDialog(
            currencies = currencies,
            selected = if (field == CurrencyField.FROM) fromCode else toCode,
            onDismiss = { pickerFor = null },
            onSelect = { code ->
                if (field == CurrencyField.FROM) fromCode = code else toCode = code
                pickerFor = null
            }
        )
    }

    Column(Modifier.fillMaxWidth()) {
        Text("Quick Convert", color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, LiraBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiraTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    placeholder = "0.00",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                CurrencyCodePill(fromCode) { pickerFor = CurrencyField.FROM }
            }

            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LiraBorder.copy(alpha = 0.4f))
                        .clickable {
                            val temp = fromCode
                            fromCode = toCode
                            toCode = temp
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⇅", color = LiraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .border(1.dp, LiraBorder, RoundedCornerShape(25.dp))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val convertedText = when {
                        isConverting -> "…"
                        else -> result?.rates?.get(toCode)?.let { String.format("%.2f", it) } ?: "—"
                    }
                    Text(convertedText, color = LiraTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                CurrencyCodePill(toCode) { pickerFor = CurrencyField.TO }
            }
        }
    }
}

@Composable
private fun CurrencyCodePill(code: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(LiraBorder.copy(alpha = 0.4f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(code, color = LiraTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TripRow(trip: Trip, onOpenDetail: () -> Unit, onToggleActive: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (trip.is_active) LiraBorder.copy(alpha = 0.5f) else Color.Transparent)
            .border(1.dp, LiraBorder, RoundedCornerShape(16.dp))
            .clickable { onOpenDetail() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(trip.name, color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(trip.currencies.joinToString(" · "), color = LiraTextSecondary, fontSize = 12.sp)
        }
        Text(
            text = if (trip.is_active) "Active" else "Activate",
            color = if (trip.is_active) IncomeColor else LiraTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onToggleActive() }
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = "Delete",
            color = ErrorColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { onDelete() }
        )
    }
}

@Composable
internal fun TripOverviewCard(
    title: String,
    trip: Trip,
    transactions: List<TransactionDetail>,
    emptyMessage: String = "No spending logged for this trip yet"
) {
    val homeCode = CurrencyPreference.current.name
    val homeSymbol = CurrencyPreference.current.symbol

    val currencyTotals = remember(transactions) {
        transactions
            .filter { it.type == TransactionRepository.TYPE_PAYED }
            .groupBy { it.currency ?: trip.currencies.firstOrNull() ?: homeCode }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
    }

    var convertedTotals by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var isConverting by remember { mutableStateOf(false) }

    LaunchedEffect(currencyTotals, homeCode) {
        if (currencyTotals.isEmpty()) {
            convertedTotals = emptyMap()
            return@LaunchedEffect
        }
        isConverting = true
        val results = mutableMapOf<String, Double>()
        currencyTotals.forEach { (code, amt) ->
            if (code == homeCode) {
                results[code] = amt
            } else {
                CurrencyConverterRepository.convert(amt, code, homeCode)
                    .onSuccess { r -> results[code] = r.rates[homeCode] ?: 0.0 }
            }
        }
        convertedTotals = results
        isConverting = false
    }

    val grandTotal = convertedTotals.values.sum()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = LiraTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, LiraBorder, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            if (currencyTotals.isEmpty()) {
                Text(emptyMessage, color = LiraTextSecondary, fontSize = 14.sp)
            } else {
                currencyTotals.forEach { (code, amt) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(code, color = LiraTextSecondary, fontSize = 14.sp)
                        Text(
                            text = String.format("%.2f %s", amt, code),
                            color = LiraTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = LiraBorder)
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total (converted)", color = LiraTextSecondary, fontSize = 13.sp)
                    if (isConverting) {
                        Text("…", color = LiraTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            text = String.format("%s%.2f", homeSymbol, grandTotal),
                            color = ExpenseColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTripDialog(
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, currencies: List<String>) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var allCurrencies by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedCurrencies by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var fieldError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        CurrencyConverterRepository.getSupportedCurrencies().onSuccess { allCurrencies = it }
    }

    val filtered = remember(allCurrencies, query.text) {
        allCurrencies.entries
            .filter {
                query.text.isBlank() ||
                    it.key.contains(query.text, ignoreCase = true) ||
                    it.value.contains(query.text, ignoreCase = true)
            }
            .sortedBy { it.key }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = LiraBackground,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("New Trip", color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column {
                LiraTextField(
                    value = name,
                    onValueChange = { name = it; fieldError = null },
                    placeholder = "Country / trip name (e.g. Japan)"
                )

                Spacer(Modifier.height(16.dp))
                Text("Currencies", color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                LiraTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search"
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(filtered, key = { it.key }) { (code, currencyName) ->
                        val isSelected = code in selectedCurrencies
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCurrencies = if (isSelected) selectedCurrencies - code else selectedCurrencies + code
                                    fieldError = null
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(code, color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(currencyName, color = LiraTextSecondary, fontSize = 12.sp)
                            }
                            if (isSelected) {
                                Text("✓", color = LiraTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                (fieldError ?: errorMessage)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = ErrorColor, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        name.text.isBlank() -> fieldError = "Enter a name"
                        selectedCurrencies.isEmpty() -> fieldError = "Pick at least one currency"
                        else -> onConfirm(name.text.trim(), selectedCurrencies.toList())
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = LiraTextPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Add", color = LiraTextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel", color = LiraTextSecondary)
            }
        }
    )
}
