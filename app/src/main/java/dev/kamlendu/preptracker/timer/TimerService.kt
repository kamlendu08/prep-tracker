package dev.kamlendu.preptracker.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import dev.kamlendu.preptracker.MainActivity
import dev.kamlendu.preptracker.PrepApp
import dev.kamlendu.preptracker.R
import dev.kamlendu.preptracker.data.StudyActivity
import dev.kamlendu.preptracker.sync.SyncEngine
import dev.kamlendu.preptracker.widget.WidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps a study session alive outside the app.
 *
 * The stopwatch itself is arithmetic on [TimerEngine]; this service exists so Android treats a
 * running session as work in progress — the notification cannot be swiped away, the process is
 * not frozen mid-sitting, and pause/stop are reachable from the shade without unlocking into the
 * app. It is also the one place that toggles Do Not Disturb, so focus mode can never outlive the
 * session that turned it on.
 */
class TimerService : LifecycleService() {

    companion object {
        const val ACTION_START = "dev.kamlendu.preptracker.START"
        const val ACTION_PAUSE = "dev.kamlendu.preptracker.PAUSE"
        const val ACTION_RESUME = "dev.kamlendu.preptracker.RESUME"
        const val ACTION_STOP = "dev.kamlendu.preptracker.STOP"
        const val ACTION_ENSURE = "dev.kamlendu.preptracker.ENSURE"
        const val ACTION_RESUME_LOGGED = "dev.kamlendu.preptracker.RESUME_LOGGED"
        const val EXTRA_ACTIVITY = "activity"
        const val EXTRA_SESSION_ID = "session_id"

        private const val CHANNEL_ID = "study_timer"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, activity: StudyActivity) =
            send(context, ACTION_START) { it.putExtra(EXTRA_ACTIVITY, activity.id) }

        /**
         * Re-attaches the service to a session that is already running.
         *
         * An app update, a force-stop or a low-memory kill takes the service down while the
         * session lives on in storage — and then the clock would keep counting on screen with no
         * notification, no focus mode and none of the auto-pause triggers. Called whenever the UI
         * comes to the foreground, which is also the only moment Android reliably permits starting
         * a foreground service.
         */
        fun ensure(context: Context) {
            if (!TimerEngine.state.value.isActive) return
            runCatching { send(context, ACTION_ENSURE) }
        }

