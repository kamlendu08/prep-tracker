package dev.kamlendu.preptracker.ui.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kamlendu.preptracker.appContainer
import dev.kamlendu.preptracker.data.ActivityTotal
import dev.kamlendu.preptracker.data.AppSettings
import dev.kamlendu.preptracker.data.CategoryTotal
import dev.kamlendu.preptracker.data.DayTotal
import dev.kamlendu.preptracker.data.Expense
import dev.kamlendu.preptracker.data.StudySession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * @param days the length of the window, or null for everything ever recorded.
 */
enum class ReportRange(val label: String, val days: Int?) {
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    QUARTER("3 months", 90),
    HALF("6 months", 180),
    YEAR("1 year", 365),
    ALL("All time", null);

    /** Beyond about three months, calendar months read better than arbitrary blocks of days. */
    val bucketByMonth: Boolean get() = this == ALL || (days ?: 0) >= 90
}

data class Insight(val text: String, val good: Boolean? = null)

data class ReportUi(
    val range: ReportRange = ReportRange.WEEK,
    val dayKeys: List<String> = emptyList(),
    val studyByDay: Map<String, Long> = emptyMap(),
    val spendByDay: Map<String, Long> = emptyMap(),
    /** The equally long window immediately before this one, for the trend comparison. */
    val previousStudyMs: Long = 0,
    val previousSpendPaise: Long = 0,
    val activityTotals: List<ActivityTotal> = emptyList(),
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val sessionCount: Int = 0,
    val longestSessions: List<StudySession> = emptyList(),
    val biggestSpends: List<Expense> = emptyList(),
    val settings: AppSettings = AppSettings(),
) {
    val studyTotalMs: Long get() = dayKeys.sumOf { studyByDay[it] ?: 0L }
    val spendTotalPaise: Long get() = dayKeys.sumOf { spendByDay[it] ?: 0L }

    /** Over every day in the window, including the empty ones — the honest figure. */
    val studyPerDayMs: Long get() = if (dayKeys.isEmpty()) 0 else studyTotalMs / dayKeys.size
    val spendPerDayPaise: Long get() = if (dayKeys.isEmpty()) 0 else spendTotalPaise / dayKeys.size

    val daysStudied: Int get() = dayKeys.count { (studyByDay[it] ?: 0L) > 0 }

    /** Over the days you actually sat down — how long a working day looks when there is one. */
    val studyPerActiveDayMs: Long get() = if (daysStudied == 0) 0 else studyTotalMs / daysStudied

    val targetMs: Long get() = settings.dailyTargetMinutes * 60_000L
    val daysOnTarget: Int
        get() = if (targetMs <= 0) 0 else dayKeys.count { (studyByDay[it] ?: 0L) >= targetMs }
    val daysOverLimit: Int
        get() = dayKeys.count { (spendByDay[it] ?: 0L) > settings.dailyLimitPaise }
    val bestDayMs: Long get() = dayKeys.maxOfOrNull { studyByDay[it] ?: 0L } ?: 0L
    val budgetForPeriodPaise: Long get() = settings.dailyLimitPaise * dayKeys.size

    /** Consecutive days with any studying, counting back from the most recent day in the window. */
    val currentStreak: Int
        get() {
            var streak = 0
            for (day in dayKeys.asReversed()) {
                if ((studyByDay[day] ?: 0L) > 0) streak++ else break
            }
            return streak
        }

    val longestStreak: Int
        get() {
            var best = 0
            var running = 0
            for (day in dayKeys) {
                if ((studyByDay[day] ?: 0L) > 0) {
                    running++
                    if (running > best) best = running
                } else {
                    running = 0
                }
            }
            return best
        }

    /** Average study time per weekday — where the week quietly leaks. */
    val byWeekday: Map<DayOfWeek, Long>
        get() {
            val sums = mutableMapOf<DayOfWeek, Pair<Long, Int>>()
            for (day in dayKeys) {
                val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: continue
                val (total, count) = sums[date.dayOfWeek] ?: (0L to 0)
                sums[date.dayOfWeek] = (total + (studyByDay[day] ?: 0L)) to (count + 1)
            }
            return sums.mapValues { (_, v) -> if (v.second == 0) 0L else v.first / v.second }
        }

    /** Percentage change against the previous window, or null when there is nothing to compare. */
    val studyTrendPercent: Int?
        get() = if (previousStudyMs <= 0) null else
            (((studyTotalMs - previousStudyMs) * 100) / previousStudyMs).toInt()

    val spendTrendPercent: Int?
        get() = if (previousSpendPaise <= 0) null else
            (((spendTotalPaise - previousSpendPaise) * 100) / previousSpendPaise).toInt()
}

