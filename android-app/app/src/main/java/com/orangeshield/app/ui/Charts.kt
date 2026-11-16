package com.orangeshield.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orangeshield.app.data.CallEventEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * ORANGE SHIELD — gráficos Canvas alimentados apenas por dados reais do
 * aparelho (histórico de triagens Room). Nada de valores simulados: quando
 * não há registros, o gráfico mostra o estado vazio com orientação.
 */

data class DonutSlice(val label: String, val value: Int, val color: Color)

/** Rosca animada com a distribuição real de decisões do motor. */
@Composable
fun DecisionDonut(slices: List<DonutSlice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value }
    val sweep by animateFloatAsState(
        targetValue = if (total > 0) 1f else 0f,
        animationSpec = tween(durationMillis = 950, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        label = "donutSweep"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(118.dp)) {
                val stroke = 17.dp.toPx()
                val inset = stroke / 2 + 2.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                if (total == 0) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.07f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                } else {
                    var start = -90f
                    slices.forEach { slice ->
                        if (slice.value <= 0) return@forEach
                        val sweepAngle = (slice.value.toFloat() / total) * 360f * sweep
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    slice.color.copy(alpha = 0.65f),
                                    slice.color,
                                    slice.color.copy(alpha = 0.65f)
                                ),
                                center = center
                            ),
                            startAngle = start,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )
                        start += (slice.value.toFloat() / total) * 360f * sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = total.toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightOnDark
                )
                Text(
                    text = "triagens",
                    fontSize = 10.sp,
                    color = MutedOnDark
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            slices.forEach { slice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(slice.color)
                    )
                    Text(
                        text = slice.label,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = slice.value.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = slice.color
                    )
                }
            }
        }
    }
}

private data class DayBucket(val label: String, val isToday: Boolean, val count: Int)

/** Barras animadas com o volume real de triagens por dia (últimos 7 dias). */
@Composable
fun WeekActivityChart(events: List<CallEventEntity>, modifier: Modifier = Modifier) {
    val zone = remember { ZoneId.systemDefault() }
    val dayBuckets = remember(events, zone) {
        val today = Instant.now().atZone(zone).toLocalDate()
        val labels = (6 downTo 0).map { offset -> today.minusDays(offset.toLong()) }
        val byDay = events.groupBy { event ->
            Instant.ofEpochMilli(event.occurredAt).atZone(zone).toLocalDate()
        }
        labels.map { day ->
            DayBucket(
                label = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("pt", "BR")),
                isToday = day == today,
                count = byDay[day]?.size ?: 0
            )
        }
    }
    val maxCount = dayBuckets.maxOf { it.count }.coerceAtLeast(1)
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 900, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        label = "bars"
    )
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MutedOnDark

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val bottomLabels = 18.dp.toPx()
        val chartHeight = size.height - bottomLabels
        val barSpace = 14.dp.toPx()
        val barWidth = (size.width - barSpace * (dayBuckets.size - 1)) / dayBuckets.size

        drawLine(
            color = Color.White.copy(alpha = 0.08f),
            start = Offset(0f, chartHeight),
            end = Offset(size.width, chartHeight),
            strokeWidth = 1.dp.toPx()
        )

        dayBuckets.forEachIndexed { index, bucket ->
            val x = index * (barWidth + barSpace)
            val barHeight = (bucket.count.toFloat() / maxCount) * (chartHeight - 12.dp.toPx()) * progress
            val top = chartHeight - barHeight

            if (bucket.count > 0) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(OrangeSecondary, OrangePrimary),
                        startY = top,
                        endY = chartHeight
                    ),
                    topLeft = Offset(x, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = 1.6.dp.toPx(),
                    center = Offset(x + barWidth / 2, top)
                )
            } else {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.05f),
                    topLeft = Offset(x, chartHeight - 3.dp.toPx()),
                    size = Size(barWidth, 3.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            val dayLabel = textMeasurer.measure(
                text = bucket.label,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 10.sp,
                    fontWeight = if (bucket.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (bucket.isToday) OrangePrimary else labelColor
                )
            )
            drawText(
                textLayoutResult = dayLabel,
                topLeft = Offset(x + (barWidth - dayLabel.size.width) / 2, chartHeight + 5.dp.toPx())
            )
        }
    }
}

/** Medidor circular animado do score de risco (0–100) com faixa colorida. */
@Composable
fun RiskGauge(score: Int, band: String, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = 950, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        label = "gauge"
    )
    val gaugeColor = when {
        score >= 55 -> RedBlock
        score >= 25 -> AmberWarn
        else -> EmeraldOk
    }
    val textMeasurer = rememberTextMeasurer()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(168.dp)) {
            val stroke = 15.dp.toPx()
            val inset = stroke / 2 + 3.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val startAngle = 150f
            val sweepTotal = 240f

            drawArc(
                color = Color.White.copy(alpha = 0.07f),
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            drawArc(
                brush = Brush.horizontalGradient(
                    colors = listOf(gaugeColor.copy(alpha = 0.75f), gaugeColor)
                ),
                startAngle = startAngle,
                sweepAngle = sweepTotal * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            for (tick in 0..10) {
                val tickAngle = Math.toRadians((startAngle + sweepTotal * tick / 10f).toDouble())
                val outerR = size.width / 2
                val innerR = outerR - stroke - 4.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.18f),
                    start = Offset(
                        (center.x + innerR * cos(tickAngle)).toFloat(),
                        (center.y + innerR * sin(tickAngle)).toFloat()
                    ),
                    end = Offset(
                        (center.x + outerR * cos(tickAngle)).toFloat(),
                        (center.y + outerR * sin(tickAngle)).toFloat()
                    ),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            val zeroLabel = textMeasurer.measure(
                text = "0",
                style = androidx.compose.ui.text.TextStyle(fontSize = 9.sp, color = MutedOnDark)
            )
            val zeroAngle = Math.toRadians(startAngle.toDouble())
            val zeroR = size.width / 2 - 6.dp.toPx()
            drawText(
                textLayoutResult = zeroLabel,
                topLeft = Offset(
                    center.x + zeroR * cos(zeroAngle).toFloat() - zeroLabel.size.width / 2,
                    center.y + zeroR * sin(zeroAngle).toFloat() - zeroLabel.size.height / 2
                )
            )

            val maxLabel = textMeasurer.measure(
                text = "100",
                style = androidx.compose.ui.text.TextStyle(fontSize = 9.sp, color = MutedOnDark)
            )
            val maxAngle = Math.toRadians((startAngle + sweepTotal).toDouble())
            drawText(
                textLayoutResult = maxLabel,
                topLeft = Offset(
                    center.x + zeroR * cos(maxAngle).toFloat() - maxLabel.size.width / 2,
                    center.y + zeroR * sin(maxAngle).toFloat() - maxLabel.size.height / 2
                )
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = gaugeColor
            )
            Text(
                text = band,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = gaugeColor
            )
        }
    }
}
