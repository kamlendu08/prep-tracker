package dev.kamlendu.preptracker.ui.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kamlendu.preptracker.data.ExpenseCategory
import dev.kamlendu.preptracker.data.StudyActivity
import dev.kamlendu.preptracker.timer.formatHoursMinutes
import dev.kamlendu.preptracker.ui.components.Bar
import dev.kamlendu.preptracker.ui.components.BarStrip
import dev.kamlendu.preptracker.ui.components.LegendRow
import dev.kamlendu.preptracker.ui.components.SectionCard
import dev.kamlendu.preptracker.ui.components.Slice
import dev.kamlendu.preptracker.ui.components.StackedBar
import dev.kamlendu.preptracker.ui.dayWithDate
import dev.kamlendu.preptracker.ui.formatRupees
import dev.kamlendu.preptracker.ui.formatRupeesExact
import dev.kamlendu.preptracker.ui.theme.AccentOk
import dev.kamlendu.preptracker.ui.theme.AccentOver
import dev.kamlendu.preptracker.ui.theme.AccentSpend
import dev.kamlendu.preptracker.ui.theme.AccentStudy
import dev.kamlendu.preptracker.ui.theme.ActivityColors
import dev.kamlendu.preptracker.ui.theme.CategoryColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ReportsScreen(
    contentPadding: PaddingValues,
    viewModel: ReportsViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

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
            // Six ranges do not fit across a phone, so the row scrolls rather than wraps.
            Row(
                Modifier
                    .padding(top = 8.dp, bottom = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportRange.entries.forEach { option ->
                    FilterChip(
                        selected = ui.range == option,
                        onClick = { viewModel.setRange(option) },
                        label = { Text(option.label) },
                    )
                }
            }
        }

        item { StudyReport(ui) }
        item { WeekdayReport(ui) }
        item { SpendReport(ui) }

        if (ui.longestSessions.isNotEmpty()) {
            item {
                SectionCard(title = "Longest sittings") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ui.longestSessions.forEach { session ->
                            StatRow(
                                left = StudyActivity.from(session.activity).label,
                                caption = prettyDate(session.dayKey),
                                right = formatHoursMinutes(session.durationMs),
                            )
                        }
                    }
                }
            }
        }

        if (ui.biggestSpends.isNotEmpty()) {
            item {
                SectionCard(title = "Biggest spends") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ui.biggestSpends.forEach { expense ->
                            StatRow(
                                left = expense.merchant ?: ExpenseCategory.from(expense.category).label,
                                caption = prettyDate(expense.dayKey),
                                right = formatRupeesExact(expense.amountPaise),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyReport(ui: ReportUi) {
    val slices = StudyActivity.entries.mapIndexed { index, activity ->
        Slice(
            label = activity.label,
            value = ui.activityTotals.firstOrNull { it.activity == activity.id }?.total?.toFloat() ?: 0f,
            color = ActivityColors[index % ActivityColors.size],
        )
    }
    val buckets = bucket(ui.dayKeys, ui.studyByDay, ui.range)

    SectionCard(
        title = "Study — ${ui.range.label.lowercase()}",
        trailing = {
            Text(
                formatHoursMinutes(ui.studyTotalMs),
                style = MaterialTheme.typography.labelMedium,
                color = AccentStudy,
            )
        },
    ) {
        Row(Modifier.fillMaxWidth()) {
            Figure("Per day", formatHoursMinutes(ui.studyPerDayMs), Modifier.weight(1f))
            Figure("On a study day", formatHoursMinutes(ui.studyPerActiveDayMs), Modifier.weight(1f))
            Figure("Best day", formatHoursMinutes(ui.bestDayMs), Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Figure(
                "Studied",
                "${ui.daysStudied}/${ui.dayKeys.size} days",
                Modifier.weight(1f),
            )
            Figure(
                "On target",
                "${ui.daysOnTarget}/${ui.dayKeys.size}",
                Modifier.weight(1f),
                valueColor = if (ui.daysOnTarget > 0) AccentOk else null,
            )
            Figure(
                "Streak",
                "${ui.currentStreak}d · best ${ui.longestStreak}d",
                Modifier.weight(1f),
                valueColor = if (ui.currentStreak > 0) AccentOk else null,
            )
        }

        Spacer(Modifier.height(18.dp))
        ChartCaption(bucketCaption("Hours studied", ui.range))
        Spacer(Modifier.height(8.dp))
        BarStrip(
            bars = buckets.map { Bar(it.label, it.total / 3_600_000f, it.containsToday) },
            accent = AccentStudy,
            valueLabel = { hoursLabel(it) },
            height = 96.dp,
            reference = ui.targetMs.takeIf { it > 0 }
                ?.let { (it / 3_600_000f) * buckets.firstOrNull()?.days.orZero() },
            referenceLabel = ui.targetMs.takeIf { it > 0 }?.let {
                targetCaption(it, buckets.firstOrNull()?.days ?: 1)
            },
        )

        Spacer(Modifier.height(20.dp))
        ChartCaption("Where the ${formatHoursMinutes(ui.studyTotalMs)} went")
        Spacer(Modifier.height(8.dp))
        StackedBar(segments = slices)
        Spacer(Modifier.height(12.dp))
        LegendRow(items = slices, valueLabel = { formatHoursMinutes(it.toLong()) })

        Spacer(Modifier.height(14.dp))
        InsightLine(
            text = "${ui.sessionCount} sittings across ${ui.daysStudied} of ${ui.dayKeys.size} days",
        )
        ui.studyTrendPercent?.let { trend ->
            InsightLine(
                text = if (trend >= 0) {
                    "$trend% more than the previous ${ui.range.label.lowercase()}"
                } else {
                    "${-trend}% less than the previous ${ui.range.label.lowercase()}"
                },
                good = trend >= 0,
            )
        }
    }
}

/** Which days of the week actually get used — the pattern a daily total cannot show. */
@Composable
private fun WeekdayReport(ui: ReportUi) {
    val byWeekday = ui.byWeekday
    if (byWeekday.isEmpty()) return
    val order = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
    )
    val best = byWeekday.maxByOrNull { it.value }
    val worst = byWeekday.filterValues { it >= 0 }.minByOrNull { it.value }

    SectionCard(title = "Which days you study") {
        ChartCaption("Average hours, by day of the week")
        Spacer(Modifier.height(8.dp))
        BarStrip(
            bars = order.map { day ->
                Bar(
                    label = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                    value = (byWeekday[day] ?: 0L) / 3_600_000f,
                    highlight = day == LocalDate.now().dayOfWeek,
                )
            },
            accent = AccentStudy,
            valueLabel = { hoursLabel(it) },
            height = 80.dp,
            reference = ui.targetMs.takeIf { it > 0 }?.let { it / 3_600_000f },
            referenceLabel = ui.targetMs.takeIf { it > 0 }
                ?.let { "daily target ${formatHoursMinutes(it)}" },
        )
        if (best != null && best.value > 0) {
            Spacer(Modifier.height(12.dp))
            InsightLine(
                "Strongest on ${dayName(best.key)} — ${formatHoursMinutes(best.value)} on average",
                good = true,
            )
            if (worst != null && worst.key != best.key) {
                InsightLine(
                    "Weakest on ${dayName(worst.key)} — ${formatHoursMinutes(worst.value)} on average",
                    good = false,
                )
            }
        }
    }
}

@Composable
private fun SpendReport(ui: ReportUi) {
    val slices = ui.categoryTotals.mapIndexed { index, total ->
        Slice(
            label = ExpenseCategory.from(total.category).label,
            value = total.total.toFloat(),
            color = CategoryColors[index % CategoryColors.size],
        )
    }
    val buckets = bucket(ui.dayKeys, ui.spendByDay, ui.range)
    val underBudget = ui.budgetForPeriodPaise - ui.spendTotalPaise

    SectionCard(
        title = "Money — ${ui.range.label.lowercase()}",
        trailing = {
            Text(
                formatRupees(ui.spendTotalPaise),
                style = MaterialTheme.typography.labelMedium,
                color = AccentSpend,
            )
        },
    ) {
        Row(Modifier.fillMaxWidth()) {
            Figure("Per day", formatRupees(ui.spendPerDayPaise), Modifier.weight(1f))
            Figure("Budget", formatRupees(ui.budgetForPeriodPaise), Modifier.weight(1f))
            Figure(
                "Over limit",
                "${ui.daysOverLimit}/${ui.dayKeys.size}",
                Modifier.weight(1f),
                valueColor = if (ui.daysOverLimit > 0) AccentOver else AccentOk,
            )
        }

        Spacer(Modifier.height(18.dp))
        ChartCaption(bucketCaption("Spent", ui.range))
        Spacer(Modifier.height(8.dp))
        BarStrip(
            bars = buckets.map { Bar(it.label, it.total / 100f, it.containsToday) },
            accent = AccentSpend,
            valueLabel = { rupeeLabel(it) },
            height = 96.dp,
            reference = (ui.settings.dailyLimitPaise / 100f) * buckets.firstOrNull()?.days.orZero(),
            referenceLabel = limitCaption(
                ui.settings.dailyLimitPaise,
                buckets.firstOrNull()?.days ?: 1,
            ),
        )

        if (slices.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            ChartCaption("Where the ${formatRupees(ui.spendTotalPaise)} went")
            Spacer(Modifier.height(8.dp))
            StackedBar(segments = slices)
            Spacer(Modifier.height(12.dp))
            LegendRow(items = slices.take(6), valueLabel = { formatRupees(it.toLong()) })
        }

        Spacer(Modifier.height(14.dp))
        InsightLine(
            text = if (underBudget >= 0) {
                "${formatRupees(underBudget)} under budget over the period"
            } else {
                "${formatRupees(-underBudget)} over budget over the period"
            },
            good = underBudget >= 0,
        )
        ui.spendTrendPercent?.let { trend ->
            InsightLine(
                text = if (trend >= 0) {
                    "$trend% more than the previous ${ui.range.label.lowercase()}"
                } else {
                    "${-trend}% less than the previous ${ui.range.label.lowercase()}"
                },
                // Spending more is the unwelcome direction here — the opposite of study hours.
                good = trend < 0,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bucketing: a chart is only readable up to a dozen or so bars, so a long range
// is grouped — into blocks of days up to three months, into calendar months
// beyond that, where "Sep" says more than "1-9".
// ---------------------------------------------------------------------------

private data class Bucket(
    val label: String,
    val total: Long,
    val days: Int,
    val containsToday: Boolean,
)

private const val MAX_BARS = 12

private fun bucket(
    dayKeys: List<String>,
    totals: Map<String, Long>,
    range: ReportRange,
): List<Bucket> {
    if (dayKeys.isEmpty()) return emptyList()
    val today = LocalDate.now().toString()

    if (range.bucketByMonth) {
        return dayKeys
            .groupBy { it.substring(0, 7) }
            .toSortedMap()
            .map { (month, days) ->
                Bucket(
                    label = monthLabel(month),
                    total = days.sumOf { totals[it] ?: 0L },
                    days = days.size,
                    containsToday = days.contains(today),
                )
            }
            .takeLast(MAX_BARS)
    }

    val size = maxOf(1, Math.ceil(dayKeys.size / MAX_BARS.toDouble()).toInt())
    return dayKeys.chunked(size).map { block ->
        Bucket(
            label = if (size == 1) {
                dayWithDate(block.first())
            } else {
                "${dayOfMonth(block.first())}-${dayOfMonth(block.last())}"
            },
            total = block.sumOf { totals[it] ?: 0L },
            days = block.size,
            containsToday = block.contains(today),
        )
    }
}

private fun bucketCaption(what: String, range: ReportRange): String = when {
    range.bucketByMonth -> "$what each month"
    range == ReportRange.WEEK -> "$what each day"
    else -> "$what, grouped by a few days at a time"
}

private fun targetCaption(targetMs: Long, days: Int): String =
    if (days <= 1) {
        "daily target ${formatHoursMinutes(targetMs)}"
    } else {
        "target for $days days · ${formatHoursMinutes(targetMs * days)}"
    }

private fun limitCaption(limitPaise: Long, days: Int): String =
    if (days <= 1) {
        "daily limit ${formatRupees(limitPaise)}"
    } else {
        "limit for $days days · ${formatRupees(limitPaise * days)}"
    }

@Composable
private fun ChartCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InsightLine(text: String, good: Boolean? = null) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = when (good) {
            true -> AccentOk
            false -> AccentOver
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun Figure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatRow(left: String, caption: String, right: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(left, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(right, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun Int?.orZero(): Float = (this ?: 1).toFloat()

private fun hoursLabel(hours: Float): String =
    if (hours >= 1f) "%.1fh".format(hours) else "${(hours * 60).toInt()}m"

private fun rupeeLabel(rupees: Float): String = when {
    rupees >= 1_000f -> "₹%.1fk".format(rupees / 1_000f)
    else -> "₹%.0f".format(rupees)
}

private fun dayOfMonth(dayKey: String): String =
    runCatching { LocalDate.parse(dayKey).dayOfMonth.toString() }.getOrDefault("")

private fun monthLabel(yearMonth: String): String =
    runCatching {
        LocalDate.parse("$yearMonth-01").format(DateTimeFormatter.ofPattern("MMM"))
    }.getOrDefault(yearMonth)

private fun dayName(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.FULL, Locale.getDefault())

private fun prettyDate(dayKey: String): String =
    runCatching {
        LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }.getOrDefault(dayKey)
