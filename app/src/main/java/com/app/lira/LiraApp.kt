package com.app.lira

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.app.lira.data.CurrencyPreference
import com.app.lira.navigation.NavGraph
import com.app.lira.ui.theme.LiraTheme
import com.app.lira.widget.WidgetActions
import com.app.lira.widget.WidgetRefresher


class MainActivity : ComponentActivity() {

    companion object {
        /** Extra set by [com.app.lira.widget.LiraWidget]'s camera button to jump straight to the scan flow. */
        const val EXTRA_SCAN_RECEIPT = "com.app.lira.EXTRA_SCAN_RECEIPT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CurrencyPreference.init(this)
        WidgetRefresher.init(this)
        handleIntent(intent)
        setContent {
            LiraApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_SCAN_RECEIPT, false)) {
            WidgetActions.requestScanReceipt()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun LiraApp() {
    LiraTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            val navController = rememberNavController()
            NavGraph(navController = navController)
        }
    }
}
