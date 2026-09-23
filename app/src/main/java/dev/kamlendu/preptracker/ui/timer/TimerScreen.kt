package dev.kamlendu.preptracker.ui.timer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.os.PowerManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kamlendu.preptracker.data.StudyActivity
import dev.kamlendu.preptracker.data.StudySession
import dev.kamlendu.preptracker.data.todayKey
import dev.kamlendu.preptracker.timer.TimerEngine
import dev.kamlendu.preptracker.timer.TimerFace
import dev.kamlendu.preptracker.timer.TimerService
import dev.kamlendu.preptracker.timer.formatDuration
import dev.kamlendu.preptracker.timer.formatHoursMinutes
import dev.kamlendu.preptracker.ui.theme.ActivityColors
import dev.kamlendu.preptracker.ui.timeOfDay
import dev.kamlendu.preptracker.ui.theme.TimerPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.min

@Composable
fun TimerScreen(contentPadding: PaddingValues) {
    val state by TimerEngine.state.collectAsStateWithLifecycle()
    if (state.isActive) StopwatchFace(contentPadding) else ActivityPicker(contentPadding)
}

/**
 * The signed-in Timer tab when nothing is running: what to start, and everything already logged.
 *
 * The list is the study counterpart of the Money tab — the place to correct a mistake, or to pick
 * a sitting back up where it left off.
 */
