package dev.kamlendu.preptracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kamlendu.preptracker.data.Expense
import dev.kamlendu.preptracker.data.ExpenseCategory
import dev.kamlendu.preptracker.data.StudyActivity
import dev.kamlendu.preptracker.data.StudySession
import dev.kamlendu.preptracker.timer.formatHoursMinutes
import dev.kamlendu.preptracker.ui.components.Bar
import dev.kamlendu.preptracker.ui.components.BarStrip
import dev.kamlendu.preptracker.ui.components.LegendRow
import dev.kamlendu.preptracker.ui.components.ProgressRing
import dev.kamlendu.preptracker.ui.components.SectionCard
import dev.kamlendu.preptracker.ui.components.Slice
import dev.kamlendu.preptracker.ui.components.StackedBar
import dev.kamlendu.preptracker.ui.formatRupees
import dev.kamlendu.preptracker.ui.formatRupeesExact
import dev.kamlendu.preptracker.ui.lastSevenDayKeys
import dev.kamlendu.preptracker.ui.shortDayLabel
import dev.kamlendu.preptracker.ui.theme.AccentOk
import dev.kamlendu.preptracker.ui.theme.AccentOver
import dev.kamlendu.preptracker.ui.theme.AccentSpend
import dev.kamlendu.preptracker.ui.theme.AccentStudy
import dev.kamlendu.preptracker.ui.theme.ActivityColors
import dev.kamlendu.preptracker.ui.theme.CategoryColors
import dev.kamlendu.preptracker.ui.timeOfDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    onStartTimer: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: DashboardViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header(ui) }
        item { StudyCard(ui, onStartTimer) }
        item { SpendCard(ui) }
        if (ui.totalBudgetPaise > 0) item { OverallBudgetCard(ui) }
        item { WeekStudyCard(ui) }
        item { WeekSpendCard(ui) }

        if (ui.sessions.isNotEmpty()) {
            item {
                SectionCard(title = "Today's sittings") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ui.sessions.forEach { SessionRow(it) }
                    }
                }
            }
        }

        if (ui.expenses.isNotEmpty()) {
            item {
                SectionCard(title = "Today's spending") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ui.expenses.take(6).forEach { ExpenseRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(ui: DashboardUi) {
    val date = runCatching {
        LocalDate.parse(ui.dayKey).format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
    }.getOrDefault(ui.dayKey)

    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(
            date,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        // Just the headline figure. The countdown to the target and the budget remainder both
        // live in their own cards below, where they sit next to the chart they belong to.
        Text(
            formatHoursMinutes(ui.todayStudyMs) + " studied",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun StudyCard(ui: DashboardUi, onStartTimer: () -> Unit) {
    val slices = StudyActivity.entries.mapIndexed { index, activity ->
        Slice(
            label = activity.label,
            value = ui.activityTotals.firstOrNull { it.activity == activity.id }?.total?.toFloat() ?: 0f,
            color = ActivityColors[index % ActivityColors.size],
        )
    }

    SectionCard(
        title = "Study today",
        trailing = {
            if (ui.sessions.isNotEmpty()) {
                Text(
                    "${ui.sessions.size} sitting${if (ui.sessions.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        // A donut of three zeroes says nothing, and squeezing a button in beside it was what made
        // the first screen of a fresh install look broken. Empty day gets its own layout.
        if (ui.todayStudyMs == 0L) {
            Text(
                "No sitting logged yet today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Target: ${formatHoursMinutes(ui.targetMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStartTimer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start a sitting")
            }
            return@SectionCard
        }

        // The ring answers "how far into the day's target am I", exactly like the spend ring
        // answers it for the budget. How the time divided up is a different question, and it is
        // the bar underneath that answers it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                fraction = if (ui.targetMs > 0) ui.todayStudyMs.toFloat() / ui.targetMs.toFloat() else 0f,
                centerTitle = formatHoursMinutes(ui.todayStudyMs),
                centerSubtitle = if (ui.targetMs > 0) "of ${formatHoursMinutes(ui.targetMs)}" else "no target",
                color = if (ui.targetReached) AccentOk else AccentStudy,
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                LegendRow(items = slices, valueLabel = { formatHoursMinutes(it.toLong()) })
            }
        }

        Spacer(Modifier.height(16.dp))
        StackedBar(segments = slices)
        if (ui.targetMs > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (ui.targetReached) {
                    "Target reached"
                } else {
                    "${formatHoursMinutes(ui.remainingStudyMs)} to go"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (ui.targetReached) AccentOk else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpendCard(ui: DashboardUi) {
    val limit = ui.settings.dailyLimitPaise.coerceAtLeast(1)
    val fraction = ui.todaySpendPaise.toFloat() / limit.toFloat()
    val categories = ui.categoryTotals.mapIndexed { index, total ->
        Slice(
            label = ExpenseCategory.from(total.category).label,
            value = total.total.toFloat(),
            color = CategoryColors[index % CategoryColors.size],
        )
    }

    SectionCard(title = "Spend today") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                fraction = fraction,
                centerTitle = formatRupees(ui.todaySpendPaise),
                centerSubtitle = "of ${formatRupees(ui.settings.dailyLimitPaise)}",
                color = when {
                    ui.overspent -> AccentOver
                    fraction > 0.75f -> AccentSpend
                    else -> AccentOk
                },
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                if (categories.isEmpty()) {
                    Text(
                        "Nothing spent yet today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LegendRow(items = categories.take(5), valueLabel = { formatRupees(it.toLong()) })
                }
            }
        }
    }
}

/**
 * The whole pot, not the day's allowance: how much of the money set aside for these months is
 * gone. A daily limit says whether today went well; this says how long the runway is.
 */
@Composable
private fun OverallBudgetCard(ui: DashboardUi) {
    val over = ui.totalRemainingPaise < 0
    val nearlyGone = !over && ui.totalUsedFraction >= 0.8f
    val color = when {
        over -> AccentOver
        nearlyGone -> AccentSpend
        else -> AccentOk
    }

    SectionCard(
        title = "Overall budget",
        trailing = {
            Text(
                "of ${formatRupees(ui.totalBudgetPaise)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatRupees(ui.spentAllTimePaise),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "spent",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ui.totalUsedFraction)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(color),
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            if (over) {
                "${formatRupees(-ui.totalRemainingPaise)} over the overall budget"
            } else {
                "${formatRupees(ui.totalRemainingPaise)} left · " +
                    "${(ui.totalUsedFraction * 100).toInt()}% used"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (over) AccentOver else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!over && ui.spendPerDayEstimatePaise > 0) {
            Text(
                "About ${ui.daysOfRunway} days left at your recent rate",
                style = MaterialTheme.typography.labelMedium,
                color = if (nearlyGone) AccentSpend else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun WeekStudyCard(ui: DashboardUi) {
    val days = lastSevenDayKeys()
    val byDay = ui.weekStudy.associate { it.dayKey to it.total }
    val bars = days.map { key ->
        Bar(
            label = shortDayLabel(key),
            value = (byDay[key] ?: 0L) / 3_600_000f,
            highlight = key == ui.dayKey,
        )
    }
    val weekTotal = days.sumOf { byDay[it] ?: 0L }

    SectionCard(
        title = "Last 7 days — study",
        trailing = {
            Text(
                formatHoursMinutes(weekTotal),
                style = MaterialTheme.typography.labelMedium,
                color = AccentStudy,
            )
        },
    ) {
        BarStrip(bars = bars, accent = AccentStudy, valueLabel = { "%.1f".format(it) })
    }
}

@Composable
private fun WeekSpendCard(ui: DashboardUi) {
    val days = lastSevenDayKeys()
    val byDay = ui.weekSpend.associate { it.dayKey to it.total }
    val bars = days.map { key ->
        Bar(
            label = shortDayLabel(key),
            value = (byDay[key] ?: 0L) / 100f,
            highlight = key == ui.dayKey,
        )
    }
    val weekTotal = days.sumOf { byDay[it] ?: 0L }
    val overDays = days.count { (byDay[it] ?: 0L) > ui.settings.dailyLimitPaise }

    SectionCard(
        title = "Last 7 days — spend",
        trailing = {
            Text(
                formatRupees(weekTotal),
                style = MaterialTheme.typography.labelMedium,
                color = AccentSpend,
            )
        },
    ) {
        BarStrip(bars = bars, accent = AccentSpend, valueLabel = { "%.0f".format(it) })
        if (overDays > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Over the limit on $overDays of the last 7 days.",
                style = MaterialTheme.typography.labelMedium,
                color = AccentOver,
            )
        }
    }
}

@Composable
private fun SessionRow(session: StudySession) {
    val activity = StudyActivity.from(session.activity)
    val color = ActivityColors[StudyActivity.entries.indexOf(activity) % ActivityColors.size]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(activity.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                timeOfDay(session.startedAt) + (if (session.manual) " · added by hand" else ""),
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
private fun ExpenseRow(expense: Expense) {
    val category = ExpenseCategory.from(expense.category)
    val color = CategoryColors[ExpenseCategory.entries.indexOf(category) % CategoryColors.size]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                expense.merchant ?: category.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                category.label + " · " + timeOfDay(expense.occurredAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatRupeesExact(expense.amountPaise),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}
