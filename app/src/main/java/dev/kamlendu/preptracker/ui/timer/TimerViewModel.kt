package dev.kamlendu.preptracker.ui.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kamlendu.preptracker.appContainer
import dev.kamlendu.preptracker.data.AppSettings
import dev.kamlendu.preptracker.data.StudyActivity
import dev.kamlendu.preptracker.data.StudySession
import dev.kamlendu.preptracker.data.dayKeyOf
import dev.kamlendu.preptracker.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.appContainer

    val settings = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val sessions = container.studyDao.recent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The sitting just removed, held only as long as the undo offer stands. */
    private val _undoable = MutableStateFlow<StudySession?>(null)
    val undoable = _undoable.asStateFlow()

    fun delete(session: StudySession) {
        viewModelScope.launch {
            container.studyDao.delete(session.id)
            _undoable.value = session
            WidgetUpdater.refresh(getApplication())
        }
    }

    fun undoDelete() {
        val session = _undoable.value ?: return
        _undoable.value = null
        viewModelScope.launch {
            container.studyDao.restore(session.id)
            WidgetUpdater.refresh(getApplication())
        }
    }

    fun forgetUndo() {
        _undoable.value = null
    }

    /**
     * For a sitting the timer missed — the phone died, or you studied away from it. Stored with
     * `manual = true` so the dashboard can always say where a number came from.
     */
    fun addManualSession(
        activity: StudyActivity,
        minutes: Int,
        endedAt: Long = System.currentTimeMillis(),
    ) {
        if (minutes <= 0) return
        val durationMs = minutes * 60_000L
        viewModelScope.launch {
            container.studyDao.insert(
                StudySession(
                    dayKey = dayKeyOf(endedAt),
                    activity = activity.id,
                    startedAt = endedAt - durationMs,
                    endedAt = endedAt,
                    durationMs = durationMs,
                    manual = true,
                )
            )
            WidgetUpdater.refresh(getApplication())
        }
    }
}
