package dev.kamlendu.preptracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val dailyLimitPaise: Long = 20_000L, // ₹200 — the figure you named as a working default
    val focusModeEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val autoCaptureEnabled: Boolean = true,
    val dimScreen: Boolean = true,
    /** The day's study goal. The dashboard and the home-screen widget both count down to it. */
    val dailyTargetMinutes: Int = 480,
    /**
     * The whole pot set aside for the preparation months, not a per-day figure. Zero means no
     * overall budget is set, and the dashboard leaves it out rather than showing an empty bar.
     */
    val totalBudgetPaise: Long = 0L,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val DAILY_LIMIT = longPreferencesKey("daily_limit_paise")
        val FOCUS_MODE = booleanPreferencesKey("focus_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val AUTO_CAPTURE = booleanPreferencesKey("auto_capture")
        val DIM_SCREEN = booleanPreferencesKey("dim_screen")
        val DAILY_TARGET = intPreferencesKey("daily_target_minutes")
        val TOTAL_BUDGET = longPreferencesKey("total_budget_paise")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            dailyLimitPaise = p[Keys.DAILY_LIMIT] ?: 20_000L,
            focusModeEnabled = p[Keys.FOCUS_MODE] ?: true,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
            autoCaptureEnabled = p[Keys.AUTO_CAPTURE] ?: true,
            dimScreen = p[Keys.DIM_SCREEN] ?: true,
            dailyTargetMinutes = p[Keys.DAILY_TARGET] ?: 480,
            totalBudgetPaise = p[Keys.TOTAL_BUDGET] ?: 0L,
        )
    }

    suspend fun setDailyLimit(paise: Long) = edit { it[Keys.DAILY_LIMIT] = paise.coerceAtLeast(0) }
    suspend fun setFocusMode(on: Boolean) = edit { it[Keys.FOCUS_MODE] = on }
    suspend fun setKeepScreenOn(on: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = on }
    suspend fun setAutoCapture(on: Boolean) = edit { it[Keys.AUTO_CAPTURE] = on }
    suspend fun setDimScreen(on: Boolean) = edit { it[Keys.DIM_SCREEN] = on }
    suspend fun setDailyTarget(minutes: Int) = edit { it[Keys.DAILY_TARGET] = minutes.coerceIn(0, 24 * 60) }
    suspend fun setTotalBudget(paise: Long) = edit { it[Keys.TOTAL_BUDGET] = paise.coerceAtLeast(0) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
