package dev.kamlendu.preptracker.ui.expenses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kamlendu.preptracker.appContainer
import dev.kamlendu.preptracker.data.AppSettings
import dev.kamlendu.preptracker.data.Expense
import dev.kamlendu.preptracker.data.ExpenseCategory
import dev.kamlendu.preptracker.data.ExpenseSource
import dev.kamlendu.preptracker.data.dayKeyOf
import dev.kamlendu.preptracker.data.todayKey
import dev.kamlendu.preptracker.widget.WidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MoneyUi(
    val todaySpendPaise: Long = 0,
    val recent: List<Expense> = emptyList(),
    val settings: AppSettings = AppSettings(),
) {
    val remainingPaise: Long get() = settings.dailyLimitPaise - todaySpendPaise
}

@OptIn(ExperimentalCoroutinesApi::class)
class MoneyViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.appContainer

    /** Ticks over at midnight so "spent today" does not stay stuck on yesterday. */
    private val dayKey = flow {
        while (true) {
            emit(todayKey())
            delay(30_000)
        }
    }.distinctUntilChanged()

    val ui = dayKey.flatMapLatest { day ->
        combine(
            container.expenseDao.totalForDay(day),
            container.expenseDao.recent(200),
            container.settings.settings,
        ) { total, recent, settings -> MoneyUi(total, recent, settings) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyUi())

    fun addManual(amountPaise: Long, merchant: String?, category: ExpenseCategory, occurredAt: Long) {
        viewModelScope.launch {
            container.expenseDao.insert(
                Expense(
                    dayKey = dayKeyOf(occurredAt),
                    amountPaise = amountPaise,
                    merchant = merchant?.takeIf { it.isNotBlank() },
                    category = category.id,
                    occurredAt = occurredAt,
                    source = ExpenseSource.MANUAL.id,
                    // Hand entries are never duplicates of each other: the timestamp makes each unique.
                    fingerprint = "manual:$occurredAt:$amountPaise",
                    categoryLocked = true,
                )
            )
            refreshWidgetAfter()
        }
    }

    fun setCategory(expense: Expense, category: ExpenseCategory) {
        viewModelScope.launch {
            container.expenseDao.setCategory(expense.id, category.id)
            refreshWidgetAfter()
        }
    }

    /** The row just removed, held only long enough for the undo offer to expire. */
    private val _undoable = MutableStateFlow<Expense?>(null)
    val undoable = _undoable.asStateFlow()

    fun delete(expense: Expense) {
        viewModelScope.launch {
            container.expenseDao.delete(expense.id)
            _undoable.value = expense
            refreshWidgetAfter()
        }
    }

    fun undoDelete() {
        val expense = _undoable.value ?: return
        _undoable.value = null
        viewModelScope.launch {
            container.expenseDao.restore(expense.id)
            refreshWidgetAfter()
        }
    }

    fun forgetUndo() {
        _undoable.value = null
    }

    private fun refreshWidgetAfter() = WidgetUpdater.refresh(getApplication())
}