private data class StudyPart(
    val days: List<DayTotal>,
    val byActivity: List<ActivityTotal>,
    val sessions: Int,
    val longest: List<StudySession>,
    val earliest: String?,
)

private data class SpendPart(
    val days: List<DayTotal>,
    val byCategory: List<CategoryTotal>,
    val biggest: List<Expense>,
    val earliest: String?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.appContainer
    private val range = MutableStateFlow(ReportRange.WEEK)

    fun setRange(value: ReportRange) {
        range.value = value
    }

    val ui = range.flatMapLatest { selected ->
        // Pull from twice as far back as the window, so the same rows answer both "this period"
        // and "the period before it" without a second set of queries.
        val today = LocalDate.now()
        val lookback = selected.days?.let { today.minusDays((it * 2L) - 1) } ?: LocalDate.MIN
        val queryFrom = if (lookback == LocalDate.MIN) "0000-01-01" else lookback.toString()
        val windowFrom = selected.days?.let { today.minusDays(it - 1L).toString() } ?: "0000-01-01"

        val study = combine(
            container.studyDao.dailyTotalsSince(queryFrom),
            container.studyDao.activityTotalsSince(windowFrom),
            container.studyDao.sessionCountSince(windowFrom),
            container.studyDao.longestSince(windowFrom, 3),
            container.studyDao.earliestDay(),
        ) { days, byActivity, sessions, longest, earliest ->
            @Suppress("UNCHECKED_CAST")
            StudyPart(
                days = days as List<DayTotal>,
                byActivity = byActivity as List<ActivityTotal>,
                sessions = sessions as Int,
                longest = longest as List<StudySession>,
                earliest = earliest as String?,
            )
        }

        val spend = combine(
            container.expenseDao.dailyTotalsSince(queryFrom),
            container.expenseDao.categoryTotalsSince(windowFrom),
            container.expenseDao.biggestSince(windowFrom, 5),
            container.expenseDao.earliestDay(),
        ) { days, byCategory, biggest, earliest ->
            SpendPart(days, byCategory, biggest, earliest)
        }

        combine(study, spend, container.settings.settings) { s, e, settings ->
            val firstRecorded = listOfNotNull(s.earliest, e.earliest).minOrNull()
            val dayKeys = windowDays(selected, firstRecorded)
            val previousDays = previousWindowDays(selected, dayKeys)
            val studyByDay = s.days.associate { it.dayKey to it.total }
            val spendByDay = e.days.associate { it.dayKey to it.total }

            ReportUi(
                range = selected,
                dayKeys = dayKeys,
                studyByDay = studyByDay,
                spendByDay = spendByDay,
                previousStudyMs = previousDays.sumOf { studyByDay[it] ?: 0L },
                previousSpendPaise = previousDays.sumOf { spendByDay[it] ?: 0L },
                activityTotals = s.byActivity,
                categoryTotals = e.byCategory,
                sessionCount = s.sessions,
                longestSessions = s.longest,
                biggestSpends = e.biggest,
                settings = settings,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUi())

    private fun windowDays(range: ReportRange, firstRecorded: String?): List<String> {
        val today = LocalDate.now()
        val start = when {
            range.days != null -> today.minusDays(range.days - 1L)
            // All time starts at the first thing ever recorded, capped so the chart stays usable.
            firstRecorded != null -> runCatching { LocalDate.parse(firstRecorded) }
                .getOrDefault(today)
                .coerceAtLeast(today.minusYears(3))
            else -> today
        }
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .map { it.toString() }
            .toList()
    }

    /** The window of the same length ending the day before this one starts. */
    private fun previousWindowDays(range: ReportRange, current: List<String>): List<String> {
        if (range.days == null || current.isEmpty()) return emptyList()
        val start = runCatching { LocalDate.parse(current.first()) }.getOrNull() ?: return emptyList()
        val previousEnd = start.minusDays(1)
        val previousStart = previousEnd.minusDays(range.days - 1L)
        return generateSequence(previousStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(previousEnd) }
            .map { it.toString() }
            .toList()
    }
}
