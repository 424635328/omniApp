package com.example.energyflow.ui.chart

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.DarkCard
import com.example.energyflow.ui.theme.DarkSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.ElectricPeakColor
import com.example.energyflow.ui.theme.ElectricValleyColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.NeonBlue
import com.example.energyflow.ui.theme.NeonYellow
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.WaterColor
import com.example.energyflow.ui.utils.Formatters
import java.time.format.DateTimeFormatter

@Composable
fun ChartScreen(
    viewModel: ChartViewModel = hiltViewModel()
) {
    val chartData by viewModel.chartData.collectAsState()
    val timeRange by viewModel.timeRange.collectAsState()
    val showCost by viewModel.showCost.collectAsState()
    val billResult by viewModel.billResult.collectAsState()
    val prediction by viewModel.prediction.collectAsState()
    val predictedBill by viewModel.predictedBill.collectAsState()
    val eventImpacts by viewModel.eventImpacts.collectAsState()
    val weatherData by viewModel.weatherData.collectAsState()
    val weatherLoading by viewModel.weatherLoading.collectAsState()
    var showWeather by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
    ) {
        ChartTopBar()

        TimeRangeSelector(timeRange) { viewModel.setTimeRange(it) }

        Spacer(modifier = Modifier.height(12.dp))

        // ── kWh / ¥ 切换按钮 ──────────────────────────
        ToggleCostButton(showCost = showCost, onToggle = { viewModel.toggleShowCost() })

        Spacer(modifier = Modifier.height(16.dp))

        if (chartData == ChartData.Empty) {
            EmptyChartPlaceholder()
        } else {
            // 折线图 + 天气叠层
            if (chartData.dailyConsumptions.isNotEmpty()) {
                // 天气开关
                if (weatherData.isNotEmpty()) {
                    WeatherToggleButton(
                        showWeather = showWeather,
                        onToggle = { showWeather = !showWeather },
                        loading = weatherLoading
                    )
                }

                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ConsumptionLineChart(
                        consumptions = chartData.dailyConsumptions,
                        showCost = showCost,
                        modifier = Modifier.matchParentSize()
                    )

                    if (showWeather && weatherData.isNotEmpty()) {
                        WeatherOverlay(
                            weatherData = weatherData,
                            consumptionDates = chartData.dailyConsumptions.map { it.date.toLocalDate() },
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ═══ 账单摘要 ═══
            if (billResult != null) {
                BillSummaryPanel(billResult!!, showCost)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ═══ 月度预测 ═══
            if (prediction != null) {
                PredictionPanel(prediction!!, predictedBill, showCost)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ═══ 数据摘要 ═══
            DataSummary(chartData)

            Spacer(modifier = Modifier.height(20.dp))

            // ═══ 事件归因 ═══
            if (eventImpacts.isNotEmpty()) {
                EventImpactPanel(eventImpacts)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ═══ 声明 ═══
            if (chartData.annotations.isNotEmpty()) {
                AnnotationsList(chartData.annotations)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ════════════════════════════════════════════════════════
// kWh / ¥ 切换
// ════════════════════════════════════════════════════════

@Composable
private fun ToggleCostButton(showCost: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            onClick = onToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkCard,
                contentColor = if (showCost) ElectricColor else TextSecondary
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (showCost) "¥ 账单" else "kWh 耗量",
                fontFamily = MonoFontFamily,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun WeatherToggleButton(showWeather: Boolean, onToggle: () -> Unit, loading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            onClick = onToggle,
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (showWeather) NeonBlue.copy(alpha = 0.2f) else DarkCard,
                contentColor = if (showWeather) NeonBlue else TextSecondary,
                disabledContainerColor = DarkCard,
                disabledContentColor = TextSecondary
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("🌡", fontSize = 16.sp)
            Text(
                text = if (loading) "加载中..." else if (showWeather) "隐藏温度" else "显示温度",
                fontFamily = MonoFontFamily,
                fontSize = 13.sp
            )
        }
    }
}

// ════════════════════════════════════════════════════════
// 账单摘要面板
// ════════════════════════════════════════════════════════

@Composable
private fun BillSummaryPanel(bill: BillData, showCost: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(16.dp)
    ) {
        Text(
            "📊 账单摘要",
            style = MaterialTheme.typography.titleMedium,
            color = NeonYellow,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 总耗量 / 总费用
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatCard("总耗电", "${Formatters.formatDecimal1(bill.totalKwh)} 度", ElectricColor, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            StatCard("总电费", "¥${Formatters.formatDecimal2(bill.electricCost)}", ElectricColor, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 峰谷明细
        if (bill.peakKwh > 0 || bill.valleyKwh > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (bill.peakKwh > 0) {
                    StatCard(
                        "峰电 ${Formatters.formatDecimal1(bill.peakKwh)}度",
                        "¥${Formatters.formatDecimal2(bill.peakPrice)}/度",
                        ElectricPeakColor,
                        Modifier.weight(1f)
                    )
                }
                if (bill.valleyKwh > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    StatCard(
                        "谷电 ${Formatters.formatDecimal1(bill.valleyKwh)}度",
                        "¥${Formatters.formatDecimal2(bill.valleyPrice)}/度",
                        ElectricValleyColor,
                        Modifier.weight(1f)
                    )
                }
            }
        }

        // 水费
        if (bill.waterTons > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            StatCard(
                "水费 ${Formatters.formatDecimal1(bill.waterTons)}吨",
                "¥${Formatters.formatDecimal2(bill.waterCost)} (¥${Formatters.formatDecimal2(bill.waterPrice)}/吨)",
                WaterColor,
                Modifier.fillMaxWidth()
            )
        }

        // 总计
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ElectricColor.copy(alpha = 0.15f))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "共电费 ¥${Formatters.formatDecimal2(bill.totalCost)}",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricColor,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ════════════════════════════════════════════════════════
// 月度预测面板
// ════════════════════════════════════════════════════════

@Composable
private fun PredictionPanel(
    prediction: com.example.energyflow.data.MonthPrediction,
    predictedBill: PredictedBill?,
    showCost: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(DarkCard, DarkCard.copy(alpha = 0.9f))
                )
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = ElectricColor, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "📈 月度预测",
                style = MaterialTheme.typography.titleMedium,
                color = NeonYellow,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatCard(
                "日均消耗",
                "${Formatters.formatDecimal1(prediction.dailyRateKwh)} 度",
                ElectricColor,
                Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            StatCard(
                "本月已耗",
                "${Formatters.formatDecimal1(prediction.consumedSoFarKwh)} 度",
                TextSecondary,
                Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatCard(
                "预计全月",
                "${Formatters.formatDecimal1(prediction.predictedTotalKwh)} 度",
                NeonYellow,
                Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (predictedBill != null) {
                StatCard(
                    "预计账单",
                    "¥${Formatters.formatDecimal2(predictedBill.predictedCost)}",
                    Color(0xFF00FF88),
                    Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// ════════════════════════════════════════════════════════
// 事件归因面板
// ════════════════════════════════════════════════════════

@Composable
private fun EventImpactPanel(impacts: List<com.example.energyflow.data.EventImpact>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(16.dp)
    ) {
        Text(
            "🔍 事件耗能分析",
            style = MaterialTheme.typography.titleMedium,
            color = NeonYellow,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        impacts.forEach { impact ->
            val deltaSign = if (impact.deltaKwh > 0) "+" else ""
            val deltaColor = if (impact.deltaKwh > 0) Color(0xFFFF6B6B) else Color(0xFF00FF88)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    impact.tag,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElectricColor,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "使用时段: ${Formatters.formatDecimal1(impact.eventDailyKwh)} 度/天",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontFamily = MonoFontFamily
                    )
                    Text(
                        "参考: ${Formatters.formatDecimal1(impact.nonEventDailyKwh)} 度/天",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontFamily = MonoFontFamily
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    "差异: $deltaSign${Formatters.formatDecimal1(impact.deltaKwh)} 度/天",
                    style = MaterialTheme.typography.labelMedium,
                    color = deltaColor,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }

            if (impact != impacts.last()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DarkSurface)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════
// 原有组件
// ════════════════════════════════════════════════════════

@Composable
private fun ChartTopBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(DarkBackground, DarkBackground.copy(alpha = 0.95f), DarkBackground.copy(alpha = 0.9f), DarkBackground.copy(alpha = 0f))
                )
            )
            .padding(16.dp)
    ) {
        Text(
            "能耗分析",
            style = MaterialTheme.typography.headlineMedium,
            color = NeonYellow,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp
        )
    }
}

@Composable
private fun TimeRangeSelector(selectedRange: TimeRange, onRangeSelected: (TimeRange) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeRange.entries.forEach { range ->
            val isSelected = range == selectedRange
            var isPressed by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.95f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
                label = "button_scale"
            )
            Button(
                onClick = { isPressed = true; onRangeSelected(range); isPressed = false },
                modifier = Modifier.weight(1f).scale(scale)
                    .shadow(if (isSelected) 4.dp else 0.dp, RoundedCornerShape(8.dp), ambientColor = ElectricColor.copy(0.2f), spotColor = ElectricColor.copy(0.2f)),
                colors = ButtonDefaults.buttonColors(if (isSelected) ElectricColor else DarkCard, if (isSelected) DarkBackground else TextSecondary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    when (range) {
                        TimeRange.WEEK -> "周"
                        TimeRange.MONTH -> "月"
                        TimeRange.YEAR -> "年"
                        TimeRange.ALL -> "全部"
                    },
                    fontFamily = MonoFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun DataSummary(chartData: ChartData) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("数据摘要", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        SummaryCard("记录数量", "${chartData.records.size}", ElectricColor)

        if (chartData.records.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val latest = chartData.records.first()
            SummaryCard("最新电表", Formatters.formatElectric(latest.electricTotal), ElectricColor)
        }

        val latestWater = chartData.records.firstOrNull { it.isWaterRecorded }
        if (latestWater != null) {
            Spacer(modifier = Modifier.height(8.dp))
            SummaryCard("最新水表", Formatters.formatWater(latestWater.waterTotal), WaterColor)
        }

        if (chartData.dailyConsumptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val avgDaily = chartData.dailyConsumptions.map { it.dailyConsumption }.average()
            SummaryCard("日均用电", Formatters.formatDailyConsumption(avgDaily), NeonYellow)

            Spacer(modifier = Modifier.height(8.dp))
            val totalCons = chartData.dailyConsumptions.sumOf { it.consumption }
            SummaryCard("总用电量", "${Formatters.formatDecimal1(totalCons)} 度", ElectricColor)
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(DarkCard, DarkCard.copy(0.8f))))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontFamily = MonoFontFamily)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface)
            .padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontFamily = MonoFontFamily)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AnnotationsList(annotations: List<com.example.energyflow.data.MeterRecord>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("事件标注", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        annotations.forEach { record ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(DarkCard, DarkCard.copy(0.8f))))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ElectricColor))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(record.timestamp.format(DateTimeFormatter.ofPattern("MM.dd HH:mm")), style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontFamily = MonoFontFamily)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(record.note ?: "", style = MaterialTheme.typography.bodyMedium, color = ElectricColor, fontFamily = MonoFontFamily, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EmptyChartPlaceholder() {
    Box(modifier = Modifier.fillMaxWidth().height(200.dp).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(60.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(ElectricColor.copy(0.2f), DarkCard))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bolt, null, tint = ElectricColor, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("暂无数据", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("请先添加能耗记录", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontFamily = MonoFontFamily)
        }
    }
}
