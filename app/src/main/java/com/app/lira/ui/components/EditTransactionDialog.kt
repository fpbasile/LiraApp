package com.app.lira.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.data.model.Category
import com.app.lira.data.repository.TransactionDetail
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.ui.theme.LiraBackground
import com.app.lira.ui.theme.LiraTextPrimary
import com.app.lira.ui.theme.LiraTextSecondary

private val DialogErrorColor = Color(0xFFE53935)

@Composable
fun EditTransactionDialog(
    transaction: TransactionDetail,
    categories: List<Category>,
    isSaving: Boolean,
    isDeleting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (amount: Double, categoryId: Int?, source: String?, description: String?) -> Unit,
    onDeleteClick: () -> Unit
) {
    val isReceived = transaction.type == TransactionRepository.TYPE_RECEIVED
    var amount by remember { mutableStateOf(TextFieldValue(String.format("%.2f", transaction.amount))) }
    var description by remember { mutableStateOf(TextFieldValue(transaction.description ?: "")) }
    var source by remember { mutableStateOf(TextFieldValue(transaction.source ?: "")) }
    var selectedCategoryId by remember {
        mutableStateOf(categories.firstOrNull { it.name == transaction.category?.name }?.id)
    }
    var fieldError by remember { mutableStateOf<String?>(null) }

    val busy = isSaving || isDeleting

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = LiraBackground,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("Edit Transaction", color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                LiraTextField(
                    value = amount,
                    onValueChange = { amount = it; fieldError = null },
                    placeholder = "Amount",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(Modifier.height(14.dp))

                if (isReceived) {
                    Text("From who", color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    LiraTextField(
                        value = source,
                        onValueChange = { source = it },
                        placeholder = "Who sent it?"
                    )
                } else if (categories.isNotEmpty()) {
                    Text("Category", color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        categories.forEach { category ->
                            CategoryChip(
                                category = category,
                                isSelected = category.id == selectedCategoryId,
                                onClick = { selectedCategoryId = category.id },
                                modifier = Modifier.width(110.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text("Description", color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                LiraTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = "(Optional)"
                )

                (fieldError ?: errorMessage)?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = DialogErrorColor, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = amount.text.toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        fieldError = "Enter a valid amount"
                    } else {
                        onSave(
                            parsed,
                            if (isReceived) null else selectedCategoryId,
                            if (isReceived) source.text else null,
                            description.text
                        )
                    }
                },
                enabled = !busy
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = LiraTextPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Save", color = LiraTextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDeleteClick, enabled = !busy) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DialogErrorColor, strokeWidth = 2.dp)
                    } else {
                        Text("Delete", color = DialogErrorColor, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text("Cancel", color = LiraTextSecondary)
                }
            }
        }
    )
}
