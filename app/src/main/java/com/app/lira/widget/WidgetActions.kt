package com.app.lira.widget

import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavController
import com.app.lira.navigation.Routes

/**
 * Bridges the widget's "scan receipt" tap into the running Compose UI. [navController] is set
 * once by NavGraph and used to jump straight to Add Payed if the app was already open elsewhere;
 * [pendingScanReceipt] is the one-shot flag AddPayedScreen watches to actually launch the camera.
 */
object WidgetActions {
    var navController: NavController? = null
    var pendingScanReceipt = mutableStateOf(false)

    fun requestScanReceipt() {
        navController?.navigate(Routes.ADD_PAYED) {
            popUpTo(Routes.ADD_PAYED) { inclusive = true }
            launchSingleTop = true
        }
        pendingScanReceipt.value = true
    }
}
