package com.app.lira.notify

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.LiraSettings
import com.app.lira.data.local.DataMode
import com.app.lira.data.local.LocalDb
import com.app.lira.data.repository.TransactionCategoryRef
import com.app.lira.data.repository.TransactionDetail
import com.app.lira.ui.screens.more.MoreData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline mode's safety net: writes every offline entry as "Lira backup.csv" into a folder the
 * user picked (any provider — local storage, a USB drive, or Google Drive through the system
 * picker). Same format as More → Export, so More → Import restores it.
 */
object OfflineBackup {
    private const val TAG = "OfflineBackup"
    const val FILE_NAME = "Lira backup.csv"

    /** CSV of everything stored offline (also used to move offline data into a new account). */
    fun localCsv(): String {
        val names = LocalDb.categories().associate { it.id to it.name }
        val rows = LocalDb.transactions().map { t ->
            TransactionDetail(
                id = t.id, amount = t.amount, type = t.type, source = t.source, description = t.description, date = t.date,
                category = t.category_id?.let { id -> names[id]?.let { TransactionCategoryRef(it) } },
                recurring_id = t.recurring_id, trip_id = t.trip_id, currency = t.currency
            )
        }
        return MoreData.toCsv(rows, CurrencyPreference.code)
    }

    /** True when offline data changed since the last backup (or none was made yet). */
    fun isDue(): Boolean =
        DataMode.isLocal && LiraSettings.backupFolder != null &&
            (LiraSettings.lastBackupAt == 0L || LocalDb.lastModified() > LiraSettings.lastBackupAt)

    /** Writes the backup now. */
    suspend fun run(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val folderUri = LiraSettings.backupFolder ?: error("Pick a folder first.")
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                ?: error("That folder isn't available any more. Pick it again.")
            if (!folder.canWrite()) error("Lira can't write to that folder any more. Pick it again.")
            val file = folder.findFile(FILE_NAME) ?: folder.createFile("text/csv", FILE_NAME)
                ?: error("Couldn't create the backup file.")
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(localCsv().toByteArray()) }
                ?: error("Couldn't write the backup file.")
            LiraSettings.setLastBackup(System.currentTimeMillis())
        }.onFailure { Log.w(TAG, "Backup failed", it) }
    }
}