        /** Picks an already-logged sitting back up instead of starting a new one. */
        fun resumeLogged(context: Context, sessionId: Long) =
            send(context, ACTION_RESUME_LOGGED) { it.putExtra(EXTRA_SESSION_ID, sessionId) }

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)

        private fun send(context: Context, action: String, block: (Intent) -> Unit = {}) {
            val intent = Intent(context, TimerService::class.java).setAction(action).also(block)
            context.startForegroundService(intent)
        }
    }

    /**
     * Locking the phone means the sitting is over for now — pressing power is the most natural
     * "I'm stepping away" gesture there is, and counting a dark phone as study time would inflate
     * the very number the app exists to report honestly.
     */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            if (!TimerEngine.state.value.running) return
            TimerEngine.pause()
            startForeground(buildNotification())
        }
    }

    /**
     * A call pauses the clock too.
     *
     * Read from the audio mode rather than the telephony call state: the audio mode needs no
     * permission at all, and it catches WhatsApp and other VoIP calls as well as the dialler.
     */
    private val callListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AudioManager.OnModeChangedListener { mode ->
            val inCall = mode == AudioManager.MODE_RINGTONE ||
                mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION
            if (inCall && TimerEngine.state.value.running) {
                TimerEngine.pause()
                startForeground(buildNotification())
            }
        }
    } else {
        null
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val listener = callListener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && listener != null) {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            audio.addOnModeChangedListener(mainExecutor, listener)
        }
        startHeartbeat()
    }

    /**
     * Marks the session as still genuinely supervised, once a minute while the clock runs.
     *
     * If the service is killed and the phone is not touched for an hour, the stored state would
     * otherwise read as "an hour of study" the next time the app opens. The heartbeat is what lets
     * [TimerEngine] bank only the time it can actually vouch for. It ticks only while running, so a
     * paused session costs nothing.
     */
    private fun startHeartbeat() {
        lifecycleScope.launch {
            TimerEngine.state
                .map { it.running }
                .distinctUntilChanged()
                .collectLatest { running ->
                    while (running) {
                        TimerEngine.heartbeat()
                        // The same minute tick keeps the home-screen widget counting up live.
                        WidgetUpdater.refresh(this@TimerService)
                        delay(60_000)
                    }
                }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        val listener = callListener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && listener != null) {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            runCatching { audio.removeOnModeChangedListener(listener) }
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Android requires startForeground() promptly after startForegroundService(), on every
        // delivery — including the one that is about to stop us.
        startForeground(buildNotification())

        when (intent?.action) {
            ACTION_START -> {
                TimerEngine.start(StudyActivity.from(intent.getStringExtra(EXTRA_ACTIVITY)))
                applyFocusMode(on = true)
                startForeground(buildNotification())
            }
            ACTION_PAUSE -> {
                TimerEngine.pause()
                startForeground(buildNotification())
            }
            ACTION_RESUME -> {
                TimerEngine.resume()
                // Focus mode belongs to the sitting, not to the start of it: if Do Not Disturb was
                // turned off during the break — or by something else entirely — picking the clock
                // back up should silence the phone again.
                applyFocusMode(on = true)
                startForeground(buildNotification())
            }
            ACTION_STOP -> {
                finishSession()
                return START_NOT_STICKY
            }
            ACTION_RESUME_LOGGED -> {
                val id = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                if (id >= 0) resumeLoggedSession(id) else stopSelf()
            }
            ACTION_ENSURE -> {
                // Nothing to re-attach to: the session ended while the service was gone.
                if (!TimerEngine.state.value.isActive) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun resumeLoggedSession(id: Long) {
        val container = (application as PrepApp).container
        lifecycleScope.launch {
            val session = container.studyDao.byId(id)
            if (session == null || session.deleted) {
                stopSelf()
                return@launch
            }
            TimerEngine.resumeLogged(session)
            applyFocusMode(on = true)
            startForeground(buildNotification())
            WidgetUpdater.refresh(this@TimerService)
        }
    }

    private fun finishSession() {
        val outcome = TimerEngine.stop()
        applyFocusMode(on = false)
        val container = (application as PrepApp).container
        lifecycleScope.launch {
            when (outcome) {
                is TimerEngine.Stopped.New -> container.studyDao.insert(outcome.session)
                is TimerEngine.Stopped.Extended ->
                    container.studyDao.extend(outcome.id, outcome.durationMs, outcome.endedAt)
                TimerEngine.Stopped.Discarded -> Unit
            }
            if (outcome !is TimerEngine.Stopped.Discarded) {
                // Push it now rather than waiting for the next app launch: a logged sitting is
                // exactly the thing that should not be lost with the phone.
                runCatching { SyncEngine.syncNow(this@TimerService) }
            }
            WidgetUpdater.refresh(this@TimerService)
            ServiceCompat.stopForeground(this@TimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * Focus mode covers the whole sitting, pauses included: a pause is a two-minute drink break,
     * and having the phone light up again for those two minutes is exactly the distraction the
     * setting is there to prevent.
     */
    private fun applyFocusMode(on: Boolean) {
        val container = (application as PrepApp).container
        lifecycleScope.launch {
            val enabled = container.settings.settings.first().focusModeEnabled
            if (!enabled) return@launch
            if (on) FocusMode.enable(this@TimerService) else FocusMode.disable(this@TimerService)
        }
    }

    private fun startForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private fun buildNotification(): Notification {
        val state = TimerEngine.state.value
        val label = state.activity?.label ?: "Study"

        val content = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(if (state.running) "Studying — $label" else "Paused — $label")
            .setContentIntent(content)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (state.running) {
            // The system renders the ticking itself, so the shade stays correct without this
            // service waking up once a second to redraw it.
            builder.setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - state.displayMs())
        } else {
            builder.setUsesChronometer(false)
                .setContentText(formatDuration(state.displayMs()) + " banked")
        }

        if (state.running) {
            builder.addAction(0, "Pause", action(ACTION_PAUSE, 1))
        } else {
            builder.addAction(0, "Resume", action(ACTION_RESUME, 2))
        }
        builder.addAction(0, "Stop & log", action(ACTION_STOP, 3))

        return builder.build()
    }

    private fun action(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, TimerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Study timer",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows the running study session"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** "3h 42m" — for totals, where seconds are noise. */
fun formatHoursMinutes(ms: Long): String {
    val totalMinutes = ms / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
