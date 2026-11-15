package com.app.lira.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.lira.MainActivity
import com.app.lira.R
import com.app.lira.widget.WidgetActions

/** Lira's few, quiet notifications. One channel per kind so each can be muted from system settings too. */
object LiraNotifications {
    const val CH_REMINDER = "reminders"
    const val CH_LIMIT = "limits"
    const val CH_RECURRING = "recurring"
    const val CH_SUMMARY = "summary"

    private const val ID_EVENING = 101
    private const val ID_LIMIT = 102
    private const val ID_RECURRING = 103
    private const val ID_WEEKLY = 104

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel(CH_REMINDER, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "An evening nudge when nothing was logged today." },
            NotificationChannel(CH_LIMIT, "Limit alerts", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "When you get close to, or go over, today's limit." },
            NotificationChannel(CH_RECURRING, "Upcoming recurring", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "The day before a recurring payment or income is logged." },
            NotificationChannel(CH_SUMMARY, "Weekly summary", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Monday morning: how last week went." }
        ).forEach { mgr.createNotificationChannel(it) }
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, quickAdd: Boolean, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (quickAdd) putExtra(WidgetActions.EXTRA_QUICK_ADD, true)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun post(context: Context, id: Int, channel: String, title: String, text: String, quickAdd: Boolean) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF5D926D.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context, quickAdd, id))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    fun evening(context: Context, title: String, text: String) = post(context, ID_EVENING, CH_REMINDER, title, text, quickAdd = true)
    fun limit(context: Context, title: String, text: String) = post(context, ID_LIMIT, CH_LIMIT, title, text, quickAdd = false)
    fun recurring(context: Context, title: String, text: String) = post(context, ID_RECURRING, CH_RECURRING, title, text, quickAdd = false)
    fun weekly(context: Context, title: String, text: String) = post(context, ID_WEEKLY, CH_SUMMARY, title, text, quickAdd = false)
}
