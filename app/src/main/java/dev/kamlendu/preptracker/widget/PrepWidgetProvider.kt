package dev.kamlendu.preptracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dev.kamlendu.preptracker.MainActivity
import dev.kamlendu.preptracker.R
import dev.kamlendu.preptracker.appContainer
import dev.kamlendu.preptracker.data.todayKey
import dev.kamlendu.preptracker.timer.TimerEngine
import dev.kamlendu.preptracker.timer.formatHoursMinutes
import dev.kamlendu.preptracker.ui.formatRupees
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen widget: how far into today's study target you are, and how much of the day's budget
 * is left.
 *
 * Android only refreshes a widget every 30 minutes on its own, which would be useless for a clock
 * that changes by the minute. So [WidgetUpdater.refresh] is called from every place that changes
 * the underlying numbers — a logged sitting, a captured spend, a changed target — and the widget
 * is repainted then.
 */
class PrepWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        render(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetUpdater.ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            render(context, manager, manager.getAppWidgetIds(componentOf(context)))
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val appContext = context.applicationContext
        // A BroadcastReceiver must not block, and the totals come from Room.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = appContext.appContainer
                val day = todayKey()
                val settings = container.settings.settings.first()
                // A sitting in progress counts towards the figure: glancing at the widget an hour
                // into a session and seeing only what was logged *before* it would read as broken.
                val running = TimerEngine.state.value.let { if (it.isActive) it.elapsedMs() else 0L }
                val studiedMs = container.studyDao.totalForDayOnce(day) + running
                val spentPaise = container.expenseDao.totalForDayOnce(day)

                val targetMs = settings.dailyTargetMinutes * 60_000L
                val remainingMs = (targetMs - studiedMs).coerceAtLeast(0)
                val remainingPaise = settings.dailyLimitPaise - spentPaise

                val views = RemoteViews(appContext.packageName, R.layout.widget_prep).apply {
                    setTextViewText(R.id.study_value, formatHoursMinutes(studiedMs))
                    setTextViewText(
                        R.id.study_remaining,
                        if (targetMs <= 0) {
                            "no target set"
                        } else if (remainingMs == 0L) {
                            "target reached"
                        } else {
                            "${formatHoursMinutes(remainingMs)} to go"
                        },
                    )
                    setProgressBar(R.id.study_bar, 100, percent(studiedMs, targetMs), false)

                    setTextViewText(R.id.spend_value, formatRupees(spentPaise))
                    setTextViewText(
                        R.id.spend_remaining,
                        if (remainingPaise >= 0) {
                            "${formatRupees(remainingPaise)} left"
                        } else {
                            "${formatRupees(-remainingPaise)} over"
                        },
                    )
                    setProgressBar(R.id.spend_bar, 100, percent(spentPaise, settings.dailyLimitPaise), false)
                    setProgressBar(R.id.spend_bar_over, 100, 100, false)
                    // Over budget swaps in the red bar. Only ever one of the two is visible.
                    val over = remainingPaise < 0
                    setViewVisibility(R.id.spend_bar, if (over) View.GONE else View.VISIBLE)
                    setViewVisibility(R.id.spend_bar_over, if (over) View.VISIBLE else View.GONE)

                    setOnClickPendingIntent(R.id.widget_root, openApp(appContext))
                }
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Anything above zero gets at least a visible nub: two minutes of an eight-hour target rounds
     * to 0%, and an empty bar reads as "nothing recorded" rather than "just started".
     */
    private fun percent(value: Long, outOf: Long): Int {
        if (outOf <= 0L || value <= 0L) return 0
        return ((value * 100) / outOf).toInt().coerceIn(2, 100)
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun componentOf(context: Context) =
        ComponentName(context, PrepWidgetProvider::class.java)
}

object WidgetPinner {
    /**
     * Asks the launcher to drop the widget on the home screen, rather than making you hunt for it
     * in the widget drawer. The launcher shows its own confirmation — this cannot place anything
     * on its own.
     *
     * @return false when the launcher does not support pinning, in which case the widget drawer is
     * the only route.
     */
    fun requestPin(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(
            ComponentName(context, PrepWidgetProvider::class.java),
            null,
            null,
        )
    }
}

object WidgetUpdater {
    const val ACTION_REFRESH = "dev.kamlendu.preptracker.WIDGET_REFRESH"

    /** Safe to call from anywhere, including when no widget has been placed. */
    fun refresh(context: Context) {
        context.applicationContext.sendBroadcast(
            Intent(context.applicationContext, PrepWidgetProvider::class.java)
                .setAction(ACTION_REFRESH)
        )
    }
}
