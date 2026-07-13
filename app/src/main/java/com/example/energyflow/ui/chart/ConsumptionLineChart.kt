package com.example.energyflow.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.energyflow.ui.theme.DarkCard
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.utils.Formatters
import java.time.format.DateTimeFormatter

@Composable
fun ConsumptionLineChart(
    consumptions: List<DailyConsumption>,
    showCost: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (consumptions.isEmpty()) return

    val chartColor = ElectricColor
    val gridColor = DarkCard.copy(alpha = 0.5f)
    val labelColor = TextSecondary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        val paddingLeft = 50f
        val paddingBottom = 40f
        val paddingTop = 16f
        val paddingRight = 16f

        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom

        val maxValue = consumptions.maxOf { it.dailyConsumption }.coerceAtLeast(1.0)
        val minValue = 0.0
        val valueRange = maxValue - minValue

        // Draw grid lines
        drawGridLines(
            topLeft = Offset(paddingLeft, paddingTop),
            width = chartWidth,
            height = chartHeight,
            gridColor = gridColor,
            maxValue = maxValue,
            labelColor = labelColor,
            showCost = showCost
        )

        // Draw the line
        val path = Path()
        val pointCount = consumptions.size
        val xStep = if (pointCount > 1) chartWidth / (pointCount - 1) else chartWidth

        consumptions.forEachIndexed { index, consumption ->
            val x = paddingLeft + index * xStep
            val y = paddingTop + chartHeight - ((consumption.dailyConsumption - minValue) / valueRange * chartHeight).toFloat()

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Draw the line with glow effect
        drawPath(
            path = path,
            color = chartColor.copy(alpha = 0.3f),
            style = Stroke(
                width = 6f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawPath(
            path = path,
            color = chartColor,
            style = Stroke(
                width = 2.5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Draw data points
        consumptions.forEachIndexed { index, consumption ->
            val x = paddingLeft + index * xStep
            val y = paddingTop + chartHeight - ((consumption.dailyConsumption - minValue) / valueRange * chartHeight).toFloat()

            // Outer glow
            drawCircle(
                color = chartColor.copy(alpha = 0.3f),
                radius = 6f,
                center = Offset(x, y)
            )
            // Inner point
            drawCircle(
                color = chartColor,
                radius = 3f,
                center = Offset(x, y)
            )
        }

        // Draw date labels on X axis
        drawDateLabels(
            consumptions = consumptions,
            topLeft = Offset(paddingLeft, paddingTop),
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            labelColor = labelColor
        )
    }
}

private fun DrawScope.drawGridLines(
    topLeft: Offset,
    width: Float,
    height: Float,
    gridColor: Color,
    maxValue: Double,
    labelColor: Color,
    showCost: Boolean = false
) {
    val lineCount = 4
    val textPaint = android.graphics.Paint().apply {
        color = labelColor.toArgb()
        textSize = 10.sp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.RIGHT
    }

    for (i in 0..lineCount) {
        val y = topLeft.y + height * i / lineCount

        // Horizontal grid line
        drawLine(
            color = gridColor,
            start = Offset(topLeft.x, y),
            end = Offset(topLeft.x + width, y),
            strokeWidth = 0.5f
        )

        // Y axis label — ¥ or kWh
        val value = maxValue * (lineCount - i) / lineCount
        val label = if (showCost) "¥${Formatters.formatInt(value)}" else Formatters.formatInt(value)
        drawContext.canvas.nativeCanvas.drawText(
            label,
            topLeft.x - 8f,
            y + 4f,
            textPaint
        )
    }
}

private fun DrawScope.drawDateLabels(
    consumptions: List<DailyConsumption>,
    topLeft: Offset,
    chartWidth: Float,
    chartHeight: Float,
    labelColor: Color
) {
    if (consumptions.isEmpty()) return

    val textPaint = android.graphics.Paint().apply {
        color = labelColor.toArgb()
        textSize = 9.sp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    val pointCount = consumptions.size
    val xStep = if (pointCount > 1) chartWidth / (pointCount - 1) else chartWidth

    // Show at most ~6 labels to avoid crowding
    val step = maxOf(1, pointCount / 6)

    consumptions.forEachIndexed { index, consumption ->
        if (index % step == 0 || index == pointCount - 1) {
            val x = topLeft.x + index * xStep
            val y = topLeft.y + chartHeight + 20f

            val label = consumption.date.format(DateTimeFormatter.ofPattern("MM/dd"))
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                y,
                textPaint
            )
        }
    }
}
