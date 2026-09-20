package dev.kamlendu.preptracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Slice(val label: String, val value: Float, val color: Color)

/**
 * Donut with the headline figure in the hole.
 *
 * Empty state draws the track ring rather than nothing, so a day with no data still reads as
 * "zero so far" instead of a broken widget.
 */
@Composable
fun DonutChart(
    slices: List<Slice>,
    centerTitle: String,
    modifier: Modifier = Modifier,
    centerSubtitle: String? = null,
    strokeWidth: Float = 34f,
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val progress by animateFloatAsState(
        targetValue = if (total > 0f) 1f else 0f,
        animationSpec = tween(700),
        label = "donut",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().height(168.dp)) {
            val diameter = minOf(size.width, size.height) - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )

            if (total <= 0f) return@Canvas

            // A 2° gap between slices; without it adjacent colours melt into one another.
            var start = -90f
            slices.filter { it.value > 0f }.forEach { slice ->
                val sweep = (slice.value / total) * 360f * progress
                drawArc(
                    color = slice.color,
                    startAngle = start + 1f,
                    sweepAngle = (sweep - 2f).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (centerSubtitle != null) {
                Text(
                    centerSubtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Single-value ring — spend against the day's limit. Turns red once the limit is crossed. */
@Composable
fun ProgressRing(
    fraction: Float,
    centerTitle: String,
    centerSubtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "ring",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().height(168.dp)) {
            val stroke = 34f
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTitle, style = MaterialTheme.typography.headlineSmall)
            Text(
                centerSubtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One horizontal bar split proportionally between the slices — how the day divided up, as opposed
 * to how far through it you are. Each segment is its own rounded pill, so a thin sliver still
 * reads as a segment rather than as a rendering artefact.
 */
@Composable
fun StackedBar(
    segments: List<Slice>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 14.dp,
) {
    val present = segments.filter { it.value > 0f }
    val total = present.sumOf { it.value.toDouble() }.toFloat()

    if (present.isEmpty() || total <= 0f) {
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        return
    }

    Row(
        modifier = modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        present.forEach { segment ->
            val share by animateFloatAsState(
                targetValue = segment.value / total,
                animationSpec = tween(600),
                label = "segment-${segment.label}",
            )
            Box(
                Modifier
                    .weight(share.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(height / 2))
                    .background(segment.color),
            )
        }
    }
}

data class Bar(val label: String, val value: Float, val highlight: Boolean = false)

/**
 * Seven-day bar strip. Bars are drawn as rounded boxes rather than on a Canvas so the labels stay
 * in the normal text pipeline — they need to respect font scale.
 */
@Composable
fun BarStrip(
    bars: List<Bar>,
    accent: Color,
    valueLabel: (Float) -> String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 120.dp,
    /** Draws a line across the chart at this value — the daily target, or the daily limit. */
    reference: Float? = null,
    referenceLabel: String? = null,
) {
    // The reference line has to be inside the scale, or a target you never reached would sit off
    // the top of the chart and tell you nothing.
    val max = maxOf(
        bars.maxOfOrNull { it.value } ?: 0f,
        reference ?: 0f,
    ).takeIf { it > 0f } ?: 1f

    if (reference != null && reference > 0f) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 14.dp, height = 2.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                referenceLabel ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            val fraction by animateFloatAsState(
                targetValue = (bar.value / max).coerceIn(0f, 1f),
                animationSpec = tween(600),
                label = "bar-${bar.label}",
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (bar.value > 0f) valueLabel(bar.value) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height * fraction.coerceAtLeast(if (bar.value > 0f) 0.04f else 0f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(accent, accent.copy(alpha = if (bar.highlight) 0.9f else 0.55f)),
                                )
                            ),
                    )
                    if (reference != null && reference > 0f) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                // Capped just below the top: a target nobody reached sits at the
                                // ceiling, and a line flush with the edge is a line you cannot see.
                                .padding(bottom = height * (reference / max).coerceIn(0f, 0.97f))
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Two fixed lines, so "Sun 13" and "Tue 15" do not wrap differently and leave the
                // labels sitting at different heights.
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (bar.highlight) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

@Composable
fun LegendRow(items: List<Slice>, valueLabel: (Float) -> String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(item.color),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    valueLabel(item.value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}
