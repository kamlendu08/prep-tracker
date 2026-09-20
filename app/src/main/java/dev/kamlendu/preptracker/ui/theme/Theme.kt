package dev.kamlendu.preptracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The stopwatch face. Deliberately grey, not white: at 3am on a desk, white glares. */
val TimerGrey = Color(0xFF8C8C8C)
/** "Dim the clock" on: still readable across a desk, a good deal less light on an OLED panel. */
val TimerGreyDimmed = Color(0xFF565656)
val TimerGreyDim = Color(0xFF4A4A4A)

val AccentStudy = Color(0xFF7AC0FF)
val AccentSpend = Color(0xFFFFB86B)
val AccentOver = Color(0xFFFF6B6B)
val AccentOk = Color(0xFF6BD68A)

/** One colour per activity, used by every chart and chip so the legend never has to be re-learnt. */
val ActivityColors = listOf(
    Color(0xFF7AC0FF), // Lecture
    Color(0xFFB39DFF), // Practice
    Color(0xFF6BD68A), // Test
)

val CategoryColors = listOf(
    Color(0xFFFFB86B),
    Color(0xFF7AC0FF),
    Color(0xFFB39DFF),
    Color(0xFF6BD68A),
    Color(0xFFFF8FA3),
    Color(0xFF8AD5D5),
    Color(0xFFD5C68A),
    Color(0xFF9AA0A6),
)

private val DarkColors = darkColorScheme(
    primary = AccentStudy,
    onPrimary = Color(0xFF00243D),
    secondary = AccentSpend,
    background = Color(0xFF0B0B0E),
    onBackground = Color(0xFFE6E6EA),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE6E6EA),
    surfaceVariant = Color(0xFF1C1C23),
    onSurfaceVariant = Color(0xFFA8A8B3),
    outline = Color(0xFF2E2E38),
    error = AccentOver,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B63A8),
    background = Color(0xFFF7F7FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEDF2),
    outline = Color(0xFFD8D8E0),
    error = Color(0xFFC0392B),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun PrepTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
