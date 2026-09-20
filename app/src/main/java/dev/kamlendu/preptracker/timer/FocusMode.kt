package dev.kamlendu.preptracker.timer

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Silences the phone for the duration of a sitting — everything muted except calls.
 *
 * Do Not Disturb is a global, system-wide setting, so whatever it was before the session has to
 * come back after it. That promise has to survive the app dying: if the process is killed while a
 * sitting runs — an update, a force-stop, the system reclaiming memory — an in-memory copy of the
 * previous state goes with it and the phone stays silenced indefinitely. So the previous filter
 * and policy are written to disk the moment focus mode goes on, and [restoreIfOrphaned] puts them
 * back on the next launch if no session is running to own them.
 *
 * Calls are deliberately let through, plus repeat callers, so a genuine emergency that rings twice
 * gets through even from an unknown number.
 */
object FocusMode {
    private const val TAG = "FocusMode"
    private const val PREFS = "focus_mode"
    private const val K_ACTIVE = "active"
    private const val K_FILTER = "previous_filter"
    private const val K_CATEGORIES = "previous_categories"
    private const val K_CALL_SENDERS = "previous_call_senders"
    private const val K_MESSAGE_SENDERS = "previous_message_senders"

    fun isGranted(context: Context): Boolean =
        nm(context).isNotificationPolicyAccessGranted

    /** Opens the system screen where the user grants DND access; there is no runtime dialog. */
    fun requestAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun enable(context: Context) {
        val manager = nm(context)
        if (!manager.isNotificationPolicyAccessGranted) return
        val prefs = prefs(context)
        try {
            if (!prefs.getBoolean(K_ACTIVE, false)) {
                val policy = manager.notificationPolicy
                prefs.edit()
                    .putBoolean(K_ACTIVE, true)
                    .putInt(K_FILTER, manager.currentInterruptionFilter)
                    .putInt(K_CATEGORIES, policy.priorityCategories)
                    .putInt(K_CALL_SENDERS, policy.priorityCallSenders)
                    .putInt(K_MESSAGE_SENDERS, policy.priorityMessageSenders)
                    .apply()
            }
            manager.notificationPolicy = NotificationManager.Policy(
                NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
            )
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } catch (e: SecurityException) {
            Log.w(TAG, "could not enter focus mode", e)
        }
    }

    fun disable(context: Context) {
        val manager = nm(context)
        val prefs = prefs(context)
        if (!prefs.getBoolean(K_ACTIVE, false)) return
        if (!manager.isNotificationPolicyAccessGranted) {
            prefs.edit().clear().apply()
            return
        }
        try {
            manager.notificationPolicy = NotificationManager.Policy(
                prefs.getInt(K_CATEGORIES, 0),
                prefs.getInt(K_CALL_SENDERS, NotificationManager.Policy.PRIORITY_SENDERS_ANY),
                prefs.getInt(K_MESSAGE_SENDERS, NotificationManager.Policy.PRIORITY_SENDERS_ANY),
            )
            manager.setInterruptionFilter(
                prefs.getInt(K_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "could not leave focus mode", e)
        } finally {
            prefs.edit().clear().apply()
        }
    }

    /**
     * Called on launch: if focus mode is still recorded as on but nothing is timing, the session
     * that turned it on is gone and the phone should not still be silenced.
     */
    fun restoreIfOrphaned(context: Context) {
        if (!prefs(context).getBoolean(K_ACTIVE, false)) return
        if (TimerEngine.state.value.isActive) return
        Log.i(TAG, "focus mode outlived its session; restoring the previous setting")
        disable(context)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun nm(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
