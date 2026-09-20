package dev.kamlendu.preptracker.expense

import android.content.Context
import android.util.Log
import dev.kamlendu.preptracker.PrepApp
import dev.kamlendu.preptracker.data.ExpenseSource
import dev.kamlendu.preptracker.widget.WidgetUpdater
import kotlinx.coroutines.flow.first

/**
 * The single funnel every automatic expense goes through — SMS, notification, or back-fill.
 *
 * Keeping it in one place means the duplicate check, the auto-capture switch and the parser's
 * conservatism apply identically no matter which pipe the message arrived on.
 */
object ExpenseCapture {
    private const val TAG = "ExpenseCapture"

    /** @return true when a new row was written (false = not a debit, or already recorded). */
    suspend fun capture(context: Context, text: String, occurredAt: Long, source: ExpenseSource): Boolean {
        val container = (context.applicationContext as PrepApp).container
        if (!container.settings.settings.first().autoCaptureEnabled) return false

        val expense = TxnParser.parse(text, occurredAt, source) ?: return false

        // The fingerprint's unique index only catches duplicates whose texts agree. One purchase
        // can produce several messages that agree on nothing but the amount, so every candidate is
        // also checked against what is already stored around that moment.
        val nearby = container.expenseDao.nearbyWithAmount(
            expense.amountPaise,
            expense.occurredAt - Deduper.LOOSE_WINDOW_MS,
            expense.occurredAt + Deduper.LOOSE_WINDOW_MS,
        )
        if (nearby.any { Deduper.isSameTransaction(it, expense) }) {
            Log.d(TAG, "skipped ${expense.amountPaise}p from ${source.id}: already recorded")
            return false
        }

        val id = container.expenseDao.insert(expense)
        // insert() returns -1 when the unique fingerprint collided.
        val inserted = id != -1L
        if (inserted) {
            Log.d(TAG, "captured ${expense.amountPaise}p from ${source.id}")
            WidgetUpdater.refresh(context)
        }
        return inserted
    }
}
