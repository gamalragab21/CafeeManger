package net.marllex.cafeemanger.feature.manager.analytics.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.cafeemanger.core.model.DailyAnalytics
import kotlin.math.max

/**
 * Line Chart for Daily Revenue Trend
 */
@Composable
fun LineChart(
    data: List<DailyAnalytics>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    showPoints: Boolean = true,
    showGrid: Boolean = true
) {
    if (data.isEmpty()) return
    
    val maxRevenue = data.maxOfOrNull { it.revenue } ?: 1.0
    val minRevenue = data.minOfOrNull { it.revenue } ?: 0.0
    
    Canvas(modifier = modifier.height(200.dp).fillMaxWidth()) {
        val width = size.width
        val height = size.height
        val spacing = width / (data.size - 1).coerceAtLeast(1)
        
        // Draw grid lines
        if (showGrid) {
            val gridColor = Color.Gray.copy(alpha = 0.2f)
            for (i in 0..4) {
                val y = height * i / 4
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }
        }
        
        // Draw line
        val path = Path()
        data.forEachIndexed { index, daily ->
            val x = index * spacing
            val normalizedValue = ((daily.revenue - minRevenue) / (maxRevenue - minRevenue)).toFloat()
            val y = height - (normalizedValue * height)
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 4f)
        )
        
        // Draw points
        if (showPoints) {
            data.forEachIndexed { index, daily ->
                val x = index * spacing
                val normalizedValue = ((daily.revenue - minRevenue) / (maxRevenue - minRevenue)).toFloat()
                val y = height - (normalizedValue * height)
                
                drawCircle(
                    color = lineColor,
                    radius = 6f,
                    center = Offset(x, y)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3f,
                    center = Offset(x, y)
                )
            }
        }
    }
}

/**
 * Pie Chart for Distribution Data
 */
@Composable
fun PieChart(
    data: Map<String, Int>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true
) {
    if (data.isEmpty()) return
    
    val total = data.values.sum().toFloat()
    val angles = data.values.map { (it / total) * 360f }
    
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                
                angles.forEachIndexed { index, angle ->
                    val color = colors.getOrElse(index) { Color.Gray }
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = angle,
                        useCenter = true,
                        size = Size(size.width, size.height)
                    )
                    startAngle += angle
                }
            }
        }
        
        if (showLegend) {
            Spacer(Modifier.height(16.dp))
            PieChartLegend(
                data = data,
                colors = colors,
                total = total.toInt()
            )
        }
    }
}

@Composable
private fun PieChartLegend(
    data: Map<String, Int>,
    colors: List<Color>,
    total: Int
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        data.entries.forEachIndexed { index, entry ->
            val percentage = ((entry.value.toFloat() / total) * 100).toInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                colors.getOrElse(index) { Color.Gray },
                                CircleShape
                            )
                    )
                    Text(
                        entry.key,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    "$percentage% (${entry.value})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Bar Chart for Comparison Data
 */
@Composable
fun BarChart(
    data: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    showValues: Boolean = true
) {
    if (data.isEmpty()) return
    
    val maxValue = data.maxOfOrNull { it.second } ?: 1.0
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        data.forEach { (label, value) ->
            BarChartItem(
                label = label,
                value = value,
                maxValue = maxValue,
                barColor = barColor,
                showValue = showValues
            )
        }
    }
}

@Composable
private fun BarChartItem(
    label: String,
    value: Double,
    maxValue: Double,
    barColor: Color,
    showValue: Boolean
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (value / maxValue).toFloat(),
        animationSpec = tween(durationMillis = 1000),
        label = "bar_animation"
    )
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (showValue) {
                Text(
                    String.format("%.2f", value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(
                    barColor.copy(alpha = 0.2f),
                    RoundedCornerShape(8.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(
                        barColor,
                        RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}

/**
 * Comparison Card showing current vs previous period
 */
@Composable
fun ComparisonCard(
    title: String,
    currentValue: Double,
    previousValue: Double,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val change = currentValue - previousValue
    val percentageChange = if (previousValue > 0) {
        ((change / previousValue) * 100).toInt()
    } else 0
    
    val isPositive = change >= 0
    val changeColor = if (isPositive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        formatValue(currentValue),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Current",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${if (isPositive) "+" else ""}$percentageChange%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = changeColor
                        )
                    }
                    Text(
                        "vs Previous",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            HorizontalDivider()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Previous:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatValue(previousValue),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Animated Progress Ring
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 120.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000),
        label = "progress_animation"
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Background circle
            drawArc(
                color = backgroundColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx())
            )
            
            // Progress arc
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx())
            )
        }
        
        Text(
            "${(animatedProgress * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
