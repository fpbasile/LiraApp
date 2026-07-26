package com.app.lira.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.repository.TransactionDetail
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.ui.theme.LiraBackground
import com.app.lira.ui.theme.LiraTextPrimary
import com.app.lira.ui.theme.LiraTextSecondary
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

val IncomeColor = Color(0xFF4CAF50)
val ExpenseColor = Color(0xFFE53935)
val RecurringColor = Color(0xFF64B5F6)

/**
 * A single transaction line: avatar-letter, title/description, and signed amount + timestamp.
 * Pass [onClick] to make the row tappable (e.g. to open an edit/delete dialog).
 */
@Composable
fun TransactionRow(txn: TransactionDetail, onClick: (() -> Unit)? = null) {
    val isIncome = txn.type == TransactionRepository.TYPE_RECEIVED
    val isRecurring = txn.recurring_id != null
    val accentColor = if (isIncome) IncomeColor else ExpenseColor
    val avatarColor = if (isRecurring) RecurringColor else accentColor
    val title = txn.displayTitle()
    // Suppress the description line when it's identical to the title (happens when
    // displayTitle() already fell back to the description because there's no category).
    val description = txn.description?.takeIf { it.isNotBlank() && it != title }
    val timestamp = txn.formattedTimestamp()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it },
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.size(44.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.take(1).uppercase(),
                    color = avatarColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            if (isRecurring) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(RecurringColor)
                        .border(2.dp, LiraBackground, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "↻", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
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

fun TransactionDetail.displayTitle(): String = when {
    type == TransactionRepository.TYPE_RECEIVED -> source?.takeIf { it.isNotBlank() } ?: "Received"
    else -> category?.name ?: description?.takeIf { it.isNotBlank() } ?: "Expense"
}

fun TransactionDetail.formattedTimestamp(): String {
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

/** Parses [TransactionDetail.date] to a [java.time.LocalDate], or null if unparseable. */
fun TransactionDetail.localDate(): java.time.LocalDate? = try {
    OffsetDateTime.parse(date).toLocalDate()
} catch (e: DateTimeParseException) {
    try {
        LocalDateTime.parse(date).toLocalDate()
    } catch (e2: DateTimeParseException) {
        null
    }
}
