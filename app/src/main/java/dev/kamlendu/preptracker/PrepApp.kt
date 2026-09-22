package dev.kamlendu.preptracker

import android.app.Application
import android.content.Context
import dev.kamlendu.preptracker.data.AppDatabase
import dev.kamlendu.preptracker.data.SettingsStore
import dev.kamlendu.preptracker.sync.AuthStore
import dev.kamlendu.preptracker.timer.FocusMode
import dev.kamlendu.preptracker.timer.TimerEngine

/**
 * Manual dependency container. The app has exactly one database, one settings store and one
 * timer — a DI framework here would add a build step and annotation processing to wire up three
 * objects that never vary.
 */
class AppContainer(context: Context) {
    private val db = AppDatabase.get(context)
    val studyDao = db.studyDao()
    val expenseDao = db.expenseDao()
    val revisionDao = db.revisionDao()
    val settings = SettingsStore(context.applicationContext)
    val auth = AuthStore(context.applicationContext)
}

class PrepApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // A session may still be running from before the process was killed; restore it before
        // any screen reads the timer state.
        TimerEngine.restore(this)
        // And if focus mode outlived the session that turned it on, give the phone its sound back.
        FocusMode.restoreIfOrphaned(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PrepApp).container
