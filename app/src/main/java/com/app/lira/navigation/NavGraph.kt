package com.app.lira.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.app.lira.R
import com.app.lira.data.HomeDataCache
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.SpendingLimitRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.data.repository.toUiCategories
import com.app.lira.ui.screens.LoginScreen
import com.app.lira.ui.screens.AddPayedScreen
import com.app.lira.ui.screens.CalendarScreen
import com.app.lira.ui.screens.ManageCategoriesScreen
import com.app.lira.ui.screens.RecurringScreen
import com.app.lira.ui.screens.SettingsScreen
import com.app.lira.ui.screens.TransactionsScreen
import com.app.lira.ui.theme.LiraBackground
import com.app.lira.ui.theme.LiraTextSecondary
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val ADD_PAYED = "add_payed"
    const val TRANSACTIONS = "transactions"
    const val SETTINGS = "settings"
    const val MANAGE_CATEGORIES = "manage_categories"
    const val RECURRING = "recurring"
    const val CALENDAR = "calendar"
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
            // Post any recurring payments/income that came due while the app was closed, before
            // prefetching totals below, so they're reflected in today/week/month sums right away.
            RecurringRepository.postDueOccurrences()

            // Prefetch the home screen's data in parallel while the splash is still up,
            // so AddPayedScreen's first frame already has real data instead of showing
            // empty/zero state for a moment while its own fetch is in flight.
            coroutineScope {
                launch { CategoryRepository.getCategories().onSuccess { HomeDataCache.categories = it.toUiCategories() } }
                launch { TransactionRepository.getTodaySpent().onSuccess { HomeDataCache.todaySpent = it } }
                launch { TransactionRepository.getTodayReceived().onSuccess { HomeDataCache.todayReceived = it } }
                launch { SpendingLimitRepository.getDailyLimit().onSuccess { HomeDataCache.dailyLimit = it } }
                launch { TransactionRepository.getWeekSpent().onSuccess { HomeDataCache.weekSpent = it } }
                launch { TransactionRepository.getMonthSpent().onSuccess { HomeDataCache.monthSpent = it } }
                launch { SpendingLimitRepository.getLimit(SpendingLimitRepository.PERIOD_WEEKLY).onSuccess { HomeDataCache.weekLimit = it } }
                launch { SpendingLimitRepository.getLimit(SpendingLimitRepository.PERIOD_MONTHLY).onSuccess { HomeDataCache.monthLimit = it } }
            }
            startDestination = Routes.ADD_PAYED
        }
        isCheckingSession = false
    }

    if (isCheckingSession) {
        SplashScreen()
    } else {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(Routes.LOGIN) { LoginScreen(navController) }
            composable(Routes.ADD_PAYED) { AddPayedScreen(navController) }
            composable(Routes.TRANSACTIONS) { TransactionsScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.MANAGE_CATEGORIES) { ManageCategoriesScreen(navController) }
            composable(Routes.RECURRING) { RecurringScreen(navController) }
            composable(Routes.CALENDAR) { CalendarScreen(navController) }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(LiraBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.lira_logo),
                contentDescription = "Lira",
                modifier = Modifier.height(64.dp)
            )
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = LiraTextSecondary,
                strokeWidth = 2.dp
            )
        }
    }
}