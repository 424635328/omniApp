package com.example.energyflow.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import com.example.energyflow.data.DailyWeather
import com.example.energyflow.ui.theme.NeonBlue
import java.time.LocalDate

/**
 * 天气温度曲线叠加层。
 *
 * 在折线图背景中绘制温度曲线，帮助用户直观看到：
 * "气温超过 32°C 时，空调耗电量是如何飙升的"
 *
 * 设计：
 * - 温度曲线使用冰蓝色（NeonBlue），半透明
 * - 最高温和最低温 各一根线
 * - 极端高温（>32°C）区域用暖色标注
 */
@Composable
fun WeatherOverlay(
    weatherData: List<DailyWeather>,
    consumptionDates: List<LocalDate>,
    paddingLeft: Float = 50f,
    paddingTop: Float = 16f,
    paddingRight: Float = 16f,
    paddingBottom: Float = 40f,
    modifier: Modifier = Modifier
) {
    if (weatherData.isEmpty() || consumptionDates.size < 2) return

    // Temperature range for scaling: 10°C ~ 40°C
    val tempRangeMin = 10.0
    val tempRangeMax = 40.0
    val tempRange = tempRangeMax - tempRangeMin

    val hotZoneColor = Color(0xFFFF6600).copy(alpha = 0.15f)  // Orange tint for >32°C

    Canvas(modifier = modifier) {
        val drawWidth = size.width - paddingLeft - paddingRight
        val xStep = drawWidth / (consumptionDates.size - 1)
        val effectiveHeight = size.height - paddingTop - paddingBottom

        // ── 高温区域标记 (>32°C) ──
        val hotY = paddingTop + effectiveHeight - ((32.0 - tempRangeMin) / tempRange * effectiveHeight).toFloat()
        if (hotY < paddingTop + effectiveHeight) {
            drawRect(
                color = hotZoneColor,
                topLeft = Offset(paddingLeft, hotY),
                size = Size(drawWidth, paddingTop + effectiveHeight - hotY)
            )
            // Label
            val textPaint = android.graphics.Paint().apply {
                color = Color(0xFFFF6600).copy(alpha = 0.6f).toArgb()
                textSize = 8.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
            }
            drawContext.canvas.nativeCanvas.drawText(
                "32°C",
                paddingLeft + 4f,
                hotY - 4f,
                textPaint
            )
        }

        // ── 最高温度曲线 ──
        val maxPath = Path()
        val minPath = Path()

        val weatherByDate = weatherData.associateBy { it.date }
        var maxPathStarted = false
        var minPathStarted = false
        consumptionDates.forEachIndexed { index, date ->
            val weather = weatherByDate[date.toString()] ?: run {
                maxPathStarted = false
                minPathStarted = false
                return@forEachIndexed
            }
            val x = paddingLeft + index * xStep
            val maxY = paddingTop + effectiveHeight - ((weather.tempMax - tempRangeMin) / tempRange * effectiveHeight).toFloat()
            val minY = paddingTop + effectiveHeight - ((weather.tempMin - tempRangeMin) / tempRange * effectiveHeight).toFloat()

            if (!maxPathStarted) {
                maxPath.moveTo(x, maxY)
                maxPathStarted = true
            } else {
                maxPath.lineTo(x, maxY)
            }
            if (!minPathStarted) {
                minPath.moveTo(x, minY)
                minPathStarted = true
            } else {
                minPath.lineTo(x, minY)
            }
        }

        // Draw min temp line (bottom)
        drawPath(
            path = minPath,
            color = NeonBlue.copy(alpha = 0.25f),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw max temp line (top)
        drawPath(
            path = maxPath,
            color = Color(0xFFFF6600).copy(alpha = 0.4f),
            style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // ── 温度标注（仅两端） ──
        val alignedWeather = consumptionDates.mapNotNull { weatherByDate[it.toString()] }
        if (alignedWeather.size >= 2) {
            val textPaint = android.graphics.Paint().apply {
                color = NeonBlue.copy(alpha = 0.7f).toArgb()
                textSize = 9.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
            }

            // First day
            val first = alignedWeather.first()
            val firstMaxY = paddingTop + effectiveHeight - ((first.tempMax - tempRangeMin) / tempRange * effectiveHeight).toFloat()
            drawContext.canvas.nativeCanvas.drawText(
                "${first.tempMax.toInt()}°",
                paddingLeft + 4f,
                firstMaxY - 4f,
                textPaint
            )

            // Last day
            val last = alignedWeather.last()
            val lastIndex = consumptionDates.indexOfLast { it.toString() == last.date }
            val lastX = paddingLeft + lastIndex * xStep
            val lastMaxY = paddingTop + effectiveHeight - ((last.tempMax - tempRangeMin) / tempRange * effectiveHeight).toFloat()
            drawContext.canvas.nativeCanvas.drawText(
                "${last.tempMax.toInt()}°",
                lastX - 32f,
                lastMaxY - 4f,
                textPaint
            )
        }
    }
}
