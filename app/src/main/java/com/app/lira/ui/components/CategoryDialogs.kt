package com.app.lira.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.ui.theme.LiraBackground
import com.app.lira.ui.theme.LiraTextPrimary
import com.app.lira.ui.theme.LiraTextSecondary

private val DialogErrorColor = Color(0xFFE53935)

@Composable
fun AddCategoryDialog(
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    CategoryNameDialog(
        title = "New Category",
        confirmLabel = "Add",
        initialName = "",
        isSaving = isSaving,
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
fun EditCategoryDialog(
    currentName: String,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    CategoryNameDialog(
        title = "Rename Category",
        confirmLabel = "Save",
        initialName = currentName,
        isSaving = isSaving,
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
private fun CategoryNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(initialName)) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = LiraBackground,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(title, color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column {
                LiraTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Category name",
                    modifier = Modifier.height(58.dp),
                    fontSize = 18.sp
                )
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = DialogErrorColor, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.text.isNotBlank()) onConfirm(name.text.trim()) },
                enabled = !isSaving && name.text.isNotBlank()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = LiraTextPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(confirmLabel, color = LiraTextPrimary, fontWeight = FontWeight.Bold)
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
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmColor: Color = DialogErrorColor
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = LiraBackground,
        shape = RoundedCornerShape(20.dp),
        title = { Text(title, color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = { Text(message, color = LiraTextSecondary, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = confirmColor, strokeWidth = 2.dp)
                } else {
                    Text(confirmLabel, color = confirmColor, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel", color = LiraTextSecondary)
            }
        }
    )
}
