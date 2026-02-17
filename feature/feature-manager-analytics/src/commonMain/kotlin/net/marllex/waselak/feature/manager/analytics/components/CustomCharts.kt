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
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(48.dp),
                )
                Box(modifier = Modifier.weight(1f).height(16.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val fraction = (value / maxValue).toFloat().coerceIn(0f, 1f)
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.15f),
                            size = Size(size.width, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                        )
                        drawRoundRect(
                            color = barColor,
                            size = Size(size.width * fraction, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                        )
                    }
                }
                Text(
                    text = formatCurrency(value),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 8.dp).width(60.dp),
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
    ) {
        Canvas(modifier = Modifier.size(80.dp)) {
            val strokeWidth = 16.dp.toPx()
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
        Spacer(Modifier.width(16.dp))
        Column {
            segments.forEachIndexed { index, (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(8.dp)) {
                        drawCircle(color = colors[index % colors.size])
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$label (${String.format("%.1f", (value / total) * 100)}%)",
                        style = MaterialTheme.typography.labelSmall,
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

    Column(modifier = modifier.fillMaxWidth()) {
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        dayNames.forEachIndexed { dayIndex, dayName ->
            Row(
                modifier = Modifier.fillMaxWidth().height(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dayName,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(32.dp),
                )
                Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val cellWidth = size.width / 24f
                    for (hour in 0..23) {
                        val count = data.find { it.first == dayIndex + 1 && it.second == hour }?.third ?: 0
                        val alpha = if (maxCount > 0) (count.toFloat() / maxCount).coerceIn(0.05f, 1f) else 0.05f
                        drawRect(
                            color = baseColor.copy(alpha = alpha),
                            topLeft = Offset(hour * cellWidth, 0f),
                            size = Size(cellWidth - 1f, size.height),
                        )
                    }
                }
            }
        }
        // Hour labels
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(32.dp))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("0", "6", "12", "18", "23").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
