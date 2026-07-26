package com.app.lira.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.app.lira.data.model.Recurring
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.ui.components.ConfirmationDialog
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

    if (showAddDialog) {
        AddRecurringDialog(
            isSaving = isSavingAdd,
            errorMessage = addError,
            onDismiss = { showAddDialog = false; addError = null },
            onConfirm = { name, amount, interval, startDate, occurrences ->
                scope.launch {
                    isSavingAdd = true
                    addError = null
                    val result = RecurringRepository.addRecurring(name, amount, startDate, interval, occurrences)
                    isSavingAdd = false
                    result.onSuccess {
                        showAddDialog = false
                        refresh()
                    }.onFailure { e ->
                        addError = e.message ?: "Couldn't add recurring payment"
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
                        RecurringRow(entry, onDelete = { entryToDelete = entry })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RecurringRow(entry: Recurring, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${entry.interval.replaceFirstChar { it.uppercase() }} · starts ${formatDate(entry.start_date)}",
                color = LiraTextSecondary,
                fontSize = 13.sp
            )
        }

        Text(
            text = String.format("%s%.2f", CurrencyPreference.current.symbol, entry.amount),
            color = LiraTextPrimary,
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
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, amount: Double, interval: String, startDate: String, occurrences: Int?) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var amount by remember { mutableStateOf(TextFieldValue("")) }
    var interval by remember { mutableStateOf(RecurringRepository.INTERVALS[2]) } // monthly by default
    var occurrences by remember { mutableStateOf(TextFieldValue("")) }
    var startDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
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
            Text("New Recurring Payment", color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                LiraTextField(
                    value = name,
                    onValueChange = { name = it; fieldError = null },
                    placeholder = "Name (e.g. Rent)"
                )

                Spacer(Modifier.height(12.dp))

                LiraTextField(
                    value = amount,
                    onValueChange = { amount = it; fieldError = null },
                    placeholder = "Amount in ${CurrencyPreference.current.symbol}",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

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
                            onConfirm(name.text.trim(), parsedAmount, interval, isoDate, parsedOccurrences)
                        }
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
