package com.app.lira.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.lira.R
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.AuthRepository
import com.app.lira.ui.components.LiraTextField
import com.app.lira.ui.theme.LiraBackground
import com.app.lira.ui.theme.LiraTextSecondary
import kotlinx.coroutines.launch


@Composable
fun LoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    // 1. States
    var email by remember { mutableStateOf(TextFieldValue("")) }
    var password by remember { mutableStateOf(TextFieldValue("")) }
    var isLoginMode by remember { mutableStateOf(true) } // Toggle between Login and Sign Up
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiraBackground), 
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp)
                .verticalScroll(rememberScrollState()), 
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // --- Logo ---
            Image(
                painter = painterResource(id = R.drawable.lira_logo),
                contentDescription = "Lira Logo",
                modifier = Modifier
                    .size(300.dp) // Adjusted size for better layout balance
                    .padding(bottom = 32.dp),
                contentScale = ContentScale.Fit
            )

            Text(
                text = if (isLoginMode) "Welcome Back" else "Create Account",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --- Input Fields  ---
            LiraTextField(
                value = email,
                onValueChange = { email = it},
                placeholder = "Email",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LiraTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Password",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            // --- Error Message ---
            errorMessage?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Color.Red, fontSize = 14.sp, textAlign = TextAlign.Center)
            }

            // --- Action Button ---
            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null

                            // Call Sign In or Sign Up based on mode
                            val result = if (isLoginMode) {
                                AuthRepository.signIn(email.text, password.text)
                            } else {
                                AuthRepository.signUp(email.text, password.text)
                            }

                            isLoading = false

                            if (result.isSuccess) {
                                navController.navigate("add_payed") {
                                    popUpTo("login") { inclusive = true }
                                }
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "An error occurred"
                            }
                        }
                    },
                    modifier = Modifier.width(180.dp).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (isLoginMode) "LOG IN" else "SIGN UP",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- Toggle Bottom Text ---
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = buildAnnotatedString {
                    append(if (isLoginMode) "No account? " else "Already have an account? ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                        append(if (isLoginMode) "Create one" else "Log in")
                    }
                },
                color = LiraTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.clickable {
                    isLoginMode = !isLoginMode // Toggles the UI mode
                    errorMessage = null
                }
            )
        }
    }
}