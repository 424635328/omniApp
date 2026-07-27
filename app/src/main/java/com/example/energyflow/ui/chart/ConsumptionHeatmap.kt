package com.example.energyflow.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.energyflow.ui.theme.AppSurface
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.TextTertiary
import java.time.DayOfWeek
import java.time.LocalDate

// ═══════════════════════════════════════════════════════════════
// 近 365 天消耗热力图 — GitHub Contribution 风格
// 7 行（周一到周日）× 53 列（周），横向可滚动，初始定位到今天
// ═══════════════════════════════════════════════════════════════

private const val WEEK_COLUMNS = 53
private val CellSize = 10.dp
private val CellSpacing = 2.dp

@Composable
fun ConsumptionHeatmap(
    data: Map<LocalDate, Double>,
    accentColor: Color,
    unitLabel: String,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val windowStart = remember(today) { today.minusDays(364) }
    // 网格起点：本周周一往前推 52 周，共 53 列覆盖今天
    val weekStarts = remember(today) {
        val thisMonday = today.with(DayOfWeek.MONDAY)
        List(WEEK_COLUMNS) { thisMonday.minusWeeks((WEEK_COLUMNS - 1 - it).toLong()) }
    }
    // 月份标签：每列周一所在月份发生变化时标注
    val monthLabels = remember(weekStarts) {
        var prevMonth = -1
        weekStarts.map { weekStart ->
            if (weekStart.monthValue != prevMonth) {
                prevMonth = weekStart.monthValue
                "${weekStart.monthValue}月"
            } else {
                null
            }
        }
    }
    // 分档阈值：非零日消耗的 25/50/75 分位数
    val thresholds = remember(data) {
        val nonZero = data.values.filter { it > 0.0 }.sorted()
        if (nonZero.isEmpty()) {
            null
        } else {
            Triple(
                percentileOf(nonZero, 0.25),
                percentileOf(nonZero, 0.50),
                percentileOf(nonZero, 0.75)
            )
        }
    }

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val scrollState = rememberScrollState()
    // 初始滚动到最右（今天所在列）
    LaunchedEffect(data) { scrollState.scrollTo(scrollState.maxValue) }

    Column(modifier = modifier) {
        Column(modifier = Modifier.horizontalScroll(scrollState)) {
            // ── 月份标签行 ──
            Row(horizontalArrangement = Arrangement.spacedBy(CellSpacing)) {
                monthLabels.forEach { label ->
                    Box(modifier = Modifier.width(CellSize)) {
                        if (label != null) {
                            Text(
                                text = label,
                                fontFamily = MonoFontFamily,
                                fontSize = 9.sp,
                                color = TextTertiary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.wrapContentWidth(
                                    align = Alignment.Start,
                                    unbounded = true
                                )
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // ── 7 × 53 网格 ──
            Row(horizontalArrangement = Arrangement.spacedBy(CellSpacing)) {
                weekStarts.forEach { weekStart ->
                    Column(verticalArrangement = Arrangement.spacedBy(CellSpacing)) {
                        for (dayOffset in 0..6) {
                            val date = weekStart.plusDays(dayOffset.toLong())
                            if (date.isBefore(windowStart) || date.isAfter(today)) {
                                Spacer(modifier = Modifier.size(CellSize))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(CellSize)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(cellColor(data[date], thresholds, accentColor))
                                        .clickable { selectedDate = date }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 选中单元格详情 ──
        selectedDate?.let { date ->
            val value = data[date]
            val dateLabel = "%02d.%02d".format(date.monthValue, date.dayOfMonth)
            Text(
                text = if (value != null) {
                    "$dateLabel · ${"%.1f".format(value)} $unitLabel"
                } else {
                    "$dateLabel · 无数据"
                },
                fontFamily = MonoFontFamily,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** 无数据 = AppSurface；非零消耗按分位数落入 4 档强度。 */
private fun cellColor(
    value: Double?,
    thresholds: Triple<Double, Double, Double>?,
    accentColor: Color
): Color {
    if (value == null || value <= 0.0 || thresholds == null) return AppSurface
    val (q1, q2, q3) = thresholds
    return when {
        value <= q1 -> accentColor.copy(alpha = 0.25f)
        value <= q2 -> accentColor.copy(alpha = 0.45f)
        value <= q3 -> accentColor.copy(alpha = 0.7f)
        else -> accentColor.copy(alpha = 1f)
    }
}

/** 简单分位数：已排序列表按位置取值。 */
private fun percentileOf(sorted: List<Double>, percentile: Double): Double {
    val index = ((sorted.size - 1) * percentile).toInt()
    return sorted[index]
}
