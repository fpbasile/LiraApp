package com.app.lira.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.ui.screens.LoginScreen
import com.app.lira.ui.screens.AddPayedScreen
import com.app.lira.ui.screens.TransactionsScreen
import com.app.lira.ui.theme.LiraBackground
import io.github.jan.supabase.auth.auth

object Routes {
    const val LOGIN = "login"
    const val ADD_PAYED = "add_payed"
    const val TRANSACTIONS = "transactions"
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NavGraph(navController: NavHostController) {

    var isCheckingSession by remember { mutableStateOf(true) }
    var startDestination by remember { mutableStateOf(Routes.LOGIN) }

    // This runs once when the app starts
    LaunchedEffect(Unit) {
        // Wait for the Auth plugin to finish restoring the persisted session
        SupabaseClient.client.auth.awaitInitialization()
        val session = SupabaseClient.client.auth.currentSessionOrNull()
        if (session != null) {
            startDestination = Routes.ADD_PAYED
        }
        isCheckingSession = false
    }

    if (isCheckingSession) {
        // Show a blank screen or a simple loading splash while checking
        Box(Modifier.fillMaxSize().background(LiraBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    } else {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(Routes.LOGIN) { LoginScreen(navController) }
            composable(Routes.ADD_PAYED) { AddPayedScreen(navController) }
            composable(Routes.TRANSACTIONS) { TransactionsScreen(navController) }
        }
    }
}