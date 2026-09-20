package dev.kamlendu.preptracker.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kamlendu.preptracker.data.Expense
import dev.kamlendu.preptracker.data.ExpenseCategory
import dev.kamlendu.preptracker.data.ExpenseSource
import dev.kamlendu.preptracker.data.todayKey
import dev.kamlendu.preptracker.ui.formatRupees
import dev.kamlendu.preptracker.ui.formatRupeesExact
import dev.kamlendu.preptracker.ui.theme.AccentOk
import dev.kamlendu.preptracker.ui.theme.AccentOver
import dev.kamlendu.preptracker.ui.theme.CategoryColors
import dev.kamlendu.preptracker.ui.timeOfDay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun MoneyScreen(
    contentPadding: PaddingValues,
    viewModel: MoneyViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val undoable by viewModel.undoable.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // A three-second offer, then the row is gone for good. Long enough to catch the wrong tap,
    // short enough that it is not still sitting there when you have moved on.
    LaunchedEffect(undoable) {
        val removed = undoable ?: return@LaunchedEffect
        val job = launch {
            delay(3_000)
            snackbarHostState.currentSnackbarData?.dismiss()
        }
        val result = snackbarHostState.showSnackbar(
            message = "Removed ${formatRupees(removed.amountPaise)}" +
                (removed.merchant?.let { " · $it" } ?: ""),
            actionLabel = "Undo",
            duration = SnackbarDuration.Indefinite,
        )
        job.cancel()
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.forgetUndo()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        "Spent today",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatRupees(ui.todaySpendPaise),
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Text(
                        if (ui.remainingPaise >= 0) {
                            "${formatRupees(ui.remainingPaise)} left today"
                        } else {
                            "${formatRupees(-ui.remainingPaise)} over the limit"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ui.remainingPaise >= 0) AccentOk else AccentOver,
                    )
                }
            }

            if (ui.recent.isEmpty()) {
                item {
                    Text(
                        "Nothing recorded yet. Grant SMS access in Settings to capture bank messages " +
                            "automatically, or add a spend by hand with the + button.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            // Grouped by day so a scroll back through the week reads as a diary, not a wall.
            val grouped = ui.recent.groupBy { it.dayKey }
            grouped.forEach { (day, expenses) ->
                item(key = "header-$day") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dayHeading(day),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            // Same rule as the day total: a card-bill settlement is money moving,
                            // not money spent, so it is not added up here either.
                            formatRupees(
                                expenses
                                    .filter { it.category != ExpenseCategory.CARD_BILL.id }
                                    .sumOf { it.amountPaise }
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(expenses, key = { it.id }) { expense ->
                    ExpenseCard(expense) { editing = expense }
                }
            }
        }

        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 20.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add a spend by hand")
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding() + 12.dp),
        )
    }

    if (adding) {
        ManualExpenseDialog(
            onDismiss = { adding = false },
            onSave = { paise, merchant, category ->
                viewModel.addManual(paise, merchant, category, System.currentTimeMillis())
                adding = false
            },
        )
    }

    editing?.let { expense ->
        EditExpenseDialog(
            expense = expense,
            onDismiss = { editing = null },
            onCategory = { viewModel.setCategory(expense, it); editing = null },
            onDelete = { viewModel.delete(expense); editing = null },
        )
    }
}

@Composable
private fun ExpenseCard(expense: Expense, onClick: () -> Unit) {
    val category = ExpenseCategory.from(expense.category)
    val color = CategoryColors[ExpenseCategory.entries.indexOf(category) % CategoryColors.size]
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
            Text(
                expense.merchant ?: category.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                buildString {
                    append(category.label)
                    append(" · ")
                    append(timeOfDay(expense.occurredAt))
                    append(" · ")
                    append(ExpenseSource.from(expense.source).label)
                },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualExpenseDialog(
    onDismiss: () -> Unit,
    onSave: (Long, String?, ExpenseCategory) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.FOOD) }
    val paise = rupeesToPaise(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a spend") },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Spent on (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                CategoryChips(selected = category, onSelect = { category = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(paise!!, merchant, category) },
                enabled = paise != null && paise > 0,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditExpenseDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onCategory: (ExpenseCategory) -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(formatRupeesExact(expense.amountPaise)) },
        text = {
            Column {
                Text(
                    expense.merchant ?: "No merchant read from the message",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (expense.rawText != null) {
                    Spacer(Modifier.height(8.dp))
                    // The original text is kept so a wrong reading can be judged, not just trusted.
                    Text(
                        expense.rawText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                CategoryChips(
                    selected = ExpenseCategory.from(expense.category),
                    onSelect = onCategory,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: ExpenseCategory, onSelect: (ExpenseCategory) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpenseCategory.entries.forEach { category ->
            FilterChip(
                selected = category == selected,
                onClick = { onSelect(category) },
                label = { Text(category.label) },
            )
        }
    }
}

private fun rupeesToPaise(input: String): Long? = runCatching {
    if (input.isBlank()) return null
    BigDecimal(input).movePointRight(2).toLong()
}.getOrNull()

private fun dayHeading(dayKey: String): String {
    if (dayKey == todayKey()) return "Today"
    if (dayKey == LocalDate.now().minusDays(1).toString()) return "Yesterday"
    return runCatching {
        LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }.getOrDefault(dayKey)
}
