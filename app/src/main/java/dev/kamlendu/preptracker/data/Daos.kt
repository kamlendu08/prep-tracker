package dev.kamlendu.preptracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DayTotal(val dayKey: String, val total: Long)

data class ActivityTotal(val activity: String, val total: Long)

data class CategoryTotal(val category: String, val total: Long)

@Dao
interface StudyDao {
    @Insert
    suspend fun insert(session: StudySession): Long

    /** A tombstone, not a delete: the server has to learn that this row went away. */
    @Query(
        "UPDATE study_sessions SET deleted = 1, pendingSync = 1, updatedAt = :now WHERE id = :id"
    )
    suspend fun delete(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM study_sessions WHERE dayKey = :dayKey AND deleted = 0 ORDER BY startedAt DESC")
    fun sessionsForDay(dayKey: String): Flow<List<StudySession>>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM study_sessions WHERE dayKey = :dayKey AND deleted = 0")
    fun totalForDay(dayKey: String): Flow<Long>

    @Query(
        "SELECT activity, COALESCE(SUM(durationMs), 0) AS total FROM study_sessions " +
            "WHERE dayKey = :dayKey AND deleted = 0 GROUP BY activity"
    )
    fun activityTotalsForDay(dayKey: String): Flow<List<ActivityTotal>>

    @Query(
        "SELECT dayKey, COALESCE(SUM(durationMs), 0) AS total FROM study_sessions " +
            "WHERE dayKey >= :fromDayKey AND deleted = 0 GROUP BY dayKey ORDER BY dayKey"
    )
    fun dailyTotalsSince(fromDayKey: String): Flow<List<DayTotal>>

    @Query("SELECT COUNT(*) FROM study_sessions WHERE dayKey = :dayKey AND deleted = 0")
    fun sessionCountForDay(dayKey: String): Flow<Int>

    @Query(
        "SELECT activity, COALESCE(SUM(durationMs), 0) AS total FROM study_sessions " +
            "WHERE dayKey >= :fromDayKey AND deleted = 0 GROUP BY activity"
    )
    fun activityTotalsSince(fromDayKey: String): Flow<List<ActivityTotal>>

    @Query("SELECT COUNT(*) FROM study_sessions WHERE dayKey >= :fromDayKey AND deleted = 0")
    fun sessionCountSince(fromDayKey: String): Flow<Int>

    @Query(
        "SELECT * FROM study_sessions WHERE dayKey >= :fromDayKey AND deleted = 0 " +
            "ORDER BY durationMs DESC LIMIT :limit"
    )
    fun longestSince(fromDayKey: String, limit: Int): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE deleted = 0 ORDER BY startedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE id = :id")
    suspend fun byId(id: Long): StudySession?

    /** Undo, and the counterpart of the tombstone delete. */
    @Query(
        "UPDATE study_sessions SET deleted = 0, pendingSync = 1, updatedAt = :now WHERE id = :id"
    )
    suspend fun restore(id: Long, now: Long = System.currentTimeMillis())

    /** Used when a sitting is picked back up: the row grows rather than a second one appearing. */
    @Query(
        "UPDATE study_sessions SET durationMs = :durationMs, endedAt = :endedAt, " +
            "pendingSync = 1, updatedAt = :now WHERE id = :id"
    )
    suspend fun extend(
        id: Long,
        durationMs: Long,
        endedAt: Long,
        now: Long = System.currentTimeMillis(),
    )

    /** Oldest day with a sitting, for the "all time" report range. */
    @Query("SELECT MIN(dayKey) FROM study_sessions WHERE deleted = 0")
    fun earliestDay(): Flow<String?>

    /** One-shot read for the home-screen widget, which has no lifecycle to collect a Flow with. */
    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM study_sessions WHERE dayKey = :dayKey AND deleted = 0")
    suspend fun totalForDayOnce(dayKey: String): Long

    // ---- sync ----

    @Query("SELECT * FROM study_sessions WHERE pendingSync = 1 LIMIT :limit")
    suspend fun pendingSync(limit: Int): List<StudySession>

    @Query("SELECT * FROM study_sessions WHERE uid = :uid")
    suspend fun byUid(uid: String): StudySession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: StudySession)

    @Query("UPDATE study_sessions SET pendingSync = 0 WHERE uid IN (:uids) AND updatedAt <= :upTo")
    suspend fun markSynced(uids: List<String>, upTo: Long)

    @Query("DELETE FROM study_sessions")
    suspend fun clearAll()
}

