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
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.repository.TransactionDetail
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.ui.components.LiraTextField
import com.app.lira.ui.theme.*
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private enum class TransactionFilter(val label: String) {
    ALL("All"),
    INCOME("Income"),
    EXPENSES("Expenses")
}

private val IncomeColor = Color(0xFF4CAF50)
private val ExpenseColor = Color(0xFFE53935)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TransactionsScreen(navController: NavController) {
    var transactions by remember { mutableStateOf<List<TransactionDetail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var filter by remember { mutableStateOf(TransactionFilter.ALL) }

    LaunchedEffect(Unit) {
        isLoading = true
        TransactionRepository.getTodayTransactions()
            .onSuccess { transactions = it }
            .onFailure { errorMessage = it.message }
        isLoading = false
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
                    text = "Today's Transactions",
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
                    Text("No transactions yet today", color = LiraTextSecondary, fontSize = 14.sp)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(filtered, key = { it.id }) { txn ->
                        TransactionRow(txn)
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(txn: TransactionDetail) {
    val isIncome = txn.type == TransactionRepository.TYPE_RECEIVED
    val accentColor = if (isIncome) IncomeColor else ExpenseColor
    val title = txn.displayTitle()
    val description = txn.description?.takeIf { it.isNotBlank() }
    val timestamp = txn.formattedTimestamp()

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.take(1).uppercase(),
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            description?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = LiraTextSecondary, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            val symbol = CurrencyPreference.current.symbol
            Text(
                text = String.format(if (isIncome) "+%s%.2f" else "-%s%.2f", symbol, txn.amount),
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            if (timestamp.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(timestamp, color = LiraTextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
    }
}

private fun TransactionDetail.displayTitle(): String = when {
    type == TransactionRepository.TYPE_RECEIVED -> source?.takeIf { it.isNotBlank() } ?: "Received"
    else -> category?.name ?: "Expense"
}

private fun TransactionDetail.formattedTimestamp(): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm · d MMM")
    // `transactions.date` is a `timestamp` column (no time zone), so Postgrest returns it
    // without an offset (e.g. "2026-07-25T14:32:10.123") — OffsetDateTime.parse alone can't
    // read that, so fall back to LocalDateTime for the offset-less case.
    return try {
        OffsetDateTime.parse(date).format(formatter)
    } catch (e: DateTimeParseException) {
        try {
            LocalDateTime.parse(date).format(formatter)
        } catch (e2: DateTimeParseException) {
            ""
        }
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
