package dev.kamlendu.preptracker.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kamlendu.preptracker.appContainer
import dev.kamlendu.preptracker.data.AppSettings
import dev.kamlendu.preptracker.expense.SmsImporter
import dev.kamlendu.preptracker.sync.Account
import dev.kamlendu.preptracker.sync.SyncEngine
import dev.kamlendu.preptracker.widget.WidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.appContainer

    val settings = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val account = container.auth.account
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Account())

    val syncState = SyncEngine.state

    fun syncNow() = viewModelScope.launch { SyncEngine.syncNow(getApplication()) }

    /**
     * Signs out and clears the phone's copy.
     *
     * The clear is the point: leaving one account's sittings behind for the next person to sign in
     * would silently merge two people's records. A final sync runs first so nothing still waiting
     * to upload is thrown away.
     */
    fun signOut() = viewModelScope.launch {
        SyncEngine.syncNow(getApplication())
        container.studyDao.clearAll()
        container.expenseDao.clearAll()
        container.auth.signOut()
        WidgetUpdater.refresh(getApplication())
    }

    fun setDailyLimit(paise: Long) = viewModelScope.launch {
        container.settings.setDailyLimit(paise)
        WidgetUpdater.refresh(getApplication())
    }

    fun setTotalBudget(paise: Long) = viewModelScope.launch {
        container.settings.setTotalBudget(paise)
        WidgetUpdater.refresh(getApplication())
    }

    fun setDailyTarget(minutes: Int) = viewModelScope.launch {
        container.settings.setDailyTarget(minutes)
        WidgetUpdater.refresh(getApplication())
    }
    fun setFocusMode(on: Boolean) = viewModelScope.launch { container.settings.setFocusMode(on) }
    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { container.settings.setKeepScreenOn(on) }
    fun setDimScreen(on: Boolean) = viewModelScope.launch { container.settings.setDimScreen(on) }
    fun setAutoCapture(on: Boolean) = viewModelScope.launch { container.settings.setAutoCapture(on) }

    suspend fun backfillFromSms(): SmsImporter.Result =
        SmsImporter.importLastMonth(getApplication())
}
