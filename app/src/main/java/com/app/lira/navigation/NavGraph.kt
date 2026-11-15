package com.app.lira.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.app.lira.R
import com.app.lira.data.AppPrefs
import com.app.lira.data.HomeDataCache
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.SpendingLimitRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.data.repository.toUiCategories
import com.app.lira.ui.components.lira.FloatingNavBar
import com.app.lira.ui.components.lira.NavTab
import com.app.lira.ui.screens.auth.AuthScreen
import com.app.lira.ui.screens.auth.OnboardingScreen
import com.app.lira.ui.screens.home.HomeScreen
import com.app.lira.ui.screens.insights.InsightsScreen
import com.app.lira.ui.screens.more.CategoriesScreen
import com.app.lira.ui.screens.more.EditEntryScreen
import com.app.lira.ui.screens.more.MoreScreen
import com.app.lira.ui.screens.more.RecurringEditScreen
import com.app.lira.ui.screens.more.RecurringListScreen
import com.app.lira.ui.screens.travel.ConverterScreen
import com.app.lira.ui.screens.travel.TripScreen
import com.app.lira.ui.screens.travel.TripStartScreen
import com.app.lira.ui.screens.travel.TripsScreen
import com.app.lira.ui.theme.LiraColors
import com.app.lira.widget.WidgetActions
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"

    // Main tabs (floating nav). Travel is a mode, not a tab.
    const val HOME = "home"
    const val INSIGHTS = "insights"
    const val MORE = "more"

    const val MANAGE_CATEGORIES = "manage_categories"
    const val RECURRING = "recurring"
    const val RECURRING_EDIT = "recurring_edit?id={id}"
    fun recurringEdit(id: Int? = null) = if (id == null) "recurring_edit" else "recurring_edit?id=$id"

    const val EDIT_TRANSACTION = "edit_transaction/{id}"
    fun editTransaction(id: Int) = "edit_transaction/$id"

    const val CURRENCY_CONVERTER = "currency_converter"
    const val TRIPS = "trips"
    const val TRIP_START = "trip_start"
    const val TRIP_DETAIL = "trip_detail/{tripId}"
    fun tripDetail(tripId: Int) = "trip_detail/$tripId"

    // Legacy names still referenced by older code.
    const val ADD_PAYED = HOME
    const val CALENDAR = INSIGHTS
    const val SETTINGS = MORE
    const val TRANSACTIONS = INSIGHTS
    const val TRAVELLING = TRIPS
}

private val mainTabs = listOf(
    NavTab(Routes.HOME, "Home", Icons.Outlined.Home),
    NavTab(Routes.INSIGHTS, "Insights", Icons.Outlined.Insights),
    NavTab(Routes.MORE, "More", Icons.Outlined.MoreHoriz)
)

/** Switch between main tabs keeping one copy of each on the back stack. */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NavGraph(navController: NavHostController) {

    // Lets the widget's buttons jump straight into Home's quick-add / scan if the app is running.
    SideEffect { WidgetActions.navController = navController }

    var isCheckingSession by remember { mutableStateOf(true) }
    var startDestination by remember { mutableStateOf(Routes.LOGIN) }

    LaunchedEffect(Unit) {
        SupabaseClient.client.auth.awaitInitialization()
        val session = SupabaseClient.client.auth.currentSessionOrNull()
        if (session != null || com.app.lira.data.local.DataMode.isLocal) {
            // Post recurring items that came due while the app was closed, before prefetching totals.
            // Capped so a slow network never holds the splash: Home fetches its own data anyway.
            // Not under the timeout: cancelling it mid-way could post an occurrence twice.
            RecurringRepository.postDueOccurrences()
            kotlinx.coroutines.withTimeoutOrNull(1500) {
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
            }
            startDestination = if (AppPrefs.onboardingDone) Routes.HOME else Routes.ONBOARDING
        }
        isCheckingSession = false
    }

    if (isCheckingSession) {
        SplashScreen()
        return
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showNav = currentRoute in mainTabs.map { it.route }

    Box(Modifier.fillMaxSize().background(LiraColors.Base)) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeIn(androidx.compose.animation.core.tween(220)) },
            exitTransition = { fadeOut(androidx.compose.animation.core.tween(160)) },
            popEnterTransition = { fadeIn(androidx.compose.animation.core.tween(220)) },
            popExitTransition = { fadeOut(androidx.compose.animation.core.tween(160)) }
        ) {
            composable(Routes.LOGIN) { AuthScreen(navController) }
            composable(Routes.ONBOARDING) { OnboardingScreen(navController) }
            composable(Routes.HOME) { HomeScreen(navController) }
            composable(Routes.INSIGHTS) { InsightsScreen(navController) }
            composable(Routes.MORE) { MoreScreen(navController) }
            composable(Routes.MANAGE_CATEGORIES) { CategoriesScreen(navController) }
            composable(Routes.RECURRING) { RecurringListScreen(navController) }
            composable(
                Routes.RECURRING_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                RecurringEditScreen(navController, entry.arguments?.getString("id")?.toIntOrNull())
            }
            composable(Routes.EDIT_TRANSACTION, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                EditEntryScreen(navController, entry.arguments?.getInt("id") ?: return@composable)
            }
            composable(Routes.CURRENCY_CONVERTER) { ConverterScreen(navController) }
            composable(Routes.TRIPS) { TripsScreen(navController) }
            composable(Routes.TRIP_START) { TripStartScreen(navController) }
            composable(Routes.TRIP_DETAIL, arguments = listOf(navArgument("tripId") { type = NavType.IntType })) { entry ->
                TripScreen(navController, entry.arguments?.getInt("tripId") ?: return@composable)
            }
        }

        AnimatedVisibility(
            visible = showNav,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            FloatingNavBar(mainTabs, currentRoute ?: Routes.HOME, onSelect = { route ->
                if (route == currentRoute) {
                    if (route == Routes.HOME) HomeEvents.clearKeypad()
                } else navController.navigateToTab(route)
            })
        }
    }
}

/** Cross-screen signals for Home (tapping Home while on Home clears the keypad). */
object HomeEvents {
    var clearRequests by mutableStateOf(0)
        private set

    fun clearKeypad() { clearRequests++ }

    /** Bumped after bulk changes (CSV import) so screens with session caches reload everything. */
    var dataVersion = 0
        private set

    fun dataChanged() { dataVersion++ }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(LiraColors.Base), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.lira_logo),
            contentDescription = "Lira",
            modifier = Modifier.height(48.dp)
        )
    }
}
