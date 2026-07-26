package com.app.lira.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.lira.data.HomeDataCache
import com.app.lira.data.repository.TransactionDetail
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.ui.components.LiraTextField
import com.app.lira.ui.components.EditTransactionDialog
import com.app.lira.ui.components.ExpenseColor
import com.app.lira.ui.components.TransactionRow
import com.app.lira.ui.components.displayTitle
import com.app.lira.ui.components.localDate
import com.app.lira.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class TransactionFilter(val label: String) {
    ALL("All"),
    INCOME("Income"),
    EXPENSES("Expenses")
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TransactionsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    var transactions by remember { mutableStateOf<List<TransactionDetail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var filter by remember { mutableStateOf(TransactionFilter.ALL) }

    suspend fun refresh() {
        isLoading = true
        TransactionRepository.getAllTransactions()
            .onSuccess { transactions = it }
            .onFailure { errorMessage = it.message }
        isLoading = false
    }

    LaunchedEffect(Unit) { refresh() }

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

    val filtered = remember(transactions, query.text, filter) {
        transactions.filter { txn ->
            val matchesFilter = when (filter) {
                TransactionFilter.ALL -> true
                TransactionFilter.INCOME -> txn.type == TransactionRepository.TYPE_RECEIVED
                TransactionFilter.EXPENSES -> txn.type == TransactionRepository.TYPE_PAYED
            }
            val matchesQuery = query.text.isBlank() ||
                txn.displayTitle().contains(query.text, ignoreCase = true) ||
                txn.description?.contains(query.text, ignoreCase = true) == true
            matchesFilter && matchesQuery
        }
    }

    // Newest-first input means groupBy naturally keeps sections in newest-to-oldest order.
    val sections = remember(filtered) {
        filtered.groupBy { it.localDate() ?: LocalDate.MIN }
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
                    text = "Transactions",
                    color = LiraTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(Modifier.height(20.dp))

            LiraTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search",
                leadingIcon = { SearchGlyph(tint = LiraTextSecondary) }
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TransactionFilter.entries.forEach { option ->
                    FilterChip(
                        label = option.label,
                        isSelected = filter == option,
                        onClick = { filter = option }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiraTextPrimary)
                }
                errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMessage ?: "", color = ExpenseColor, fontSize = 14.sp)
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions found", color = LiraTextSecondary, fontSize = 14.sp)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    sections.forEach { (date, txns) ->
                        item(key = "header_$date") {
                            Text(
                                text = sectionLabel(date),
                                color = LiraTextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(txns, key = { it.id }) { txn ->
                            TransactionRow(txn, onClick = { editingTransaction = txn })
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
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

@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color.White else LiraBorder.copy(alpha = 0.4f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else LiraTextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SearchGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val strokeWidth = 1.6.dp.toPx()
        val radius = size.minDimension * 0.32f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color = tint, radius = radius, center = center, style = Stroke(width = strokeWidth))
        drawLine(
            color = tint,
            start = Offset(center.x + radius * 0.7f, center.y + radius * 0.7f),
            end = Offset(size.width * 0.88f, size.height * 0.88f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
