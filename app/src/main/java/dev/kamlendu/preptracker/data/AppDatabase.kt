package dev.kamlendu.preptracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StudySession::class, Expense::class, RevisionRemark::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun revisionDao(): RevisionDao

    companion object {
        /**
         * Adds the columns sync needs, and back-fills them for rows recorded before there was a
         * server to send them to. Written out rather than falling back to a destructive migration:
         * the rows already on the phone are the whole point of the feature.
         *
         * `uid` gets a random value per existing row, `updatedAt` takes the row's own timestamp,
         * and everything starts life pending so the first sync uploads the entire history.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("study_sessions", "expenses")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("UPDATE $table SET uid = lower(hex(randomblob(16))) WHERE uid = ''")
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_${table}_uid ON $table(uid)"
                    )
                }
                db.execSQL("UPDATE study_sessions SET updatedAt = endedAt WHERE updatedAt = 0")
                db.execSQL("UPDATE expenses SET updatedAt = occurredAt WHERE updatedAt = 0")
            }
        }

        /** Revision-card remarks. A new table only — nothing existing is touched. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS revision_remarks (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "cardId TEXT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "deleted INTEGER NOT NULL DEFAULT 0, " +
                        "updatedAt INTEGER NOT NULL DEFAULT 0, " +
                        "pendingSync INTEGER NOT NULL DEFAULT 1)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_revision_remarks_cardId " +
                        "ON revision_remarks(cardId)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "prep-tracker.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