@Dao
interface ExpenseDao {
    /**
     * IGNORE, not REPLACE: the unique fingerprint means a duplicate is the *same* debit arriving
     * from a second source, and replacing would wipe a hand-corrected category.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(expense: Expense): Long

    @Query("UPDATE expenses SET deleted = 1, pendingSync = 1, updatedAt = :now WHERE id = :id")
    suspend fun delete(id: Long, now: Long = System.currentTimeMillis())

    /** Undo. Only possible because a delete is a tombstone rather than a DELETE. */
    @Query("UPDATE expenses SET deleted = 0, pendingSync = 1, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long = System.currentTimeMillis())

    @Query(
        "UPDATE expenses SET category = :category, categoryLocked = 1, " +
            "pendingSync = 1, updatedAt = :now WHERE id = :id"
    )
    suspend fun setCategory(id: Long, category: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM expenses WHERE dayKey = :dayKey AND deleted = 0 ORDER BY occurredAt DESC")
    fun forDay(dayKey: String): Flow<List<Expense>>

    /** Card-bill settlements are excluded everywhere a *total* is computed — see [ExpenseCategory]. */
    @Query(
        "SELECT COALESCE(SUM(amountPaise), 0) FROM expenses " +
            "WHERE dayKey = :dayKey AND deleted = 0 AND category != 'CARD_BILL'"
    )
    fun totalForDay(dayKey: String): Flow<Long>

    @Query(
        "SELECT category, COALESCE(SUM(amountPaise), 0) AS total FROM expenses " +
            "WHERE dayKey = :dayKey AND deleted = 0 AND category != 'CARD_BILL' GROUP BY category ORDER BY total DESC"
    )
    fun categoryTotalsForDay(dayKey: String): Flow<List<CategoryTotal>>

    @Query(
        "SELECT dayKey, COALESCE(SUM(amountPaise), 0) AS total FROM expenses " +
            "WHERE dayKey >= :fromDayKey AND deleted = 0 AND category != 'CARD_BILL' GROUP BY dayKey ORDER BY dayKey"
    )
    fun dailyTotalsSince(fromDayKey: String): Flow<List<DayTotal>>

    @Query("SELECT * FROM expenses WHERE deleted = 0 ORDER BY occurredAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<Expense>>

    @Query(
        "SELECT category, COALESCE(SUM(amountPaise), 0) AS total FROM expenses " +
            "WHERE dayKey >= :fromDayKey AND deleted = 0 AND category != 'CARD_BILL' " +
            "GROUP BY category ORDER BY total DESC"
    )
    fun categoryTotalsSince(fromDayKey: String): Flow<List<CategoryTotal>>

    @Query(
        "SELECT * FROM expenses WHERE dayKey >= :fromDayKey AND deleted = 0 AND category != 'CARD_BILL' " +
            "ORDER BY amountPaise DESC LIMIT :limit"
    )
    fun biggestSince(fromDayKey: String, limit: Int): Flow<List<Expense>>

    @Query("SELECT MIN(dayKey) FROM expenses WHERE deleted = 0")
    fun earliestDay(): Flow<String?>

    /** Everything ever spent, for the overall budget. Card-bill settlements excluded as always. */
    @Query(
        "SELECT COALESCE(SUM(amountPaise), 0) FROM expenses " +
            "WHERE deleted = 0 AND category != 'CARD_BILL'"
    )
    fun totalAllTime(): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(amountPaise), 0) FROM expenses " +
            "WHERE deleted = 0 AND category != 'CARD_BILL'"
    )
    suspend fun totalAllTimeOnce(): Long

    @Query("SELECT MAX(occurredAt) FROM expenses WHERE source = :source")
    suspend fun latestFrom(source: String): Long?

    // ---- sync ----

    @Query("SELECT * FROM expenses WHERE pendingSync = 1 LIMIT :limit")
    suspend fun pendingSync(limit: Int): List<Expense>

    @Query("SELECT * FROM expenses WHERE uid = :uid")
    suspend fun byUid(uid: String): Expense?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: Expense)

    @Query("UPDATE expenses SET pendingSync = 0 WHERE uid IN (:uids) AND updatedAt <= :upTo")
    suspend fun markSynced(uids: List<String>, upTo: Long)

    @Query("DELETE FROM expenses")
    suspend fun clearAll()

    @Query(
        "SELECT COALESCE(SUM(amountPaise), 0) FROM expenses " +
            "WHERE dayKey = :dayKey AND deleted = 0 AND category != 'CARD_BILL'"
    )
    suspend fun totalForDayOnce(dayKey: String): Long

    /** Candidates for the duplicate check: same amount, near the same moment. */
    @Query(
        "SELECT * FROM expenses WHERE amountPaise = :amountPaise AND deleted = 0 " +
            "AND occurredAt BETWEEN :from AND :to ORDER BY occurredAt"
    )
    suspend fun nearbyWithAmount(amountPaise: Long, from: Long, to: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE deleted = 0 ORDER BY occurredAt")
    suspend fun allByTime(): List<Expense>

    /** Rows the parser owns — anything hand-corrected is excluded and stays that way. */
    @Query("SELECT * FROM expenses WHERE categoryLocked = 0 AND deleted = 0 AND rawText IS NOT NULL")
    suspend fun unlockedWithText(): List<Expense>

    @Query(
        "UPDATE expenses SET category = :category, merchant = :merchant, " +
            "pendingSync = 1, updatedAt = :now WHERE id = :id AND categoryLocked = 0"
    )
    suspend fun refreshParsed(
        id: Long,
        category: String,
        merchant: String?,
        now: Long = System.currentTimeMillis(),
    )
}
