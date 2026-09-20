package dev.kamlendu.preptracker.timer

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import dev.kamlendu.preptracker.data.StudyActivity
import dev.kamlendu.preptracker.data.StudySession
import dev.kamlendu.preptracker.data.dayKeyOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class TimerState(
    val activity: StudyActivity? = null,
    val running: Boolean = false,
    /** Study time banked by earlier run segments of this sitting; pauses are not in here. */
    val accumulatedMs: Long = 0L,
    /** SystemClock.elapsedRealtime() at the last resume. Meaningless while paused. */
    val resumedAtElapsed: Long = 0L,
    val startedAtWall: Long = 0L,
    /**
     * True when a running stretch could not be measured — a reboot, or the service being killed —
     * and the clock came back paused with only the time it could vouch for.
     */
    val recoveredFromGap: Boolean = false,
    /**
     * Set when an already-logged sitting was picked back up. The row is extended on stop rather
     * than a second one being written, so "30 minutes, then another 20" reads as one 50-minute
     * sitting instead of two short ones.
     */
    val resumedSessionId: Long? = null,
    /** Time already on that logged row. Displayed, but never counted twice — see [elapsedMs]. */
    val carriedMs: Long = 0L,
) {
    val isActive: Boolean get() = activity != null

    /**
     * New time measured in this run only. Everything that adds live time to a stored total — the
     * dashboard, the widget — uses this, because the carried time is already in the database.
     */
    fun elapsedMs(nowElapsed: Long = SystemClock.elapsedRealtime()): Long =
        accumulatedMs + if (running) (nowElapsed - resumedAtElapsed).coerceAtLeast(0) else 0

    /** What the clock face shows: the whole sitting, carried time included. */
    fun displayMs(nowElapsed: Long = SystemClock.elapsedRealtime()): Long =
        carriedMs + elapsedMs(nowElapsed)
}

/**
 * The single source of truth for the running stopwatch.
 *
 * Time is measured with `elapsedRealtime()`, never by counting ticks: a UI that counts frames
 * loses minutes the moment the screen sleeps or the process is frozen, and a study log that
 * quietly under-reports is worse than no log. The UI only *renders* on a tick — the value it
 * renders is always a subtraction against the clock.
 *
 * Mutations go through [TimerService] so that starting, pausing and stopping stay tied to the
 * foreground notification and to Do Not Disturb.
 */
