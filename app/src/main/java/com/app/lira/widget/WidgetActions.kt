package com.app.lira.widget

import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavController
import com.app.lira.navigation.Routes

/**
 * Bridges the widget's "scan receipt" tap into the running Compose UI. [navController] is set
 * once by NavGraph and used to jump straight to Add Payed if the app was already open elsewhere;
 * [pendingScanReceipt] is the one-shot flag HomeScreen watches to actually launch the camera.
 *
 * The widget's "Add expense" / quick-add tap launches MainActivity with [EXTRA_QUICK_ADD]; the
 * activity should call [requestQuickAdd], which opens Home (the keypad) and raises the one-shot
 * [pendingQuickAdd] flag for the Home screen to consume (e.g. focus the keypad, then reset it).
 */
object WidgetActions {
    /** Intent extra (boolean) set by the widget's quick-add buttons. */
    const val EXTRA_QUICK_ADD = "com.app.lira.EXTRA_QUICK_ADD"

    var navController: NavController? = null
    var pendingScanReceipt = mutableStateOf(false)
    var pendingQuickAdd = mutableStateOf(false)

    fun requestScanReceipt() {
        navController?.navigate(Routes.ADD_PAYED) {
            popUpTo(Routes.ADD_PAYED) { inclusive = true }
            launchSingleTop = true
        }
        pendingScanReceipt.value = true
    }

    fun requestQuickAdd() {
        navController?.navigate(Routes.ADD_PAYED) {
            popUpTo(Routes.ADD_PAYED) { inclusive = true }
            launchSingleTop = true
        }
        pendingQuickAdd.value = true
    }
}
