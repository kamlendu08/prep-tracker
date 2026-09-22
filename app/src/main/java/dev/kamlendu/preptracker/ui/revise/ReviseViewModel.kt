package dev.kamlendu.preptracker.ui.revise

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kamlendu.preptracker.appContainer
import dev.kamlendu.preptracker.data.RevisionRemark
import dev.kamlendu.preptracker.revision.RevisionContent
import dev.kamlendu.preptracker.revision.Subject
import dev.kamlendu.preptracker.revision.SubjectSummary
import dev.kamlendu.preptracker.sync.SyncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReviseViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.appContainer

    private val _subjects = MutableStateFlow<List<SubjectSummary>>(emptyList())
    val subjects = _subjects.asStateFlow()

    /** cardId → note. Only live notes; a cleared one is a tombstone and must not show. */
    val remarks = container.revisionDao.liveRemarks()
        .map { rows -> rows.associate { it.cardId to it.text } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch { _subjects.value = RevisionContent.index(getApplication()) }
    }

    suspend fun subject(slug: String): Subject? = RevisionContent.subject(getApplication(), slug)

    /**
     * Writes the note locally and then tries to push it. The local write is what matters — this
     * screen is used on trains — so the sync is best-effort and the row stays pending until the
     * server has it.
     */
    fun saveRemark(cardId: String, text: String) = viewModelScope.launch {
        val trimmed = text.trim()
        val existing = container.revisionDao.byCard(cardId)
        container.revisionDao.upsert(
            RevisionRemark(
                id = existing?.id ?: 0,
                cardId = cardId,
                text = trimmed,
                deleted = trimmed.isEmpty(),
                updatedAt = System.currentTimeMillis(),
                pendingSync = true,
            )
        )
        SyncEngine.syncNow(getApplication())
    }
}