object TimerEngine {
    private const val PREFS = "timer_state"
    private const val K_ACTIVITY = "activity"
    private const val K_RUNNING = "running"
    private const val K_ACCUM = "accumulated"
    private const val K_RESUMED = "resumed_at_elapsed"
    private const val K_STARTED = "started_at_wall"
    private const val K_BOOT_REF = "boot_ref"
    private const val K_ALIVE = "last_alive"
    private const val K_RESUMED_ID = "resumed_session_id"
    private const val K_CARRIED = "carried_ms"

    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    /** Wall-clock time of boot. Shifts only across a reboot, which is exactly what we test for. */
    private fun bootRef(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    fun restore(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val activity = p.getString(K_ACTIVITY, null) ?: return
        val running = p.getBoolean(K_RUNNING, false)
        val accumulated = p.getLong(K_ACCUM, 0L)
        val resumed = p.getLong(K_RESUMED, 0L)
        val started = p.getLong(K_STARTED, 0L)
        val resumedId = p.getLong(K_RESUMED_ID, -1L).takeIf { it >= 0 }
        val carried = p.getLong(K_CARRIED, 0L)
        val rebooted = abs(bootRef() - p.getLong(K_BOOT_REF, 0L)) > 5_000L

        _state.value = if (running && rebooted) {
            // elapsedRealtime() restarted at 0, so the segment that was running has no measurable
            // length. Bank what was already accumulated and come back paused rather than invent it.
            TimerState(
                activity = StudyActivity.from(activity),
                running = false,
                accumulatedMs = accumulated,
                startedAtWall = started,
                recoveredFromGap = true,
                resumedSessionId = resumedId,
                carriedMs = carried,
            )
        } else if (running && staleSince(p, resumed) != null) {
            // The service stopped supervising this session (an app update, a force-stop, a kill).
            // Bank only up to the last heartbeat: whatever happened after it is not time this app
            // can honestly claim was spent studying.
            val lastAlive = staleSince(p, resumed)!!
            TimerState(
                activity = StudyActivity.from(activity),
                running = false,
                accumulatedMs = accumulated + (lastAlive - resumed).coerceAtLeast(0),
                startedAtWall = started,
                recoveredFromGap = true,
                resumedSessionId = resumedId,
                carriedMs = carried,
            )
        } else {
            TimerState(
                activity = StudyActivity.from(activity),
                running = running,
                accumulatedMs = accumulated,
                resumedAtElapsed = resumed,
                startedAtWall = started,
                resumedSessionId = resumedId,
                carriedMs = carried,
            )
        }
        if (_state.value.recoveredFromGap) persist()
    }

    /**
     * Returns the last moment the session was known to be supervised, or null if the heartbeat is
     * recent enough that the clock can simply be trusted. Two missed beats is the threshold.
     */
    private fun staleSince(p: SharedPreferences, resumedAtElapsed: Long): Long? {
        val lastAlive = p.getLong(K_ALIVE, resumedAtElapsed)
        val gap = SystemClock.elapsedRealtime() - lastAlive
        return if (gap > 150_000L) lastAlive else null
    }

    /** Called once a minute by the service while the clock runs. */
    fun heartbeat() {
        prefs?.edit()?.putLong(K_ALIVE, SystemClock.elapsedRealtime())?.apply()
    }

    fun start(activity: StudyActivity) {
        _state.value = TimerState(
            activity = activity,
            running = true,
            accumulatedMs = 0L,
            resumedAtElapsed = SystemClock.elapsedRealtime(),
            startedAtWall = System.currentTimeMillis(),
        )
        persist()
    }

    /**
     * Picks a logged sitting back up. Its stored time is carried onto the clock face and its row
     * is extended when this run stops, so the sitting stays one row.
     */
    fun resumeLogged(session: StudySession) {
        _state.value = TimerState(
            activity = StudyActivity.from(session.activity),
            running = true,
            accumulatedMs = 0L,
            resumedAtElapsed = SystemClock.elapsedRealtime(),
            startedAtWall = session.startedAt,
            resumedSessionId = session.id,
            carriedMs = session.durationMs,
        )
        persist()
    }

    fun pause() {
        val s = _state.value
        if (!s.isActive || !s.running) return
        _state.value = s.copy(
            running = false,
            accumulatedMs = s.elapsedMs(),
            resumedAtElapsed = 0L,
            recoveredFromGap = false,
        )
        persist()
    }

    fun resume() {
        val s = _state.value
        if (!s.isActive || s.running) return
        _state.value = s.copy(
            running = true,
            resumedAtElapsed = SystemClock.elapsedRealtime(),
            recoveredFromGap = false,
        )
        persist()
    }

    /** What [stop] wants done with the sitting that just ended. */
    sealed interface Stopped {
        /** A new sitting worth recording. */
        data class New(val session: StudySession) : Stopped
        /** A resumed sitting: the existing row grows instead of a second one appearing. */
        data class Extended(val id: Long, val durationMs: Long, val endedAt: Long) : Stopped
        /** Under a minute and not a resumption — a mis-tap, dropped. */
        data object Discarded : Stopped
    }

    /**
     * Ends the sitting. A first run under a minute is dropped as a mis-tap; a *resumed* sitting is
     * always written back, because the row already exists and silently shrinking it would be worse
     * than recording a short extra stretch.
     */
    fun stop(): Stopped {
        val s = _state.value
        val elapsed = s.elapsedMs()
        val activity = s.activity
        val resumedId = s.resumedSessionId
        val carried = s.carriedMs
        _state.value = TimerState()
        persist()

        if (activity == null) return Stopped.Discarded
        val endedAt = System.currentTimeMillis()

        if (resumedId != null) {
            return Stopped.Extended(resumedId, carried + elapsed, endedAt)
        }
        if (elapsed < 60_000L) return Stopped.Discarded

        return Stopped.New(
            StudySession(
                dayKey = dayKeyOf(s.startedAtWall.takeIf { it > 0 } ?: endedAt),
                activity = activity.id,
                startedAt = s.startedAtWall.takeIf { it > 0 } ?: (endedAt - elapsed),
                endedAt = endedAt,
                durationMs = elapsed,
            )
        )
    }

    private fun persist() {
        val s = _state.value
        prefs?.edit()?.apply {
            if (s.activity == null) {
                clear()
            } else {
                putString(K_ACTIVITY, s.activity.id)
                putBoolean(K_RUNNING, s.running)
                putLong(K_ACCUM, s.accumulatedMs)
                putLong(K_RESUMED, s.resumedAtElapsed)
                putLong(K_STARTED, s.startedAtWall)
                putLong(K_BOOT_REF, bootRef())
                putLong(K_ALIVE, SystemClock.elapsedRealtime())
                putLong(K_RESUMED_ID, s.resumedSessionId ?: -1L)
                putLong(K_CARRIED, s.carriedMs)
            }
        }?.apply()
    }
}
