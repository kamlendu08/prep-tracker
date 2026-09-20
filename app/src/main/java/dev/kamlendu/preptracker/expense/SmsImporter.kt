package dev.kamlendu.preptracker.expense

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import dev.kamlendu.preptracker.PrepApp
import dev.kamlendu.preptracker.data.Expense
import dev.kamlendu.preptracker.data.ExpenseSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Back-fills spending from SMS already sitting in the inbox.
 *
 * Run once after granting the permission, and again whenever the phone was offline or the app was
 * force-stopped and the live receiver missed messages. Duplicates are impossible to create here —
 * the fingerprint collides with whatever was already captured.
 */
object SmsImporter {

    data class Result(val scanned: Int, val imported: Int, val duplicatesRemoved: Int = 0)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun importSince(context: Context, sinceMillis: Long): Result = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext Result(0, 0)

        var scanned = 0
        var imported = 0
        val projection = arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                scanned++
                val body = cursor.getString(bodyIndex) ?: continue
                val date = cursor.getLong(dateIndex)
                if (ExpenseCapture.capture(context, body, date, ExpenseSource.SMS)) imported++
            }
        }
        Result(scanned, imported)
    }

    /** Default back-fill window: a month is enough to make the dashboard useful on day one. */
    suspend fun importLastMonth(context: Context): Result {
        val result = importSince(context, System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        recategorise(context)
        val removed = removeDuplicates(context)
        return result.copy(duplicatesRemoved = removed)
    }

    /**
     * Sweeps out rows that describe a payment already recorded by another message.
     *
     * The capture path now refuses duplicates as they arrive, but rows stored before that check
     * existed — or before a parser change altered a fingerprint — are still sitting in the
     * database, and a wrong total does not fix itself. Hand-entered and hand-corrected rows are
     * never the ones discarded.
     *
     * @return how many rows were removed.
     */
    suspend fun removeDuplicates(context: Context): Int = withContext(Dispatchers.IO) {
        val dao = (context.applicationContext as PrepApp).container.expenseDao
        val kept = mutableListOf<Expense>()
        var removed = 0

        for (row in dao.allByTime()) {
            val clash = kept.firstOrNull { Deduper.isSameTransaction(it, row) }
            if (clash == null) {
                kept += row
                continue
            }
            val winner = Deduper.preferred(clash, row)
            val loser = if (winner === clash) row else clash
            dao.delete(loser.id)
            removed++
            if (winner !== clash) {
                kept.remove(clash)
                kept += winner
            }
        }
        removed
    }

    /**
     * Re-reads already-stored spends with the current parser — category and payee both.
     *
     * The category rules keep improving as new message shapes turn up, and without this a row
     * imported under an older rule keeps its wrong category forever — the duplicate check means a
     * re-import silently skips it. Rows whose category was corrected by hand are never touched.
     *
     * @return how many rows changed category.
     */
    suspend fun recategorise(context: Context): Int = withContext(Dispatchers.IO) {
        val dao = (context.applicationContext as PrepApp).container.expenseDao
        var changed = 0
        for (expense in dao.unlockedWithText()) {
            val raw = expense.rawText ?: continue
            val parsed = TxnParser.parseText(raw, expense.occurredAt) ?: continue
            if (parsed.category.id != expense.category || parsed.merchant != expense.merchant) {
                dao.refreshParsed(expense.id, parsed.category.id, parsed.merchant)
                changed++
            }
        }
        changed
    }
}
