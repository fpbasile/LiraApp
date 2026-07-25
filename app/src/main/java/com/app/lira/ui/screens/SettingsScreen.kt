package com.app.lira.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.lira.R
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.AccountRepository
import com.app.lira.data.repository.AuthRepository
import com.app.lira.data.repository.DailyLimitRepository
import com.app.lira.navigation.Routes
import com.app.lira.ui.components.LiraTextField
import com.app.lira.ui.theme.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val ErrorColor = Color(0xFFE53935)
private val SuccessColor = Color(0xFF4CAF50)

@Composable
fun SettingsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    val userEmail = remember { SupabaseClient.client.auth.currentUserOrNull()?.email ?: "" }

    var limitInput by remember { mutableStateOf(TextFieldValue("")) }
    var isLoadingLimit by remember { mutableStateOf(true) }
    var isSavingLimit by remember { mutableStateOf(false) }
    var limitError by remember { mutableStateOf<String?>(null) }
    var limitSaved by remember { mutableStateOf(false) }

    var isSigningOut by remember { mutableStateOf(false) }
    var signOutError by remember { mutableStateOf<String?>(null) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    if (showDeleteConfirm) {
        DeleteAccountDialog(
            isDeleting = isDeletingAccount,
            onDismiss = { if (!isDeletingAccount) showDeleteConfirm = false },
            onConfirm = {
                scope.launch {
                    isDeletingAccount = true
                    deleteError = null
                    val result = AccountRepository.deleteAccountData()
                    isDeletingAccount = false
                    result.onSuccess {
                        showDeleteConfirm = false
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }.onFailure { e ->
                        deleteError = e.message ?: "Couldn't delete account"
                    }
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        DailyLimitRepository.getDailyLimit().onSuccess {
            limitInput = TextFieldValue(String.format("%.2f", it))
        }
        isLoadingLimit = false
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
                    text = "Settings",
                    color = LiraTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(Modifier.height(28.dp))

            SectionLabel("General")
            Spacer(Modifier.height(10.dp))
            SectionCard {
                Text("Daily Limit", color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Used to calculate the progress bar on your home overview.",
                    color = LiraTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(16.dp))

                if (isLoadingLimit) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = LiraTextPrimary)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LiraTextField(
                            value = limitInput,
                            onValueChange = {
                                limitInput = it
                                limitError = null
                                limitSaved = false
                            },
                            placeholder = "Amount in €",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        PillButton(
                            label = "Save",
                            isLoading = isSavingLimit,
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            onClick = {
                                val parsed = limitInput.text.toDoubleOrNull()
                                if (parsed == null || parsed <= 0.0) {
                                    limitError = "Enter a valid amount"
                                } else {
                                    scope.launch {
                                        isSavingLimit = true
                                        limitError = null
                                        val result = DailyLimitRepository.setDailyLimit(parsed)
                                        isSavingLimit = false
                                        result.onSuccess {
                                            limitSaved = true
                                        }.onFailure { e ->
                                            limitError = e.message ?: "Couldn't save"
                                        }
                                    }
                                }
                            }
                        )
                    }

                    limitError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = ErrorColor, fontSize = 13.sp)
                    }
                    if (limitSaved) {
                        Spacer(Modifier.height(8.dp))
                        Text("Saved", color = SuccessColor, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionLabel("Account")
            Spacer(Modifier.height(10.dp))
            SectionCard {
                Text("Signed in as", color = LiraTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = userEmail.ifBlank { "Unknown" },
                    color = LiraTextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = LiraBorder)
                Spacer(Modifier.height(18.dp))

                ChangePasswordDropdown(scope)

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = LiraBorder)
                Spacer(Modifier.height(18.dp))

                signOutError?.let {
                    Text(it, color = ErrorColor, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                deleteError?.let {
                    Text(it, color = ErrorColor, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(
                        label = "Sign Out",
                        isLoading = isSigningOut,
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                isSigningOut = true
                                signOutError = null
                                val result = AuthRepository.signOut()
                                isSigningOut = false
                                result.onSuccess {
                                    navController.navigate(Routes.LOGIN) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }.onFailure { e ->
                                    signOutError = e.message ?: "Couldn't sign out"
                                }
                            }
                        }
                    )
                    PillButton(
                        label = "Delete Account",
                        isLoading = false,
                        containerColor = ErrorColor.copy(alpha = 0.15f),
                        contentColor = ErrorColor,
                        modifier = Modifier.weight(1f),
                        onClick = { showDeleteConfirm = true }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = LiraTextSecondary.copy(alpha = 0.7f),
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.sp
    )
}

@Composable
private fun ChangePasswordDropdown(scope: CoroutineScope) {
    var expanded by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf(TextFieldValue("")) }
    var confirmPassword by remember { mutableStateOf(TextFieldValue("")) }
    var showPassword by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "chevron")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Change Password", color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                text = "⌄",
                color = LiraTextSecondary,
                fontSize = 18.sp,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(16.dp))

                LiraTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null; saved = false },
                    placeholder = "New password",
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                painter = painterResource(if (showPassword) R.drawable.ic_eye_off else R.drawable.ic_eye),
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = LiraTextSecondary
                            )
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                LiraTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null; saved = false },
                    placeholder = "Confirm new password",
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = ErrorColor, fontSize = 13.sp)
                }
                if (saved) {
                    Spacer(Modifier.height(8.dp))
                    Text("Password updated", color = SuccessColor, fontSize = 13.sp)
                }

                Spacer(Modifier.height(16.dp))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    PillButton(
                        label = "Save",
                        isLoading = isSaving,
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        onClick = {
                            when {
                                newPassword.text.length < 6 -> error = "Password must be at least 6 characters"
                                newPassword.text != confirmPassword.text -> error = "Passwords don't match"
                                else -> {
                                    scope.launch {
                                        isSaving = true
                                        error = null
                                        val result = AuthRepository.updatePassword(newPassword.text)
                                        isSaving = false
                                        result.onSuccess {
                                            saved = true
                                            newPassword = TextFieldValue("")
                                            confirmPassword = TextFieldValue("")
                                        }.onFailure { e ->
                                            error = e.message ?: "Couldn't update password"
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LiraBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun PillButton(
    label: String,
    isLoading: Boolean,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(containerColor)
            .clickable(enabled = !isLoading) { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = contentColor, strokeWidth = 2.dp)
        } else {
            Text(label, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LiraBackground,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("Delete account?", color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Text(
                text = "This permanently deletes your transactions, recurring entries, and daily limit. This can't be undone.",
                color = LiraTextSecondary,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isDeleting) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ErrorColor, strokeWidth = 2.dp)
                } else {
                    Text("Delete", color = ErrorColor, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text("Cancel", color = LiraTextSecondary)
            }
        }
    )
}
