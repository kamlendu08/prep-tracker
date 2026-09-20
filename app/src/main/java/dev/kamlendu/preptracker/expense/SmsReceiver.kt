package dev.kamlendu.preptracker.expense

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dev.kamlendu.preptracker.data.ExpenseSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Catches bank messages as they arrive.
 *
 * Long bank SMS are split into parts by the network, so the parts are stitched back together per
 * sender before parsing — half a message parses to half a number, or to nothing at all.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        val timestamp = messages.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        if (body.isBlank()) return

        // goAsync() buys this receiver the few milliseconds the database insert needs; without it
        // the process can be torn down the instant onReceive returns.
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ExpenseCapture.capture(appContext, body, timestamp, ExpenseSource.SMS)
            } finally {
                pending.finish()
            }
        }
    }
}
