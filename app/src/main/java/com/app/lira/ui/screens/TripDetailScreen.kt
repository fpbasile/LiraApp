package com.app.lira.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.lira.data.HomeDataCache
import com.app.lira.data.model.Trip
import com.app.lira.data.repository.TransactionDetail
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.data.repository.TripRepository
import com.app.lira.ui.components.EditTransactionDialog
import com.app.lira.ui.components.TransactionRow
import com.app.lira.ui.components.localDate
import com.app.lira.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ErrorColor = Color(0xFFE53935)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TripDetailScreen(navController: NavController, tripId: Int) {
    val scope = rememberCoroutineScope()

    var trip by remember { mutableStateOf<Trip?>(null) }
    var transactions by remember { mutableStateOf<List<TransactionDetail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        TripRepository.getTrip(tripId).onSuccess { trip = it }.onFailure { e -> loadError = e.message }
        TransactionRepository.getAllTransactions().onSuccess { all ->
            transactions = all.filter { it.trip_id == tripId }
        }.onFailure { e -> loadError = e.message }
    }

    LaunchedEffect(tripId) {
        isLoading = true
        refresh()
        isLoading = false
    }

    val sections = remember(transactions) {
        transactions.groupBy { it.localDate() ?: LocalDate.MIN }
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
                    text = trip?.name ?: "Trip",
                    color = LiraTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(Modifier.height(24.dp))

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiraTextPrimary)
                }
                loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(loadError ?: "", color = ErrorColor, fontSize = 14.sp)
                }
                trip == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("This trip no longer exists", color = LiraTextSecondary, fontSize = 14.sp)
                }
                else -> {
                    val currentTrip = trip!!
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            TripOverviewCard(
                                title = "All-time in ${currentTrip.name}",
                                trip = currentTrip,
                                transactions = transactions,
                                emptyMessage = "No spending logged for this trip yet"
                            )
                            Spacer(Modifier.height(24.dp))
                            Text("History", color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(4.dp))
                        }

                        if (transactions.isEmpty()) {
                            item {
                                Text("No transactions for this trip yet", color = LiraTextSecondary, fontSize = 14.sp)
                            }
                        } else {
                            sections.forEach { (date, txns) ->
                                item(key = "header_$date") {
                                    Text(
                                        text = sectionLabel(date),
                                        color = LiraTextSecondary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                                    )
                                }
                                items(txns, key = { it.id }) { txn ->
                                    TransactionRow(txn, onClick = { editingTransaction = txn })
                                }
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

private fun sectionLabel(date: LocalDate): String {
    if (date == LocalDate.MIN) return "Unknown Date"
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(
            DateTimeFormatter.ofPattern(if (date.year == today.year) "EEEE, d MMM" else "d MMM yyyy")
        )
    }
}
