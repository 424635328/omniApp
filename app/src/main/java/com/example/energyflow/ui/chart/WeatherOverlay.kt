package com.example.energyflow.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.sp
import com.example.energyflow.data.DailyWeather
import com.example.energyflow.ui.theme.ElectricEnd
import com.example.energyflow.ui.theme.GasStart
import com.example.energyflow.ui.theme.WarningNeon
import java.time.LocalDate

/**
 * 天气温度曲线叠加层。
 *
 * 在折线图上方绘制平滑连续的温度曲线，帮助观察气温与耗电的关联。
 * 所有日期均有插值数据，曲线无断点。
 */
@Composable
fun WeatherOverlay(
    weatherData: List<DailyWeather>,
    consumptionDates: List<LocalDate>,
    paddingLeft: Float = 54f,
    paddingTop: Float = 12f,
    paddingRight: Float = 20f,
    paddingBottom: Float = 44f,
    modifier: Modifier = Modifier
) {
    if (weatherData.isEmpty() || consumptionDates.size < 2) return

    val tempMin = 0.0
    val tempMax = 42.0
    val tempRange = tempMax - tempMin
    val hotThreshold = 32.0

    val hotBgColor = WarningNeon.copy(alpha = 0.06f)
    val hotLineColor = WarningNeon.copy(alpha = 0.5f)
    val coldLineColor = ElectricEnd.copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        val drawWidth = size.width - paddingLeft - paddingRight
        val xStep = drawWidth / (consumptionDates.size - 1)
        val effH = size.height - paddingTop - paddingBottom
        if (effH <= 0f || drawWidth <= 0f) return@Canvas

        val weatherByDate = weatherData.associateBy { it.date }

        // ── 高温区域 (>32°C) ──
        val hotY = paddingTop + effH - ((hotThreshold - tempMin) / tempRange * effH).toFloat()
        if (hotY < paddingTop + effH) {
            drawRect(
                color = hotBgColor,
                topLeft = Offset(paddingLeft, hotY),
                size = Size(drawWidth, (paddingTop + effH - hotY).coerceAtLeast(0f))
            )
            drawLine(
                WarningNeon.copy(alpha = 0.25f),
                Offset(paddingLeft, hotY),
                Offset(paddingLeft + drawWidth, hotY),
                1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)
            )
            val lbl = android.graphics.Paint().apply {
                color = WarningNeon.copy(alpha = 0.5f).toArgb()
                textSize = 9.sp.toPx(); isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
            }
            drawContext.canvas.nativeCanvas.drawText("32°C", paddingLeft + 8f, hotY - 6f, lbl)
        }

        // ── 构建平滑曲线 ──
        val maxPath = Path()
        val minPath = Path()
        var maxStarted = false
        var minStarted = false

        // 先收集所有有效点
        val points = consumptionDates.mapIndexedNotNull { index, date ->
            val w = weatherByDate[date] ?: return@mapIndexedNotNull null
            val x = paddingLeft + index * xStep
            val my = paddingTop + effH -
                ((w.tempMax.coerceIn(tempMin, tempMax) - tempMin) / tempRange * effH).toFloat()
            val ny = paddingTop + effH -
                ((w.tempMin.coerceIn(tempMin, tempMax) - tempMin) / tempRange * effH).toFloat()
            Triple(x, my, ny)
        }

        points.forEachIndexed { i, (x, my, ny) ->
            if (i == 0) {
                maxPath.moveTo(x, my); maxStarted = true
                minPath.moveTo(x, ny); minStarted = true
            } else {
                maxPath.lineTo(x, my)
                minPath.lineTo(x, ny)
            }
        }

        if (!maxStarted || !minStarted) return@Canvas

        // ── 最低温填充（冰蓝渐变） ──
        val minFill = Path().apply {
            addPath(minPath)
            lineTo(points.last().first, paddingTop + effH)
            lineTo(points.first().first, paddingTop + effH)
            close()
        }
        drawPath(
            minFill,
            brush = Brush.verticalGradient(
                listOf(coldLineColor.copy(alpha = 0.06f), Color.Transparent),
                startY = paddingTop,
                endY = paddingTop + effH
            )
        )

        // 最低温曲线
        drawPath(minPath, coldLineColor.copy(alpha = 0.25f),
            style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(minPath, coldLineColor,
            style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // 最高温曲线
        drawPath(maxPath, hotLineColor.copy(alpha = 0.25f),
            style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(maxPath, hotLineColor,
            style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // ── 温度标注（仅两端） ──
        val firstW = weatherByDate[consumptionDates.first()]
        val lastW = weatherByDate[consumptionDates.last()]
        val labelPaint = android.graphics.Paint().apply {
            color = hotLineColor.toArgb(); textSize = 9.sp.toPx()
            isAntiAlias = true; textAlign = android.graphics.Paint.Align.LEFT
        }

        if (firstW != null) {
            val y = paddingTop + effH -
                ((firstW.tempMax.coerceIn(tempMin, tempMax) - tempMin) / tempRange * effH).toFloat()
            drawContext.canvas.nativeCanvas.drawText(
                "${firstW.tempMax.toInt()}°", paddingLeft - 2f, y - 6f, labelPaint
            )
        }
        if (lastW != null) {
            val y = paddingTop + effH -
                ((lastW.tempMax.coerceIn(tempMin, tempMax) - tempMin) / tempRange * effH).toFloat()
            labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            val lx = paddingLeft + (consumptionDates.size - 1) * xStep
            drawContext.canvas.nativeCanvas.drawText(
                "${lastW.tempMax.toInt()}°", lx - 4f, y - 6f, labelPaint
            )
        }
    }
}
