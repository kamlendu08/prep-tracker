package dev.kamlendu.preptracker.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kamlendu.preptracker.ui.theme.AccentOk
import dev.kamlendu.preptracker.ui.theme.AccentSpend
import dev.kamlendu.preptracker.ui.theme.AccentStudy

/**
 * The first screen anyone sees.
 *
 * It has one job: say what the app does clearly enough that granting it notification and SMS
 * access a minute later feels reasonable rather than alarming — which is why the privacy line is
 * on this screen and not buried in settings.
 */
@Composable
fun LandingScreen(onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                // A single cool glow behind the title, fading to near-black. The rest of the app
                // is flat dark, so this is the one place with any depth to it.
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1B2740), Color(0xFF0B0B0E)),
                    center = Offset(240f, 220f),
                    radius = 1500f,
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
        ) {
            Spacer(Modifier.height(72.dp))

            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AccentStudy.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = AccentStudy,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(Modifier.height(22.dp))
            Text(
                "Every hour,\nevery rupee.",
                style = MaterialTheme.typography.displayLarge,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF2F2F5),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "A study clock and a spending log for the months before the exam — so you know " +
                    "what you actually put in, not what it felt like.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp,
            )

            Spacer(Modifier.height(34.dp))

            Feature(
                icon = Icons.Filled.Timer,
                tint = AccentStudy,
                title = "A clock that only counts real study",
                body = "Full-screen stopwatch, tagged by what you are doing. It pauses the moment " +
                    "you leave it — a call, the lock button, another app.",
            )
            Feature(
                icon = Icons.Filled.DoNotDisturbOn,
                tint = AccentOk,
                title = "Silence for the length of a sitting",
                body = "Do Not Disturb goes on with calls still allowed through, and goes back " +
                    "exactly as it was when you stop.",
            )
            Feature(
                icon = Icons.Filled.CurrencyRupee,
                tint = AccentSpend,
                title = "Spending read from your bank messages",
                body = "Set a daily limit and watch what is left of it. Read from your bank's " +
                    "SMS, on the phone — the messages themselves never leave it.",
            )
            Feature(
                icon = Icons.Filled.BarChart,
                tint = Color(0xFFB39DFF),
                title = "The week and the month, at a glance",
                body = "Days on target, days over budget, where the hours went — plus a home " +
                    "screen widget you see every time you unlock the phone.",
            )

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onCreateAccount,
                shape = RoundedCornerShape(15.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Create an account", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("I already have an account")
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Bank SMS is read on the phone and never uploaded. What syncs to your account is " +
                    "the amount, the payee and the date.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, tint: Color, title: String, body: String) {
    Row(Modifier.padding(bottom = 20.dp)) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFE8E8EE),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                body,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp,
            )
        }
    }
}
