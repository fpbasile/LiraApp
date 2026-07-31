package com.app.lira.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.app.lira.data.model.Recurring
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.ui.components.CategoryChip
import com.app.lira.ui.components.ConfirmationDialog
import com.app.lira.ui.components.ExpenseColor
import com.app.lira.ui.components.IncomeColor
import com.app.lira.ui.components.LiraTextField
import com.app.lira.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val ErrorColor = Color(0xFFE53935)

@Composable
fun RecurringScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<Recurring>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<Recurring?>(null) }
    var isSavingAdd by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }

    var entryToDelete by remember { mutableStateOf<Recurring?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        RecurringRepository.getRecurring()
            .onSuccess { entries = it }
            .onFailure { e -> loadError = e.message }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        refresh()
        isLoading = false
    }

    if (showAddDialog || editingEntry != null) {
        val editTarget = editingEntry
        AddRecurringDialog(
            existing = editTarget,
            isSaving = isSavingAdd,
            errorMessage = addError,
            onDismiss = { showAddDialog = false; editingEntry = null; addError = null },
            onConfirm = { name, amount, interval, startDate, occurrences, type, categoryId ->
                scope.launch {
                    isSavingAdd = true
                    addError = null
                    val result = if (editTarget != null) {
                        RecurringRepository.updateRecurring(editTarget, name, amount, startDate, interval, occurrences, type, categoryId)
                    } else {
                        RecurringRepository.addRecurring(name, amount, startDate, interval, occurrences, type, categoryId).map { }
                    }
                    isSavingAdd = false
                    result.onSuccess {
                        // Otherwise a newly added (or just-edited) entry due today/earlier would
                        // sit unposted until the next cold app start — post it right away instead.
                        RecurringRepository.postDueOccurrences()
                        showAddDialog = false
                        editingEntry = null
                        refresh()
                    }.onFailure { e ->
                        addError = e.message ?: "Couldn't save recurring payment"
                    }
                }
            }
        )
    }

    entryToDelete?.let { deleting ->
        ConfirmationDialog(
            title = "Delete \"${deleting.name}\"?",
            message = deleteError ?: "This recurring payment will stop appearing here. This can't be undone.",
            confirmLabel = "Delete",
            isLoading = isDeleting,
            onDismiss = { entryToDelete = null; deleteError = null },
            onConfirm = {
                scope.launch {
                    isDeleting = true
                    deleteError = null
                    val id = deleting.id
                    val result = if (id != null) RecurringRepository.deleteRecurring(id) else Result.failure(Exception("Missing id"))
                    isDeleting = false
                    result.onSuccess {
                        entryToDelete = null
                        refresh()
                    }.onFailure { e ->
                        deleteError = e.message ?: "Couldn't delete"
                    }
                }
            }
        )
    }

    Scaffold(containerColor = LiraBackground) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = "←",
                    color = LiraTextPrimary,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { navController.popBackStack() }
                )
                Text(
                    text = "Recurring Payments",
                    color = LiraTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Payments", color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { showAddDialog = true }
                ) {
                    Text(text = "Add", color = LiraTextPrimary.copy(alpha = 0.6f), fontSize = 13.sp)
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = "Add Recurring Payment",
                        tint = LiraTextPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiraTextPrimary)
                }
                loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(loadError ?: "", color = ErrorColor, fontSize = 14.sp)
                }
                entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No recurring payments yet", color = LiraTextSecondary, fontSize = 14.sp)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(entries, key = { it.id ?: it.hashCode() }) { entry ->
                        RecurringRow(
                            entry,
                            onClick = { editingEntry = entry },
                            onDelete = { entryToDelete = entry }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RecurringRow(entry: Recurring, onClick: () -> Unit, onDelete: () -> Unit) {
    val isReceived = entry.type == TransactionRepository.TYPE_RECEIVED
    val accentColor = if (isReceived) IncomeColor else ExpenseColor
    val categoryName = entry.category_id?.let { id -> HomeDataCache.categories.firstOrNull { it.id == id }?.name }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            val detail = buildString {
                append(entry.interval.replaceFirstChar { it.uppercase() })
                append(" · starts ${formatDate(entry.start_date)}")
                categoryName?.let { append(" · $it") }
            }
            Text(detail, color = LiraTextSecondary, fontSize = 13.sp)
        }

        Text(
            text = String.format(if (isReceived) "+%s%.2f" else "-%s%.2f", CurrencyPreference.current.symbol, entry.amount),
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
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

private fun formatDate(isoDate: String): String = try {
    LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
} catch (e: Exception) {
    isoDate
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecurringDialog(
    existing: Recurring? = null,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, amount: Double, interval: String, startDate: String, occurrences: Int?, type: String, categoryId: Int?) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(existing?.name ?: "")) }
    var amount by remember { mutableStateOf(TextFieldValue(existing?.amount?.let { String.format("%.2f", it) } ?: "")) }
    var type by remember { mutableStateOf(existing?.type ?: TransactionRepository.TYPE_PAYED) }
    var interval by remember { mutableStateOf(existing?.interval ?: RecurringRepository.INTERVALS[2]) } // monthly by default
    var occurrences by remember { mutableStateOf(TextFieldValue(existing?.occurrences?.toString() ?: "")) }
    var startDateMillis by remember {
        mutableStateOf(
            existing?.start_date?.let { iso ->
                try {
                    LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
            } ?: System.currentTimeMillis()
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf(existing?.category_id) }
    var fieldError by remember { mutableStateOf<String?>(null) }

    val startDateText = remember(startDateMillis) {
        Instant.ofEpochMilli(startDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
            .format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { startDateMillis = it }
                    showDatePicker = false
                }) {
                    Text("OK", color = LiraTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = LiraTextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = LiraBackground)
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = LiraBackground,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                if (existing != null) "Edit Recurring Payment" else "New Recurring Payment",
                color = LiraTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntervalChip(
                        label = "Payment",
                        isSelected = type == TransactionRepository.TYPE_PAYED,
                        onClick = { type = TransactionRepository.TYPE_PAYED }
                    )
                    IntervalChip(
                        label = "Income",
                        isSelected = type == TransactionRepository.TYPE_RECEIVED,
                        onClick = { type = TransactionRepository.TYPE_RECEIVED; selectedCategoryId = null }
                    )
                }

                Spacer(Modifier.height(16.dp))

                LiraTextField(
                    value = name,
                    onValueChange = { name = it; fieldError = null },
                    placeholder = if (type == TransactionRepository.TYPE_RECEIVED) "Name (e.g. Paycheck)" else "Name (e.g. Rent)"
                )

                Spacer(Modifier.height(12.dp))

                LiraTextField(
                    value = amount,
                    onValueChange = { amount = it; fieldError = null },
                    placeholder = "Amount in ${CurrencyPreference.current.symbol}",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (type == TransactionRepository.TYPE_PAYED && HomeDataCache.categories.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Category (optional)", color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeDataCache.categories.forEach { category ->
                            CategoryChip(
                                category = category,
                                isSelected = category.id == selectedCategoryId,
                                onClick = {
                                    selectedCategoryId = if (selectedCategoryId == category.id) null else category.id
                                },
                                modifier = Modifier.width(110.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Interval", color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecurringRepository.INTERVALS.forEach { option ->
                        IntervalChip(
                            label = option.replaceFirstChar { it.uppercase() },
                            isSelected = interval == option,
                            onClick = { interval = option }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Start Date", color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(LiraBorder.copy(alpha = 0.3f))
                        .clickable { showDatePicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(startDateText, color = LiraTextPrimary, fontSize = 15.sp)
                }

                Spacer(Modifier.height(12.dp))

                LiraTextField(
                    value = occurrences,
                    onValueChange = { occurrences = it; fieldError = null },
                    placeholder = "Occurrences (optional)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                (fieldError ?: errorMessage)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = ErrorColor, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedAmount = amount.text.toDoubleOrNull()
                    val parsedOccurrences = occurrences.text.takeIf { it.isNotBlank() }?.toIntOrNull()
                    when {
                        name.text.isBlank() -> fieldError = "Enter a name"
                        parsedAmount == null || parsedAmount <= 0.0 -> fieldError = "Enter a valid amount"
                        occurrences.text.isNotBlank() && parsedOccurrences == null -> fieldError = "Occurrences must be a number"
                        else -> {
                            val isoDate = Instant.ofEpochMilli(startDateMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                            onConfirm(name.text.trim(), parsedAmount, interval, isoDate, parsedOccurrences, type, selectedCategoryId)
                        }
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = LiraTextPrimary, strokeWidth = 2.dp)
                } else {
                    Text(if (existing != null) "Save" else "Add", color = LiraTextPrimary, fontWeight = FontWeight.Bold)
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

@Composable
private fun IntervalChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color.White else LiraBorder.copy(alpha = 0.4f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else LiraTextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        )
    }
}
