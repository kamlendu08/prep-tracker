package dev.kamlendu.preptracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** What a study session was spent on. The timer asks for this before it starts counting. */
enum class StudyActivity(val id: String, val label: String) {
    LECTURE("LECTURE", "Lecture"),
    PRACTICE("PRACTICE", "Practice"),
    TEST("TEST", "Test");

    companion object {
        fun from(id: String?): StudyActivity = entries.firstOrNull { it.id == id } ?: LECTURE
    }
}

enum class ExpenseCategory(val id: String, val label: String) {
    FOOD("FOOD", "Food"),
    GROCERIES("GROCERIES", "Groceries"),
    TRANSPORT("TRANSPORT", "Transport"),
    STUDY("STUDY", "Study"),
    BILLS("BILLS", "Bills & recharge"),
    HEALTH("HEALTH", "Health"),
    SHOPPING("SHOPPING", "Shopping"),
    /**
     * Paying off a credit-card bill. Kept in the list but left out of the daily total: those
     * purchases were already counted on the day they happened, so counting the settlement too
     * would charge the same money to the budget twice.
     */
    CARD_BILL("CARD_BILL", "Card bill"),
    OTHER("OTHER", "Other");

    companion object {
        fun from(id: String?): ExpenseCategory = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/** Where an expense row came from — shown in the list so a mis-parse is traceable. */
enum class ExpenseSource(val id: String, val label: String) {
    SMS("SMS", "SMS"),
    /**
     * No longer captured. Watching notifications as well as SMS meant one purchase arrived as
     * several unrelated-looking messages — a wallet email, an order confirmation, the bank's own
     * SMS — and no amount of duplicate logic could reliably tell them apart. Kept so rows recorded
     * before that decision still display correctly.
     */
    NOTIFICATION("NOTIF", "Notification"),
    MANUAL("MANUAL", "Manual");

    companion object {
        fun from(id: String?): ExpenseSource = entries.firstOrNull { it.id == id } ?: MANUAL
    }
}

/**
 * One continuous sitting. Pauses are excluded: [durationMs] is time actually spent studying,
 * which is the number the dashboard reports and the one worth competing against.
 */
@Entity(
    tableName = "study_sessions",
    indices = [Index("dayKey"), Index(value = ["uid"], unique = true)],
)
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * The id this row carries on the server. Generated here, not there, so pushing the same row
     * twice — after a dropped connection, say — updates it instead of creating a second copy.
     */
    val uid: String = UUID.randomUUID().toString(),
    /** yyyy-MM-dd in the device's zone — every "today" query keys off this, never off a range. */
    val dayKey: String,
    val activity: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val note: String? = null,
    val manual: Boolean = false,
    /** Tombstone. A row that simply vanished could not tell the server it was deleted. */
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    /** True until the current version of this row has reached the server. */
    val pendingSync: Boolean = true,
)

/**
 * Money leaving the account. Amounts are paise (integer) — rupee floats accumulate error over a
 * month of small transactions, and the daily-limit maths must be exact.
 *
 * [fingerprint] is unique: the same debit usually arrives twice (bank SMS *and* the UPI app's
 * notification), so the second one has to collide rather than double-count.
 */
@Entity(
    tableName = "expenses",
    indices = [
        Index("dayKey"),
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["uid"], unique = true),
    ],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val dayKey: String,
    val amountPaise: Long,
    val merchant: String?,
    val category: String,
    val occurredAt: Long,
    val source: String,
    val rawText: String? = null,
    val fingerprint: String,
    /** True once the category has been corrected by hand, so re-parsing never overwrites it. */
    val categoryLocked: Boolean = false,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val pendingSync: Boolean = true,
)

fun dayKeyOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()

fun todayKey(zone: ZoneId = ZoneId.systemDefault()): String = LocalDate.now(zone).toString()

/**
 * The candidate's own note on one revision card — "always forget the 1/2 here", "did this wrong
 * in the 2019 paper".
 *
 * The card's slug path (`subject/topic/card`) *is* the identity, on the phone and on the server
 * alike: revision content ships with the app rather than living in a table, so there is no row to
 * point a foreign key at, and a note written here has to land on the same card on the website.
 */
@Entity(
    tableName = "revision_remarks",
    indices = [Index(value = ["cardId"], unique = true)],
)
data class RevisionRemark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val text: String,
    /** Tombstone. Clearing a note has to reach the other device, not just vanish here. */
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val pendingSync: Boolean = true,
)
