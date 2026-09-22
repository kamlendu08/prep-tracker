package dev.kamlendu.preptracker.sync

import android.content.Context
import android.util.Log
import dev.kamlendu.preptracker.appContainer
import dev.kamlendu.preptracker.data.Expense
import dev.kamlendu.preptracker.data.RevisionRemark
import dev.kamlendu.preptracker.data.StudySession
import dev.kamlendu.preptracker.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pushes what this phone has changed and pulls what changed elsewhere.
 *
 * Deliberately simple, because the realistic conflict here is "the same person on one phone", not
 * concurrent editors: every row carries `updatedAt`, and the later timestamp wins on both sides.
 * The one thing it must never do is lose a local row — so a row stays `pendingSync` until the
 * server has acknowledged that exact version of it.
 *
 * Nothing about the *content* of a bank message is sent. Amount, payee, category and time go up;
 * the message body stays on the phone.
 */
object SyncEngine {
    private const val TAG = "SyncEngine"
    private const val BATCH = 500

    private val mutex = Mutex()

    sealed interface State {
        data object Idle : State
        data object Running : State
        data class Done(val at: Long, val pushed: Int, val pulled: Int) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** @return true when the sync completed; false when signed out, offline, or already running. */
    suspend fun syncNow(context: Context): Boolean {
        val app = context.applicationContext
        val container = app.appContainer
        val account = container.auth.current()
        val token = account.token ?: return false

        // One at a time: two overlapping syncs would both mark rows synced against different
        // server clocks and could strand a change.
        if (mutex.isLocked) return false

        return mutex.withLock {
            _state.value = State.Running

            val sessions = container.studyDao.pendingSync(BATCH)
            val spends = container.expenseDao.pendingSync(BATCH)
            val remarks = container.revisionDao.pendingSync(BATCH)

            val response = SyncApi.sync(
                baseUrl = SyncApi.DEFAULT_BASE_URL,
                token = token,
                since = account.lastSyncAt,
                sessions = sessions.map { it.toApi() },
                spends = spends.map { it.toApi() },
                remarks = remarks.map { it.toApi() },
            )

            when (response) {
                is SyncApi.Result.Failed -> {
                    Log.w(TAG, "sync failed: ${response.message}")
                    if (response.signedOut) container.auth.signOut()
                    _state.value = State.Failed(response.message)
                    false
                }

                is SyncApi.Result.Ok -> {
                    val value = response.value
                    // Only clear the pending flag for the exact versions that were sent: anything
                    // edited while the request was in flight has a newer `updatedAt` and stays
                    // pending for the next round.
                    container.studyDao.markSynced(
                        sessions.map { it.uid },
                        sessions.maxOfOrNull { it.updatedAt } ?: 0L,
                    )
                    container.expenseDao.markSynced(
                        spends.map { it.uid },
                        spends.maxOfOrNull { it.updatedAt } ?: 0L,
                    )
                    // Only when the server proved it understands them. A deployment older than
                    // this app drops the field without complaint and still answers 200; clearing
                    // the pending flag on that would lose the note for good.
                    if (value.revisionRemarks != null) {
                        container.revisionDao.markSynced(
                            remarks.map { it.cardId },
                            remarks.maxOfOrNull { it.updatedAt } ?: 0L,
                        )
                    } else if (remarks.isNotEmpty()) {
                        Log.w(TAG, "server has no revision-remark support; keeping ${remarks.size} pending")
                    }

                    val pulled = applyServerRows(context, value)
                    container.auth.setLastSync(value.serverTime)
                    if (pulled > 0) WidgetUpdater.refresh(app)

                    _state.value = State.Done(
                        value.serverTime,
                        sessions.size + spends.size + remarks.size,
                        pulled,
                    )
                    true
                }
            }
        }
    }

    private suspend fun applyServerRows(context: Context, response: SyncApi.SyncResponse): Int {
        val container = context.applicationContext.appContainer
        var applied = 0

        for (remote in response.sessions) {
            val local = container.studyDao.byUid(remote.uid)
            if (local != null && local.updatedAt >= remote.updatedAt) continue
            container.studyDao.upsert(
                StudySession(
                    id = local?.id ?: 0,
                    uid = remote.uid,
                    dayKey = remote.dayKey,
                    activity = remote.activity,
                    startedAt = remote.startedAt,
                    endedAt = remote.endedAt,
                    durationMs = remote.durationMs,
                    note = remote.note,
                    manual = remote.manual,
                    deleted = remote.deleted,
                    updatedAt = remote.updatedAt,
                    pendingSync = false,
                )
            )
            applied++
        }

        for (remote in response.spends) {
            val local = container.expenseDao.byUid(remote.uid)
            if (local != null && local.updatedAt >= remote.updatedAt) continue
            container.expenseDao.upsert(
                Expense(
                    id = local?.id ?: 0,
                    uid = remote.uid,
                    dayKey = remote.dayKey,
                    amountPaise = remote.amountPaise,
                    merchant = remote.merchant,
                    category = remote.category,
                    occurredAt = remote.occurredAt,
                    source = remote.source,
                    // Message text is never uploaded, so a row that came back from the server has
                    // none to restore — except on this phone, where the local copy still has it.
                    rawText = local?.rawText,
                    fingerprint = remote.fingerprint,
                    categoryLocked = remote.categoryLocked,
                    deleted = remote.deleted,
                    updatedAt = remote.updatedAt,
                    pendingSync = false,
                )
            )
            applied++
        }
        for (remote in response.revisionRemarks.orEmpty()) {
            val local = container.revisionDao.byCard(remote.cardId)
            if (local != null && local.updatedAt >= remote.updatedAt) continue
            container.revisionDao.upsert(
                RevisionRemark(
                    id = local?.id ?: 0,
                    cardId = remote.cardId,
                    text = remote.text,
                    deleted = remote.deleted,
                    updatedAt = remote.updatedAt,
                    pendingSync = false,
                )
            )
            applied++
        }
        return applied
    }

    private fun StudySession.toApi() = SyncApi.Session(
        uid = uid,
        dayKey = dayKey,
        activity = activity,
        startedAt = startedAt,
        endedAt = endedAt,
        durationMs = durationMs,
        manual = manual,
        note = note,
        deleted = deleted,
        updatedAt = updatedAt,
    )

    private fun RevisionRemark.toApi() = SyncApi.Remark(
        cardId = cardId,
        text = text,
        deleted = deleted,
        updatedAt = updatedAt,
    )

    private fun Expense.toApi() = SyncApi.Spend(
        uid = uid,
        dayKey = dayKey,
        amountPaise = amountPaise,
        merchant = merchant,
        category = category,
        occurredAt = occurredAt,
        source = source,
        fingerprint = fingerprint,
        categoryLocked = categoryLocked,
        deleted = deleted,
        updatedAt = updatedAt,
    )
}
