package com.app.lira.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * User settings for notifications and the offline auto-backup, plus the "already sent today"
 * bookkeeping the background worker needs. Observable so the More screen updates live.
 */
object LiraSettings {
    private const val PREFS = "lira_settings"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        eveningReminder = bool("evening", true)
        eveningHour = prefs?.getInt("evening_hour", 21) ?: 21
        limitAlerts = bool("limit", true)
        recurringHeadsUp = bool("recurring", true)
        weeklySummary = bool("weekly", true)
        backupFolder = prefs?.getString("backup_folder", null)
        lastBackupAt = prefs?.getLong("last_backup_at", 0L) ?: 0L
    }

    private fun bool(key: String, default: Boolean) = prefs?.getBoolean(key, default) ?: default
    private fun put(block: SharedPreferences.Editor.() -> Unit) { prefs?.edit()?.apply(block)?.apply() }

    // ── Notifications ──
    var eveningReminder by mutableStateOf(true); private set
    var eveningHour by mutableStateOf(21); private set
    var limitAlerts by mutableStateOf(true); private set
    var recurringHeadsUp by mutableStateOf(true); private set
    var weeklySummary by mutableStateOf(true); private set

    fun setEvening(on: Boolean) { eveningReminder = on; put { putBoolean("evening", on) } }
    fun setEveningAt(hour: Int) { eveningHour = hour; put { putInt("evening_hour", hour) } }
    fun setLimit(on: Boolean) { limitAlerts = on; put { putBoolean("limit", on) } }
    fun setRecurring(on: Boolean) { recurringHeadsUp = on; put { putBoolean("recurring", on) } }
    fun setWeekly(on: Boolean) { weeklySummary = on; put { putBoolean("weekly", on) } }

    val anyNotification: Boolean get() = eveningReminder || limitAlerts || recurringHeadsUp || weeklySummary

    /** "Sent on this day" markers (ISO date / week key), so each notification fires at most once. */
    fun sentOn(kind: String): String? = prefs?.getString("sent_$kind", null)
    fun markSent(kind: String, key: String) = put { putString("sent_$kind", key) }

    // ── Offline auto-backup ──
    /** Tree URI of the folder chosen for backups (may be a Google Drive folder), or null when off. */
    var backupFolder by mutableStateOf<String?>(null); private set
    var lastBackupAt by mutableStateOf(0L); private set

    fun chooseBackupFolder(uri: String?) { backupFolder = uri; put { putString("backup_folder", uri) } }
    fun setLastBackup(millis: Long) { lastBackupAt = millis; put { putLong("last_backup_at", millis) } }
}
