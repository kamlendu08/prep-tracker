package dev.kamlendu.preptracker.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kamlendu.preptracker.sync.SyncEngine
import dev.kamlendu.preptracker.timer.FocusMode
import dev.kamlendu.preptracker.ui.components.SectionCard
import dev.kamlendu.preptracker.ui.formatRupees
import dev.kamlendu.preptracker.ui.theme.AccentOk
import dev.kamlendu.preptracker.ui.theme.AccentOver
import dev.kamlendu.preptracker.widget.WidgetPinner
import java.math.BigDecimal
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var editingLimit by remember { mutableStateOf(false) }
    var confirmingSignOut by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf(false) }
    var editingTotal by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    // Permission screens are separate system activities, so the only reliable moment to re-read
    // them is when this screen comes back to the foreground.
    var dndGranted by remember { mutableStateOf(FocusMode.isGranted(context)) }
    var smsGranted by remember { mutableStateOf(hasSmsPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dndGranted = FocusMode.isGranted(context)
                smsGranted = hasSmsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        smsGranted = granted.values.all { it }
        if (smsGranted) {
            scope.launch {
                val result = viewModel.backfillFromSms()
                status = buildString {
                                    append("Scanned ${result.scanned} messages, imported ${result.imported} spends.")
                                    if (result.duplicatesRemoved > 0) {
                                        append(" Removed ${result.duplicatesRemoved} duplicate")
                                        if (result.duplicatesRemoved > 1) append("s")
                                        append(".")
                                    }
                                }
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionCard(title = "Account") {
                Text(
                    account.email ?: "Not signed in",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    syncDescription(syncState, account.lastSyncAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (syncState) {
                        is SyncEngine.State.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sittings and spends are kept on the GATE platform under this account, so they " +
                        "survive reinstalling the app. Message text is never uploaded.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::syncNow,
                        enabled = syncState !is SyncEngine.State.Running,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (syncState is SyncEngine.State.Running) "Syncing…" else "Sync now")
                    }
                    TextButton(onClick = { confirmingSignOut = true }) { Text("Sign out") }
                }
            }
        }

        item {
            SectionCard(title = "Daily budget") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            formatRupees(settings.dailyLimitPaise),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "Everything spent past this shows as overspend on the dashboard.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { editingLimit = true }) { Text("Change") }
                }
            }
        }

        item {
            SectionCard(title = "Overall budget") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (settings.totalBudgetPaise > 0) {
                                formatRupees(settings.totalBudgetPaise)
                            } else {
                                "Not set"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "The whole amount set aside for these months. The dashboard shows how " +
                                "much of it is gone and roughly how long it will last.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { editingTotal = true }) {
                        Text(if (settings.totalBudgetPaise > 0) "Change" else "Set")
                    }
                }
            }
        }

        item {
            SectionCard(title = "Daily study target") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            formatTarget(settings.dailyTargetMinutes),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "The dashboard and the home-screen widget count down to this.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { editingTarget = true }) { Text("Change") }
                }
            }
        }

        item {
            SectionCard(title = "While the timer runs") {
                ToggleRow(
                    title = "Focus mode",
                    subtitle = "Silences everything except calls for the length of the sitting, then " +
                        "puts Do Not Disturb back the way it was.",
                    checked = settings.focusModeEnabled,
                    onChange = viewModel::setFocusMode,
                )
                Spacer(Modifier.height(12.dp))
                ToggleRow(
                    title = "Keep the screen awake",
                    subtitle = "The clock stays visible on the desk instead of the screen sleeping.",
                    checked = settings.keepScreenOn,
                    onChange = viewModel::setKeepScreenOn,
                )
                Spacer(Modifier.height(12.dp))
                ToggleRow(
                    title = "Dim the clock",
                    subtitle = "The backlight is what drains the battery on a screen held awake for " +
                        "hours. Turn this off if the desk is in daylight.",
                    checked = settings.dimScreen,
                    onChange = viewModel::setDimScreen,
                )
                if (settings.focusModeEnabled && !dndGranted) {
                    Spacer(Modifier.height(12.dp))
                    PermissionRow(
                        granted = false,
                        label = "Do Not Disturb access is needed for focus mode",
                        action = "Grant",
                        onClick = { context.startActivity(FocusMode.requestAccessIntent()) },
                    )
                }
            }
        }

        item {
            SectionCard(title = "Automatic expense capture") {
                ToggleRow(
                    title = "Read spends from bank SMS",
                    subtitle = "Bank messages are matched for debits. Nothing is uploaded — the " +
                        "text is read and discarded on the phone.",
                    checked = settings.autoCaptureEnabled,
                    onChange = viewModel::setAutoCapture,
                )
                Spacer(Modifier.height(14.dp))
                PermissionRow(
                    granted = smsGranted,
                    label = if (smsGranted) {
                        "SMS access granted"
                    } else {
                        "SMS access — needed to read bank messages"
                    },
                    action = if (smsGranted) "Import last 30 days" else "Grant",
                    onClick = {
                        if (smsGranted) {
                            scope.launch {
                                val result = viewModel.backfillFromSms()
                                status = buildString {
                                    append("Scanned ${result.scanned} messages, imported ${result.imported} spends.")
                                    if (result.duplicatesRemoved > 0) {
                                        append(" Removed ${result.duplicatesRemoved} duplicate")
                                        if (result.duplicatesRemoved > 1) append("s")
                                        append(".")
                                    }
                                }
                            }
                        } else {
                            smsLauncher.launch(
                                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                            )
                        }
                    },
                )
                status?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionCard(title = "Home screen") {
                Text(
                    "A widget showing today's study time against your target, and today's spending " +
                        "against the daily limit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                PermissionRow(
                    granted = true,
                    label = "Put the widget on the home screen",
                    action = "Add",
                    onClick = {
                        if (!WidgetPinner.requestPin(context)) {
                            status = "This launcher cannot add it directly — long-press the home " +
                                "screen, open Widgets, and drag Prep Tracker across."
                        }
                    },
                )
            }
        }

        item {
            SectionCard(title = "How the numbers are worked out") {
                Text(
                    "Study time counts only while the clock is running — pauses are excluded, and a " +
                        "sitting under a minute is discarded. Spends come from bank SMS only: " +
                        "watching app notifications and email too meant one purchase arrived as " +
                        "several messages that could not be told apart. Anything paid without an " +
                        "SMS — a wallet balance, say — needs adding by hand from the Money tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "This phone's copy is cleared on sign-out. Anything not yet uploaded is synced " +
                        "first, and signing back in restores everything."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.signOut(); confirmingSignOut = false }) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSignOut = false }) { Text("Cancel") }
            },
        )
    }

    if (editingTotal) {
        AmountDialog(
            title = "Overall budget",
            label = "Total rupees",
            supporting = "Everything recorded counts towards this. Set it to 0 to hide the card.",
            currentPaise = settings.totalBudgetPaise,
            onDismiss = { editingTotal = false },
            onSave = { viewModel.setTotalBudget(it); editingTotal = false },
        )
    }

    if (editingTarget) {
        TargetDialog(
            currentMinutes = settings.dailyTargetMinutes,
            onDismiss = { editingTarget = false },
            onSave = { viewModel.setDailyTarget(it); editingTarget = false },
        )
    }

    if (editingLimit) {
        AmountDialog(
            title = "Daily spending limit",
            label = "Rupees per day",
            currentPaise = settings.dailyLimitPaise,
            onDismiss = { editingLimit = false },
            onSave = { viewModel.setDailyLimit(it); editingLimit = false },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermissionRow(granted: Boolean, label: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted) AccentOk else AccentOver,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(action, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AmountDialog(
    title: String,
    label: String,
    currentPaise: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
    supporting: String? = null,
) {
    var text by remember { mutableStateOf((currentPaise / 100).toString()) }
    val paise = runCatching { BigDecimal(text).movePointRight(2).toLong() }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (supporting != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        supporting,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(paise!!) }, enabled = paise != null && paise >= 0) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TargetDialog(currentMinutes: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var hours by remember { mutableStateOf((currentMinutes / 60).toString()) }
    var minutes by remember { mutableStateOf((currentMinutes % 60).toString()) }
    val total = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily study target") },
        text = {
            Row {
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it.filter(Char::isDigit).take(2) },
                    label = { Text("Hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                    label = { Text("Minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(total) }, enabled = total in 1..(24 * 60)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatTarget(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

private fun syncDescription(state: SyncEngine.State, lastSyncAt: Long): String = when (state) {
    is SyncEngine.State.Running -> "Syncing…"
    is SyncEngine.State.Failed -> state.message
    else -> if (lastSyncAt > 0) "Last synced ${relativeTime(lastSyncAt)}" else "Not synced yet"
}

private fun relativeTime(at: Long): String {
    val minutes = (System.currentTimeMillis() - at) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}

private fun hasSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED
