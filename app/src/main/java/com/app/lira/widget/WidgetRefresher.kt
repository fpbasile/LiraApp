package com.app.lira.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll

/**
 * Lets the data layer nudge the home screen widget to refresh after a payed-transaction mutation,
 * without giving TransactionRepository a hard dependency on an Activity/Application instance.
 */
object WidgetRefresher {
    private const val TAG = "LiraWidgetRefresher"
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        Log.d(TAG, "init() context set")
    }

    /** Best-effort — a failed widget refresh should never fail the operation that triggered it. */
    suspend fun refresh() {
        val context = appContext
        if (context == null) {
            Log.w(TAG, "refresh() called before init() — appContext is null, skipping")
            return
        }
        runCatching { LiraWidget().updateAll(context) }
            .onSuccess { Log.d(TAG, "refresh() updateAll succeeded") }
            .onFailure { e -> Log.w(TAG, "refresh() updateAll failed", e) }
    }
}
