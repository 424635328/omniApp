package com.example.energyflow.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.energyflow.ui.theme.DarkCard
import com.example.energyflow.data.DailyWeather
import com.example.energyflow.ui.theme.DarkSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.NeonBlue
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.utils.Formatters.formatDecimal1
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun ConsumptionLineChart(
    consumptions: List<DailyConsumption>,
    showCost: Boolean = false,
    selectedIndex: Int = -1,
    onSelectedIndexChange: (Int) -> Unit = {},
    weatherByDate: Map<LocalDate, DailyWeather> = emptyMap(),
    modifier: Modifier = Modifier
) {
    if (consumptions.isEmpty()) return

    val animateProgress = remember { Animatable(0f) }

    // 数据变化时重播动画
    LaunchedEffect(consumptions, showCost) {
        animateProgress.snapTo(0f)
        animateProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
    }

    val chartColor = ElectricColor
    val gridColor = DarkCard.copy(alpha = 0.6f)
    val labelColor = TextSecondary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .pointerInput(consumptions, showCost) {
                detectTapGestures { offset ->
                    val paddingLeft = 54f
                    val chartWidth = size.width - paddingLeft - 20f
                    val xStep = if (consumptions.size > 1)
                        chartWidth / (consumptions.size - 1) else chartWidth
                    val index = ((offset.x - paddingLeft) / xStep).roundToInt()
                        .coerceIn(0, consumptions.size - 1)
                    onSelectedIndexChange(if (selectedIndex == index) -1 else index)
                }
            }
    ) {
        val paddingLeft = 54f
        val paddingBottom = 44f
        val paddingTop = 12f
        val paddingRight = 20f
        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom
        if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

        val values = consumptions.map {
            if (showCost) it.estimatedCost else it.dailyConsumption
        }
        val maxValue = values.maxOrNull()?.let { if (it <= 0) 1.0 else it * 1.1 } ?: 1.0
        val minValue = 0.0
        val valueRange = maxValue - minValue
        val pointCount = consumptions.size
        val xStep = if (pointCount > 1) chartWidth / (pointCount - 1) else chartWidth

        // ── 计算所有数据点 ──
        val points = consumptions.mapIndexed { index, consumption ->
            val x = paddingLeft + index * xStep
            val value = if (showCost) consumption.estimatedCost else consumption.dailyConsumption
            val y = paddingTop + chartHeight -
                    ((value - minValue) / valueRange * chartHeight).toFloat()
            Offset(x, y)
        }

        val progress = animateProgress.value
        val visibleCount = (pointCount * progress).toInt().coerceIn(1, pointCount)

        // ── 网格线 ──
        val gridTextPaint = android.graphics.Paint().apply {
            color = labelColor.copy(alpha = 0.7f).toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }

        // 水平虚线
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        val lineCount = 4
        for (i in 0..lineCount) {
            val y = paddingTop + chartHeight * i / lineCount
            drawLine(
                gridColor.copy(alpha = 0.4f),
                Offset(paddingLeft, y),
                Offset(paddingLeft + chartWidth, y),
                1f,
                pathEffect = dashEffect
            )
            val gridValue = maxValue * (lineCount - i) / lineCount
            val label = if (showCost) "¥${formatInt(gridValue)}" else formatInt(gridValue)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                paddingLeft - 10f,
                y + 4f,
                gridTextPaint
            )
        }

        if (visibleCount < 2) {
            // 仅一个点可见时，只画一个点
            val pt = points[0]
            drawCircle(chartColor.copy(alpha = 0.3f), 7f, pt)
            drawCircle(chartColor, 4f, pt)
            return@Canvas
        }

        // ── 构建可见路径 ──
        val linePath = Path()
        val fillPath = Path()

        points.take(visibleCount).forEachIndexed { index, pt ->
            if (index == 0) {
                linePath.moveTo(pt.x, pt.y)
                fillPath.moveTo(pt.x, paddingTop + chartHeight)
                fillPath.lineTo(pt.x, pt.y)
            } else {
                linePath.lineTo(pt.x, pt.y)
                fillPath.lineTo(pt.x, pt.y)
            }
        }

        // 补间最后一段（平滑过渡）
        if (visibleCount < pointCount) {
            val last = points[visibleCount - 1]
            val next = points[visibleCount]
            val fraction = (pointCount * progress) - visibleCount + 1
            val interpX = last.x + (next.x - last.x) * fraction
            val interpY = last.y + (next.y - last.y) * fraction
            linePath.lineTo(interpX, interpY)
            fillPath.lineTo(interpX, interpY)
            fillPath.lineTo(interpX, paddingTop + chartHeight)
        } else {
            fillPath.lineTo(points.last().x, paddingTop + chartHeight)
        }
        fillPath.close()

        // ── 面积填充（渐隐到透明） ──
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                listOf(
                    chartColor.copy(alpha = 0.12f),
                    chartColor.copy(alpha = 0.02f),
                    Color.Transparent
                ),
                startY = paddingTop,
                endY = paddingTop + chartHeight
            )
        )

        // ── 发光底层 ──
        drawPath(
            linePath, chartColor.copy(alpha = 0.2f),
            style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // ── 主线 ──
        drawPath(
            linePath, chartColor.copy(alpha = 0.5f),
            style = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            linePath, chartColor,
            style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // ── 数据点 ──
        val pointStep = (pointCount / 120).coerceAtLeast(1)
        points.forEachIndexed { index, point ->
            if (index >= visibleCount) return@forEachIndexed
            if (index % pointStep != 0 && index != pointCount - 1) return@forEachIndexed

            val isSelected = index == selectedIndex
            // 外圈发光
            drawCircle(
                chartColor.copy(alpha = if (isSelected) 0.4f else 0.15f),
                if (isSelected) 10f else 7f,
                point
            )
            // 实心点
            drawCircle(
                if (isSelected) Color.White else chartColor,
                if (isSelected) 5f else 3f,
                point
            )
        }

        // ── 选中指示器 ──
        if (selectedIndex in points.indices && selectedIndex < visibleCount) {
            val selPoint = points[selectedIndex]
            val selValue = values[selectedIndex]
            val selLabel = if (showCost) "¥${formatDecimal1(selValue)}" else "${formatDecimal1(selValue)} 度"
            val selDate = consumptions[selectedIndex].date.format(
                DateTimeFormatter.ofPattern("MM.dd")
            )

            // 竖线指示
            drawLine(
                chartColor.copy(alpha = 0.18f),
                Offset(selPoint.x, paddingTop),
                Offset(selPoint.x, paddingTop + chartHeight),
                2f
            )

            // ── Tooltip ──
            val selLocalDate = consumptions[selectedIndex].date.toLocalDate()
            val selWeather = weatherByDate[selLocalDate]

            val hasWeather = selWeather != null
            // 单行 38f；双行（含分隔线）更高
            val tooltipH = if (hasWeather) 64f else 38f
            val tooltipPadH = 16f  // 水平内边距
            val tooltipRadius = 10f

            // ── 测量文字宽度 ──
            val datePaint = android.graphics.Paint().apply {
                color = TextSecondary.toArgb()
                textSize = 10.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
            }
            val valuePaint = android.graphics.Paint().apply {
                color = Color.White.toArgb()
                textSize = 13.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
                isFakeBoldText = true
            }
            val tempMaxPaint = android.graphics.Paint().apply {
                color = Color(0xFFFF8800).toArgb()  // 暖橙
                textSize = 11.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
                isFakeBoldText = true
            }
            val tempMinPaint = android.graphics.Paint().apply {
                color = NeonBlue.toArgb()  // 冰蓝
                textSize = 11.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
                isFakeBoldText = true
            }
            val sepLabelPaint = android.graphics.Paint().apply {
                color = TextSecondary.copy(alpha = 0.4f).toArgb()
                textSize = 11.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
            }

            // 第一行：日期（左） + 数值（右）
            val dateStr = selDate
            val valueStr = selLabel  // e.g. "5.2 度" or "¥3.12"
            val dw = datePaint.measureText(dateStr)
            val vw = valuePaint.measureText(valueStr)
            val row1W = dw + vw + 12f  // date + gap + value

            // 第二行：温度
            val wHighStr = if (hasWeather) "H${selWeather!!.tempMax.toInt()}°" else ""
            val wLowStr  = if (hasWeather) "L${selWeather!!.tempMin.toInt()}°" else ""
            val sepStr   = if (hasWeather) "  ·  " else ""
            val whw = tempMaxPaint.measureText(wHighStr)
            val wsw = sepLabelPaint.measureText(sepStr)
            val wlw = tempMinPaint.measureText(wLowStr)
            val row2W = if (hasWeather) whw + wsw + wlw else 0f

            val contentW = maxOf(row1W, row2W)
            val tooltipW = contentW + tooltipPadH * 2f

            val tipX = selPoint.x.coerceIn(
                paddingLeft + tooltipW / 2f,
                paddingLeft + chartWidth - tooltipW / 2f
            )
            val tipY = (paddingTop + 6f).coerceAtLeast(selPoint.y - tooltipH - 12f)

            val left = tipX - tooltipW / 2f
            val right = tipX + tooltipW / 2f
            val baseY = tipY

            // ── 背景 ──
            drawRoundRect(
                color = DarkSurface,
                topLeft = Offset(left, baseY),
                size = Size(tooltipW, tooltipH),
                cornerRadius = CornerRadius(tooltipRadius, tooltipRadius)
            )
            // 边框
            drawRoundRect(
                color = chartColor.copy(alpha = 0.3f),
                topLeft = Offset(left, baseY),
                size = Size(tooltipW, tooltipH),
                cornerRadius = CornerRadius(tooltipRadius, tooltipRadius),
                style = Stroke(1f)
            )

            // ── 第一行：日期 | 数值 ──
            val row1Y = baseY + 20f
            drawContext.canvas.nativeCanvas.drawText(
                dateStr, left + tooltipPadH, row1Y, datePaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                valueStr, right - tooltipPadH, row1Y, valuePaint
            )

            // ── 分隔线 ──
            if (hasWeather) {
                val sepY = baseY + 29f
                drawLine(
                    chartColor.copy(alpha = 0.12f),
                    Offset(left + tooltipPadH, sepY),
                    Offset(right - tooltipPadH, sepY),
                    1f
                )
            }

            // ── 第二行：温度范围 ──
            if (hasWeather) {
                val row2Y = baseY + 48f
                var cursorX = left + tooltipPadH
                drawContext.canvas.nativeCanvas.drawText(wHighStr, cursorX, row2Y, tempMaxPaint)
                cursorX += whw
                drawContext.canvas.nativeCanvas.drawText(sepStr, cursorX, row2Y, sepLabelPaint)
                cursorX += wsw
                drawContext.canvas.nativeCanvas.drawText(wLowStr, cursorX, row2Y, tempMinPaint)
            }

            // ── 小三角指示器 ──
            val triPath = Path().apply {
                moveTo(selPoint.x - 6f, baseY + tooltipH)
                lineTo(selPoint.x + 6f, baseY + tooltipH)
                lineTo(selPoint.x, baseY + tooltipH + 7f)
                close()
            }
            drawPath(triPath, color = DarkSurface)
            drawPath(triPath, chartColor.copy(alpha = 0.3f), style = Stroke(1f))
        }

        // ── 日期标签 ──
        val dateTextPaint = android.graphics.Paint().apply {
            color = labelColor.copy(alpha = 0.6f).toArgb()
            textSize = 9.sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val dateStep = maxOf(1, pointCount / 6)
        consumptions.forEachIndexed { index, consumption ->
            if (index % dateStep == 0 || index == pointCount - 1) {
                drawContext.canvas.nativeCanvas.drawText(
                    consumption.date.format(DateTimeFormatter.ofPattern("MM/dd")),
                    paddingLeft + index * xStep,
                    paddingTop + chartHeight + 22f,
                    dateTextPaint
                )
            }
        }
    }
}

private fun formatInt(value: Double): String = "%.0f".format(value)
