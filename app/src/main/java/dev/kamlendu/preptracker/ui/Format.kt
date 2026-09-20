package dev.kamlendu.preptracker.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** ₹1,250 — whole rupees. Paise are stored but never shown; they are noise on a daily budget. */
fun formatRupees(paise: Long): String {
    val rupees = paise / 100.0
    val formatter = java.text.NumberFormat.getIntegerInstance(Locale("en", "IN"))
    return "₹" + formatter.format(Math.round(rupees))
}

/** ₹1,250.50 — used where the exact figure matters, such as a single transaction row. */
fun formatRupeesExact(paise: Long): String {
    val formatter = java.text.NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = if (paise % 100L == 0L) 0 else 2
        maximumFractionDigits = 2
    }
    return "₹" + formatter.format(paise / 100.0)
}

fun shortDayLabel(dayKey: String): String =
    runCatching {
        LocalDate.parse(dayKey).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3)
    }.getOrDefault("")

fun timeOfDay(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("h:mm a")
        .format(java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()))

/** "₹2.1k" — bar labels have no room for "₹2,056". */
fun compactRupees(paise: Long): String {
    val rupees = paise / 100
    return when {
        rupees >= 100_000 -> "₹%.1fL".format(rupees / 100_000.0)
        rupees >= 1_000 -> "₹%.1fk".format(rupees / 1_000.0)
        else -> "₹$rupees"
    }
}

/** "1h 20m" compressed to "1.3h" for a bar label; minutes stay as minutes. */
fun compactHours(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "%.1fh".format(minutes / 60.0) else "${minutes}m"
}

/** "Sat 19" — a weekday alone does not say which week you are looking at. */
fun dayWithDate(dayKey: String): String =
    runCatching {
        LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEE\nd"))
    }.getOrDefault(dayKey)

fun lastSevenDayKeys(): List<String> {
    val today = LocalDate.now()
    return (6 downTo 0).map { today.minusDays(it.toLong()).toString() }
}