@Composable
private fun ActivityPicker(
    contentPadding: PaddingValues,
    viewModel: TimerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val undoable by viewModel.undoable.collectAsStateWithLifecycle()
    var addingManual by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<StudySession?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(undoable) {
        val removed = undoable ?: return@LaunchedEffect
        val timeout = launch {
            delay(3_000)
            snackbarHostState.currentSnackbarData?.dismiss()
        }
        val result = snackbarHostState.showSnackbar(
            message = "Removed ${formatHoursMinutes(removed.durationMs)} " +
                StudyActivity.from(removed.activity).label.lowercase(),
            actionLabel = "Undo",
            duration = SnackbarDuration.Indefinite,
        )
        timeout.cancel()
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.forgetUndo()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 24.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                Text(
                    "What are you sitting down to do?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "The clock is tagged with this, so the dashboard can tell lectures from practice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))

                ActivityOption(StudyActivity.LECTURE, Icons.Filled.MenuBook, "Watching or reading a lecture") {
                    TimerService.start(context, it)
                }
                Spacer(Modifier.height(12.dp))
                ActivityOption(StudyActivity.PRACTICE, Icons.Filled.Edit, "Solving questions") {
                    TimerService.start(context, it)
                }
                Spacer(Modifier.height(12.dp))
                ActivityOption(StudyActivity.TEST, Icons.Filled.Timer, "Full-length or sectional test") {
                    TimerService.start(context, it)
                }

                Spacer(Modifier.height(14.dp))
                TextButton(
                    onClick = { addingManual = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Studied without the timer? Add it by hand")
                }
                Spacer(Modifier.height(10.dp))
            }

            if (sessions.isEmpty()) {
                item {
                    Text(
                        "Sittings you log will appear here, ready to pick back up or remove.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            val grouped = sessions.groupBy { it.dayKey }
            grouped.forEach { (day, ofDay) ->
                item(key = "header-$day") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dayHeading(day),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatHoursMinutes(ofDay.sumOf { it.durationMs }),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(ofDay, key = { it.id }) { session ->
                    SessionCard(session) { selected = session }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding() + 12.dp),
        )
    }

    if (addingManual) {
        ManualSessionDialog(
            onDismiss = { addingManual = false },
            onSave = { activity, minutes ->
                viewModel.addManualSession(activity, minutes)
                addingManual = false
            },
        )
    }

    selected?.let { session ->
        SessionActionsDialog(
            session = session,
            onDismiss = { selected = null },
            onResume = {
                TimerService.resumeLogged(context, session.id)
                selected = null
            },
            onDelete = {
                viewModel.delete(session)
                selected = null
            },
        )
    }
}

@Composable
private fun SessionCard(session: StudySession, onClick: () -> Unit) {
    val activity = StudyActivity.from(session.activity)
    val color = ActivityColors[StudyActivity.entries.indexOf(activity) % ActivityColors.size]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(activity.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                timeOfDay(session.startedAt) + if (session.manual) " · added by hand" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatHoursMinutes(session.durationMs),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SessionActionsDialog(
    session: StudySession,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val activity = StudyActivity.from(session.activity)
    // Only today's sittings can be resumed: study time is filed by day, so adding this evening's
    // hour to a sitting from last Tuesday would credit it to last Tuesday.
    val resumable = session.dayKey == todayKey()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${activity.label} · ${formatHoursMinutes(session.durationMs)}") },
        text = {
            Column {
                Text(
                    "Started ${timeOfDay(session.startedAt)} on ${dayHeading(session.dayKey).lowercase()}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (resumable) {
                        "Picking it back up continues this same sitting — the clock starts from " +
                            "${formatHoursMinutes(session.durationMs)} and the entry grows rather " +
                            "than a second one appearing."
                    } else {
                        "Only today's sittings can be picked back up, because study time is " +
                            "recorded against the day it happened."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (resumable) {
                Button(onClick = onResume) { Text("Resume") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

private fun dayHeading(dayKey: String): String {
    if (dayKey == todayKey()) return "Today"
    if (dayKey == LocalDate.now().minusDays(1).toString()) return "Yesterday"
    return runCatching {
        LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }.getOrDefault(dayKey)
}

@Composable
private fun ManualSessionDialog(
    onDismiss: () -> Unit,
    onSave: (StudyActivity, Int) -> Unit,
) {
    var activity by remember { mutableStateOf(StudyActivity.LECTURE) }
    var minutes by remember { mutableStateOf("") }
    val parsed = minutes.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a sitting") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudyActivity.entries.forEach { option ->
                        FilterChip(
                            selected = option == activity,
                            onClick = { activity = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                    label = { Text("Minutes studied") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(activity, parsed!!) },
                enabled = parsed != null && parsed > 0,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ActivityOption(
    activity: StudyActivity,
    icon: ImageVector,
    subtitle: String,
    onPick: (StudyActivity) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onPick(activity) }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(activity.label, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The Giant Stopwatch face: black to the edges, grey digits as large as the screen allows,
 * nothing else competing for attention. Tapping anywhere pauses and resumes — at a study desk the
 * phone is at arm's length, and hunting for a small button is itself a distraction.
 */
@Composable
private fun StopwatchFace(
    contentPadding: PaddingValues,
    viewModel: TimerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by TimerEngine.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // The whole face takes its colour from one palette, so the digits, the paused label and the
    // Stop button's outline can never drift apart.
    val palette = TimerPalette.from(settings.timerPalette)
    var elapsed by remember { mutableLongStateOf(state.displayMs()) }

    // Render on a tick, but read the value from the clock — never accumulate the tick itself.
    // The wake-up lands exactly on the next second boundary: one wake per second instead of five,
    // which matters on a screen that is deliberately kept awake for hours.
    LaunchedEffect(state.running, state.accumulatedMs, state.resumedAtElapsed, state.carriedMs) {
        elapsed = state.displayMs()
        while (state.running) {
            delay(1_000L - (elapsed % 1_000L))
            elapsed = state.displayMs()
        }
    }

    KeepScreenOn(enabled = settings.keepScreenOn)
    ImmersiveMode()
    RotateWithTheDevice()
    PauseWhenLeaving()

    val text = formatDuration(elapsed)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (state.running) TimerService.pause(context) else TimerService.resume(context)
            },
    ) {
        // Shown while paused only. Reserving space for it while running would cost the digits
        // exactly that much height, and the label is the one thing on this screen you already know.
        AnimatedVisibility(
            visible = !state.running,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = contentPadding.calculateTopPadding() + 20.dp),
        ) {
            Text(
                state.activity?.label?.uppercase() ?: "",
                color = palette.paused,
                fontSize = 13.sp,
                letterSpacing = 3.sp,
            )
        }

        GiantDigits(
            text = text,
            color = when {
                !state.running -> palette.paused
                settings.dimScreen -> palette.dimmed
                else -> palette.bright
            },
            // Room for the controls only once they are actually on screen, so a running clock
            // gets the whole canvas.
            // Nothing is reserved while the clock runs: no padding, no room held for a label.
            // The controls get their space back only once it is paused.
            bottomReserve = if (state.running) 0.dp else 150.dp,
            topReserve = 0.dp,
            modifier = Modifier.fillMaxSize(),
        )

        // The controls belong to the paused state. While the clock runs there is nothing on the
        // screen but the time — which is the entire point of this face.
        AnimatedVisibility(
            visible = !state.running,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding() + 32.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (state.recoveredFromGap) {
                        "PAUSED — THE APP LOST TRACK OF THIS SITTING.\nONLY THE MEASURED TIME WAS KEPT."
                    } else {
                        "PAUSED — TAP ANYWHERE TO RESUME"
                    },
                    color = palette.paused,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(18.dp))
                OutlinedButton(
                    onClick = { TimerService.stop(context) },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, palette.paused),
                ) {
                    Text("Stop & log", color = palette.bright)
                }
            }
        }

        // A single hint on the first few seconds of a sitting, then out of the way for good.
        FirstRunHint(running = state.running, elapsed = elapsed, hintColor = palette.paused)
    }
}

/**
 * Digits scaled to actually fill the canvas.
 *
 * The size is derived by measuring the string at a reference size and scaling by the ratio that
 * fits — guessing from a character-width constant leaves a visible margin, which is what made the
 * first version look small. Monospace, so the layout does not twitch when a 1 replaces an 8.
 */
@Composable
private fun GiantDigits(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    bottomReserve: Dp = 0.dp,
    topReserve: Dp = 0.dp,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val availableWidth = constraints.maxWidth.toFloat()
        val availableHeight = with(density) {
            (maxHeight - bottomReserve - topReserve).toPx()
        }.coerceAtLeast(1f)

        val style = TextStyle(
            fontSize = REFERENCE_SIZE,
            fontWeight = CLOCK_WEIGHT,
            fontFamily = CLOCK_FAMILY,
            platformStyle = CLOCK_PLATFORM_STYLE,
            lineHeight = REFERENCE_SIZE,
        )
        // Only the length changes the fit, so this is not recomputed every second.
        val fontSize = remember(text.length, availableWidth, availableHeight) {
            val measured = measurer.measure(
                text = AnnotatedString(text),
                style = style,
                maxLines = 1,
                softWrap = false,
            )
            val scale = minOf(
                availableWidth / measured.size.width.toFloat(),
                availableHeight / measured.size.height.toFloat(),
            )
            (REFERENCE_SIZE.value * scale).sp
        }

        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = CLOCK_WEIGHT,
            fontFamily = CLOCK_FAMILY,
            style = TextStyle(platformStyle = CLOCK_PLATFORM_STYLE, lineHeight = fontSize),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(bottom = bottomReserve / 2, top = topReserve / 2),
        )
    }
}

private val REFERENCE_SIZE = 100.sp

/**
 * Between the two: monospace Bold was too light, the default family at Black filled less of the
 * screen because its heavier glyphs are wider, so the width-fit came out smaller. ExtraBold on the
 * default family sits in between — and its digits are uniform width, which was the only reason
 * monospace was here at all.
 */
private val CLOCK_WEIGHT = FontWeight.ExtraBold
private val CLOCK_FAMILY = FontFamily.Default

/**
 * Font padding is the reserved space above the ascender and below the descender. For a line of
 * prose it is what stops lines colliding; for one line of digits meant to fill a screen it is pure
 * margin, and measuring with it included made the clock shrink to fit space nothing occupies.
 */
private val CLOCK_PLATFORM_STYLE = PlatformTextStyle(includeFontPadding = false)

@Composable
private fun BoxScope.FirstRunHint(running: Boolean, elapsed: Long, hintColor: Color) {
    AnimatedVisibility(
        visible = running && elapsed < 6_000,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 36.dp),
    ) {
        Text(
            "TAP ANYWHERE TO PAUSE",
            color = hintColor,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        )
    }
}

/**
 * The whole point is to glance at the clock from across the desk, so the screen must not sleep.
 *
 * Dimming is deliberately *not* done here any more. A window brightness override is an absolute
 * level, not a reduction: asking for 0.35 on a phone already at 15% in a dark room makes the
 * screen brighter, which is the opposite of what the setting promises. Dimming the digits
 * themselves is both honest to the label and, on an OLED panel where each pixel is its own light,
 * the thing that actually saves the battery.
 */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/**
 * Stops the clock when you leave the face **with the screen still on**.
 *
 * Home button, another app, another tab: you are doing something other than studying, and a clock
 * that kept running would report time nobody spent. Two triggers because they catch different
 * exits — the lifecycle event covers leaving the app, `onDispose` covers switching tabs inside it,
 * where the activity never pauses.
 *
 * Locking the phone is deliberately **not** one of them any more. Pressing power backgrounds the
 * app exactly like opening another app does, so the two are told apart by whether the screen is
 * still awake: a dark screen is a lock, and reading from paper with the phone locked beside you is
 * studying. [TimerService] handles what happens when it is unlocked again.
 *
 * Resuming from the notification stays exempt: that is an explicit instruction to keep counting
 * while the phone is used for something else.
 */
@Composable
private fun PauseWhenLeaving() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val power = context.getSystemService(PowerManager::class.java)
        // Defaults to true if the service is somehow unavailable, which keeps the old, safer
        // behaviour of pausing rather than counting time that was not studied.
        fun screenStillOn() = power?.isInteractive ?: true

        fun leave() {
            TimerFace.visible = false
            if (TimerEngine.state.value.running && screenStillOn()) {
                runCatching { TimerService.pause(context) }
            }
        }

        // Set here, not only on ON_RESUME: the face can enter composition while the activity is
        // already resumed, in which case no lifecycle event follows.
        TimerFace.visible = true

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> TimerFace.visible = true
                Lifecycle.Event.ON_PAUSE -> leave()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            leave()
        }
    }
}

/**
 * Turns with the phone even when auto-rotate is locked.
 *
 * A locked-to-portrait phone is the normal setting, but a stopwatch laid on its side on a desk is
 * exactly the case the lock should not win — so the timer face asks for sensor orientation, which
 * overrides the user's rotation preference for this screen only, and hands control back on exit.
 */
@Composable
private fun RotateWithTheDevice() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose {
            activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

/** Status and navigation bars hidden while the face is up — black really means black. */
@Composable
private fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
