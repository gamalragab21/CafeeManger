package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*

/**
 * Simple horizontal bar chart using Canvas.
 */
@Composable
fun SimpleBarChart(
    items: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (items.isEmpty()) return
    val maxValue = items.maxOf { it.second }.coerceAtLeast(1.0)

    Column(modifier = modifier.fillMaxWidth()) {
        items.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(52.dp),
                    maxLines = 1,
                )
                Box(modifier = Modifier.weight(1f).height(20.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val fraction = (value / maxValue).toFloat().coerceIn(0f, 1f)
                        val radius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.1f),
                            size = Size(size.width, size.height),
                            cornerRadius = radius,
                        )
                        if (fraction > 0f) {
                            drawRoundRect(
                                color = barColor.copy(alpha = 0.8f),
                                size = Size(size.width * fraction, size.height),
                                cornerRadius = radius,
                            )
                        }
                    }
                }
                Text(
                    text = formatCurrency(value),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp).width(72.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Simple donut chart using Canvas.
 */
@Composable
fun DonutChart(
    segments: List<Pair<String, Float>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) return
    val total = segments.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(100.dp)) {
            val strokeWidth = 20.dp.toPx()
            var startAngle = -90f
            segments.forEachIndexed { index, (_, value) ->
                val sweep = (value / total) * 360f
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                )
                startAngle += sweep
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            segments.forEachIndexed { index, (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(color = colors[index % colors.size])
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$label (${String.format(java.util.Locale.US, "%.1f", (value / total) * 100)}%)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * Heatmap grid using Canvas.
 */
@Composable
fun HeatmapChart(
    data: List<Triple<Int, Int, Int>>, // dayOfWeek, hour, count
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return
    val maxCount = data.maxOf { it.third }.coerceAtLeast(1)
    val baseColor = MaterialTheme.colorScheme.primary

    // Resolve day label strings at the composable level (before Canvas/drawScope)
    val dayNames = listOf(
        stringResource(Res.string.day_mon),
        stringResource(Res.string.day_tue),
        stringResource(Res.string.day_wed),
        stringResource(Res.string.day_thu),
        stringResource(Res.string.day_fri),
        stringResource(Res.string.day_sat),
        stringResource(Res.string.day_sun),
    )

    Column(modifier = modifier.fillMaxWidth()) {
        dayNames.forEachIndexed { dayIndex, dayName ->
            Row(
                modifier = Modifier.fillMaxWidth().height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp),
                )
                Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val cellWidth = size.width / 24f
                    for (hour in 0..23) {
                        val count = data.find { it.first == dayIndex + 1 && it.second == hour }?.third ?: 0
                        val alpha = if (maxCount > 0) (count.toFloat() / maxCount).coerceIn(0.05f, 1f) else 0.05f
                        drawRoundRect(
                            color = baseColor.copy(alpha = alpha),
                            topLeft = Offset(hour * cellWidth + 0.5f, 1f),
                            size = Size(cellWidth - 1f, size.height - 2f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // Hour labels
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(36.dp))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("0", "6", "12", "18", "23").forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
