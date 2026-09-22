package dev.kamlendu.preptracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dev.kamlendu.preptracker.sync.SyncEngine
import dev.kamlendu.preptracker.timer.TimerEngine
import dev.kamlendu.preptracker.timer.TimerService
import dev.kamlendu.preptracker.ui.dashboard.DashboardScreen
import dev.kamlendu.preptracker.ui.auth.AuthFlow
import dev.kamlendu.preptracker.ui.expenses.MoneyScreen
import dev.kamlendu.preptracker.ui.reports.ReportsScreen
import dev.kamlendu.preptracker.ui.revise.ReviseScreen
import dev.kamlendu.preptracker.ui.settings.SettingsScreen
import dev.kamlendu.preptracker.ui.theme.PrepTrackerTheme
import dev.kamlendu.preptracker.ui.timer.TimerScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    DASHBOARD("Today", Icons.Filled.SpaceDashboard),
    TIMER("Timer", Icons.Filled.Timer),
    REVISE("Revise", Icons.Filled.MenuBook),
    MONEY("Money", Icons.Filled.CurrencyRupee),
    REPORTS("Reports", Icons.Filled.BarChart),
    SETTINGS("Settings", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {

    /**
     * Re-attaches a running session to its foreground service. Doing it here rather than in
     * Application.onCreate matters twice over: the app is provably in the foreground (Android 12+
     * refuses background foreground-service starts), and it also covers coming back from a kill
     * that happened while the app sat in the background.
     */
    override fun onStart() {
        super.onStart()
        TimerService.ensure(this)
        // Coming to the foreground is the moment worth syncing on: it is when new rows have
        // usually accumulated, and the one moment the network is reliably allowed.
        lifecycleScope.launch { SyncEngine.syncNow(this@MainActivity) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PrepTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val account by appContainer.auth.account
                        .collectAsStateWithLifecycle(initialValue = null)
                    when {
                        // Still reading the stored account — a flash of the login screen here
                        // would be a lie about being signed out.
                        account == null -> Unit
                        account?.signedIn == true -> AppScaffold()
                        else -> AuthFlow()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppScaffold() {
    var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
    val timerState by TimerEngine.state.collectAsStateWithLifecycle()

    NotificationPermissionOnce()

    // The stopwatch face owns the whole screen while a sitting is running — a nav bar glowing at
    // the bottom of a black screen is exactly the clutter the design is avoiding.
    val fullBleed = tab == Tab.TIMER && timerState.isActive

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!fullBleed) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Icon(entry.icon, contentDescription = entry.label) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                Tab.DASHBOARD -> DashboardScreen(
                    onStartTimer = { tab = Tab.TIMER },
                    contentPadding = padding,
                )
                Tab.TIMER -> TimerScreen(contentPadding = padding)
                Tab.REVISE -> ReviseScreen(contentPadding = padding)
                Tab.MONEY -> MoneyScreen(contentPadding = padding)
                Tab.REPORTS -> ReportsScreen(contentPadding = padding)
                Tab.SETTINGS -> SettingsScreen(contentPadding = padding)
            }
        }
    }
}

/**
 * Asked once, on first launch. Without it the foreground-service notification is silently
 * suppressed on Android 13+, and with it the stopwatch loses its controls in the shade.
 */
@Composable
private fun NotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
