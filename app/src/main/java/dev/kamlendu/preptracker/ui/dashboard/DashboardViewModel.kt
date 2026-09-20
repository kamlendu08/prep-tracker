package dev.kamlendu.preptracker.ui.dashboard

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
import dev.kamlendu.preptracker.data.todayKey
import dev.kamlendu.preptracker.ui.lastSevenDayKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class DashboardUi(
    val dayKey: String = todayKey(),
    val todayStudyMs: Long = 0,
    val activityTotals: List<ActivityTotal> = emptyList(),
    val weekStudy: List<DayTotal> = emptyList(),
    val sessions: List<StudySession> = emptyList(),
    val todaySpendPaise: Long = 0,
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val weekSpend: List<DayTotal> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val spentAllTimePaise: Long = 0,
) {
    val totalBudgetPaise: Long get() = settings.totalBudgetPaise
    val totalRemainingPaise: Long get() = totalBudgetPaise - spentAllTimePaise
    /** How much of the whole pot is gone, capped so an overrun does not overflow the bar. */
    val totalUsedFraction: Float
        get() = if (totalBudgetPaise <= 0) 0f
        else (spentAllTimePaise.toFloat() / totalBudgetPaise.toFloat()).coerceIn(0f, 1f)

    /** Recent daily rate, from the last seven days rather than all time — the pot is being spent
     *  at today's pace, not at the average of a month that may have looked nothing like it. */
    val spendPerDayEstimatePaise: Long
        get() = if (weekSpend.isEmpty()) 0 else weekSpend.sumOf { it.total } / 7

    val daysOfRunway: Long
        get() = if (spendPerDayEstimatePaise <= 0) 0
        else (totalRemainingPaise.coerceAtLeast(0) / spendPerDayEstimatePaise)
    val remainingPaise: Long get() = settings.dailyLimitPaise - todaySpendPaise
    val overspent: Boolean get() = remainingPaise < 0
    val targetMs: Long get() = settings.dailyTargetMinutes * 60_000L
    val remainingStudyMs: Long get() = (targetMs - todayStudyMs).coerceAtLeast(0)
    val targetReached: Boolean get() = targetMs > 0 && todayStudyMs >= targetMs
}

private data class StudyPart(
    val total: Long,
    val byActivity: List<ActivityTotal>,
    val week: List<DayTotal>,
    val sessions: List<StudySession>,
)

private data class SpendPart(
    val total: Long,
    val byCategory: List<CategoryTotal>,
    val week: List<DayTotal>,
    val expenses: List<Expense>,
    val allTime: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.appContainer

    /**
     * Re-emits when the calendar day rolls over, so a dashboard left open past midnight starts
     * reporting the new day instead of freezing on yesterday's totals.
     */
    private val dayKey: Flow<String> = flow {
        while (true) {
            emit(todayKey())
            delay(30_000)
        }
    }.distinctUntilChanged()

    val ui = dayKey.flatMapLatest { day ->
        val weekFrom = lastSevenDayKeys().first()

        val study = combine(
            container.studyDao.totalForDay(day),
            container.studyDao.activityTotalsForDay(day),
            container.studyDao.dailyTotalsSince(weekFrom),
            container.studyDao.sessionsForDay(day),
        ) { total, byActivity, week, sessions -> StudyPart(total, byActivity, week, sessions) }

        val spend = combine(
            container.expenseDao.totalForDay(day),
            container.expenseDao.categoryTotalsForDay(day),
            container.expenseDao.dailyTotalsSince(weekFrom),
            container.expenseDao.forDay(day),
            container.expenseDao.totalAllTime(),
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            SpendPart(
                total = values[0] as Long,
                byCategory = values[1] as List<CategoryTotal>,
                week = values[2] as List<DayTotal>,
                expenses = values[3] as List<Expense>,
                allTime = values[4] as Long,
            )
        }

        combine(study, spend, container.settings.settings) { s, e, settings ->
            DashboardUi(
                dayKey = day,
                todayStudyMs = s.total,
                activityTotals = s.byActivity,
                weekStudy = s.week,
                sessions = s.sessions,
                todaySpendPaise = e.total,
                categoryTotals = e.byCategory,
                weekSpend = e.week,
                expenses = e.expenses,
                settings = settings,
                spentAllTimePaise = e.allTime,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUi())
}
