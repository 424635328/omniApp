package com.example.energyflow.ui.chart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.data.WeatherInterpolator
import com.example.energyflow.data.DailyWeather
import com.example.energyflow.data.EventImpact
import com.example.energyflow.data.MonthPrediction
import com.example.energyflow.shared.CarbonResult
import com.example.energyflow.shared.GreenBadge
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.DarkCard
import com.example.energyflow.ui.theme.DarkSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.ElectricPeakColor
import com.example.energyflow.ui.theme.ElectricValleyColor
import com.example.energyflow.ui.theme.ErrorNeon
import com.example.energyflow.ui.theme.GasColor
import com.example.energyflow.ui.theme.OutlineDark
import com.example.energyflow.ui.theme.SuccessGreen
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.NeonBlue
import com.example.energyflow.ui.theme.NeonYellow
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.TextTertiary
import com.example.energyflow.ui.theme.WarningNeon
import com.example.energyflow.ui.theme.WaterColor
import com.example.energyflow.ui.utils.Formatters
import java.time.LocalDate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════
// 主屏幕
// ═══════════════════════════════════════════════════════════════

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
    val predictionTracking by viewModel.predictionTracking.collectAsState()
    val eventImpacts by viewModel.eventImpacts.collectAsState()
    val aiAnalysis by viewModel.aiAnalysis.collectAsState()
    val aiLoading by viewModel.aiLoading.collectAsState()
    val weatherData by viewModel.weatherData.collectAsState()
    val weatherLoading by viewModel.weatherLoading.collectAsState()
    val weatherError by viewModel.weatherError.collectAsState()
    val carbonData by viewModel.carbonData.collectAsState()
    var showWeather by remember { mutableStateOf(false) }
    var selectedChartIndex by remember { mutableIntStateOf(-1) }

    // ── 延迟渲染重面板，避免首帧 JIT 编译卡顿 ──
    var renderHeavy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        renderHeavy = true
    }

    // ── 电/水/气 表类型 ──
    val selectedMeterType by viewModel.selectedMeterType.collectAsState()
    val waterChartData by viewModel.waterChartData.collectAsState()
    val waterBillResult by viewModel.waterBillResult.collectAsState()
    val waterPrediction by viewModel.waterPrediction.collectAsState()
    val gasChartData by viewModel.gasChartData.collectAsState()

    val isEmpty = when (selectedMeterType) {
        ChartViewModel.MeterType.ELECTRIC -> chartData == ChartData.Empty
        ChartViewModel.MeterType.WATER -> waterChartData == ChartData.Empty
        ChartViewModel.MeterType.GAS -> gasChartData == ChartData.Empty
    }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // 滚动超过 ~400px 时显示回到顶部按钮
    val isScrolledPastTop by remember {
        derivedStateOf { scrollState.value > 400 }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .verticalScroll(scrollState)
        ) {
            ChartTopBar(chartData, isEmpty, timeRange, selectedMeterType)

            TimeRangeSelector(timeRange) { viewModel.setTimeRange(it) }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 电/水/气 表类型切换 ──
            MeterTypeSelector(selectedMeterType) { viewModel.setMeterType(it) }

            Spacer(modifier = Modifier.height(12.dp))

            // ── kWh / ¥ 切换（仅电表） ──
            if (selectedMeterType == ChartViewModel.MeterType.ELECTRIC) {
                ToggleCostButton(showCost = showCost, onToggle = { viewModel.toggleShowCost() })
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isEmpty) {
                EmptyChartPlaceholder(selectedMeterType)
            } else {
                AnimatedVisibility(
                    visible = renderHeavy,
                    enter = fadeIn(tween(100))
                ) {
                    Column {
                        when (selectedMeterType) {
                            ChartViewModel.MeterType.ELECTRIC -> ElectricAnalysisSection(
                                chartData, billResult, showCost, prediction, predictedBill,
                                predictionTracking, eventImpacts, aiAnalysis, aiLoading,
                                weatherData, weatherLoading, weatherError, showWeather,
                                { showWeather = it }, selectedChartIndex, { selectedChartIndex = it },
                                viewModel, carbonData
                            )
                            ChartViewModel.MeterType.WATER -> WaterAnalysisSection(
                                waterChartData, waterBillResult, waterPrediction, showCost
                            )
                            ChartViewModel.MeterType.GAS -> GasAnalysisSection(
                                gasChartData
                            )
                        }
                    }
                }
            }
        }

        // ── 回到顶部按钮（右下角） ──
        AnimatedVisibility(
            visible = isScrolledPastTop && !isEmpty,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(tween(200)),
            exit = scaleOut(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(6.dp, CircleShape, ambientColor = ElectricColor.copy(0.3f))
                    .clip(CircleShape)
                    .background(DarkCard)
                    .clickable { coroutineScope.launch { scrollState.animateScrollTo(0) } },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "回到顶部",
                    tint = ElectricColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 顶栏 — 渐变背景 + 日期范围
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ChartTopBar(
    chartData: ChartData,
    isEmpty: Boolean,
    timeRange: TimeRange,
    meterType: ChartViewModel.MeterType
) {
    val today = LocalDate.now()
    val fmt = DateTimeFormatter.ofPattern("MM.dd")
    val dateRangeLabel = when {
        isEmpty -> today.format(DateTimeFormatter.ofPattern("yyyy年M月"))
        timeRange != TimeRange.ALL && chartData.records.size >= 2 -> {
            val start = when (timeRange) {
                TimeRange.WEEK  -> today.minusDays(6)
                TimeRange.MONTH -> today.minusDays(29)
                TimeRange.YEAR  -> today.minusDays(364)
                else -> chartData.records.first().timestamp.toLocalDate()
            }
            "${start.format(fmt)} — ${today.format(fmt)}"
        }
        chartData.records.size >= 2 -> {
            val first = chartData.records.first().timestamp
            val last = chartData.records.last().timestamp
            "${first.format(fmt)} — ${last.format(fmt)}"
        }
        else -> chartData.records.first().timestamp.format(
            DateTimeFormatter.ofPattern("yyyy年M月dd日")
        )
    }
    val (topIcon, topColor, topTitle) = when (meterType) {
        ChartViewModel.MeterType.ELECTRIC -> Triple(Icons.Default.Bolt, ElectricColor, "能耗分析")
        ChartViewModel.MeterType.WATER -> Triple(Icons.Default.WaterDrop, WaterColor, "用水分析")
        ChartViewModel.MeterType.GAS -> Triple(Icons.Default.Bolt, GasColor, "燃气分析")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        topColor.copy(alpha = 0.08f),
                        topColor.copy(alpha = 0.03f),
                        DarkBackground
                    )
                )
            )
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(topColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        topIcon,
                        contentDescription = null,
                        tint = topColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    topTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    dateRangeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    fontFamily = MonoFontFamily
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 时间范围选择器 — 滑动胶囊
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TimeRangeSelector(selectedRange: TimeRange, onRangeSelected: (TimeRange) -> Unit) {
    val entries = TimeRange.entries

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .border(1.dp, ElectricColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            entries.forEach { range ->
                val isSelected = range == selectedRange
                val selectedBg = ElectricColor.copy(alpha = 0.15f)
                val selectedBorder = ElectricColor.copy(alpha = 0.25f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) selectedBg else Color.Transparent)
                        .then(
                            if (isSelected) Modifier.border(
                                1.dp, selectedBorder, RoundedCornerShape(10.dp)
                            ) else Modifier
                        )
                        .clickable { onRangeSelected(range) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (range) {
                            TimeRange.WEEK -> "周"
                            TimeRange.MONTH -> "月"
                            TimeRange.YEAR -> "年"
                            TimeRange.ALL -> "全部"
                        },
                        fontFamily = MonoFontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) ElectricColor else TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// kWh / ¥ 切换按钮
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ToggleCostButton(showCost: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(DarkCard)
                .border(1.dp, ElectricColor.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .clickable { onToggle() }
                .padding(horizontal = 5.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // kWh tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!showCost) ElectricColor.copy(alpha = 0.18f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        "kWh 耗量",
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp,
                        fontWeight = if (!showCost) FontWeight.Bold else FontWeight.Medium,
                        color = if (!showCost) ElectricColor else TextTertiary
                    )
                }
                // ¥ tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (showCost) SuccessGreen.copy(alpha = 0.18f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        "¥ 费用",
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp,
                        fontWeight = if (showCost) FontWeight.Bold else FontWeight.Medium,
                        color = if (showCost) SuccessGreen else TextTertiary
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 图表区块（含天气切换）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ChartSection(
    showCost: Boolean,
    showWeather: Boolean,
    weatherLoading: Boolean,
    weatherError: String?,
    onToggleWeather: () -> Unit,
    meterType: ChartViewModel.MeterType,
    chartContent: @Composable () -> Unit
) {
    val chartTitle = when (meterType) {
        ChartViewModel.MeterType.ELECTRIC -> if (showCost) "费用趋势" else "电量趋势"
        ChartViewModel.MeterType.WATER -> if (showCost) "费用趋势" else "用水趋势"
        ChartViewModel.MeterType.GAS -> "燃气趋势"
    }
    val accentColor = when (meterType) {
        ChartViewModel.MeterType.ELECTRIC -> ElectricColor
        ChartViewModel.MeterType.WATER -> WaterColor
        ChartViewModel.MeterType.GAS -> GasColor
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, accentColor.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .drawBehind {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(accentColor.copy(alpha = 0.10f), accentColor.copy(alpha = 0.02f))
                    ),
                    style = Stroke(width = 1.5f),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
            }
            .padding(16.dp)
    ) {
        // ── 标题行 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(4.dp, 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(listOf(accentColor, accentColor.copy(alpha = 0.5f)))
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    chartTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            // ── 天气切换（仅电表支持） ──
            if (meterType == ChartViewModel.MeterType.ELECTRIC) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (showWeather) NeonBlue.copy(alpha = 0.12f)
                            else DarkSurface
                        )
                        .clickable { onToggleWeather() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (weatherLoading) {
                            Text("⏳", fontSize = 12.sp)
                        } else {
                            Text("🌡", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = when {
                                weatherLoading -> "加载中"
                                showWeather -> "隐藏"
                                else -> "温度"
                            },
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                            color = if (showWeather) NeonBlue else TextTertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (weatherError != null) {
            Text(
                text = weatherError,
                color = ErrorNeon,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 图表内容 ──
        chartContent()
    }
}

// ═══════════════════════════════════════════════════════════════
// Hero KPI 行 — 玻璃拟态卡片 + 数字动效
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HeroKpiRow(chartData: ChartData, billResult: BillData?, showCost: Boolean) {
    if (chartData.records.size < 2) return

    val first = chartData.records.first()
    val last = chartData.records.last()
    val totalCons = (last.electricTotal ?: 0.0) - (first.electricTotal ?: 0.0)
    val totalDays = ChronoUnit.DAYS.between(first.timestamp, last.timestamp) + 1
    val avgDaily = totalCons / totalDays

    // 费用模式：基于账单的有效费率换算
    val effectiveRate = if (billResult != null && billResult.totalKwh > 0)
        billResult.electricCost / billResult.totalKwh else 0.0
    val totalCost = totalCons * effectiveRate
    val dailyCost = avgDaily * effectiveRate

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showCost) {
            KpiCard(
                icon = Icons.Filled.SwapHoriz,
                label = "总费用",
                value = Formatters.formatDecimal2(totalCost),
                unit = "¥",
                accentColor = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                label = "日均费用",
                value = Formatters.formatDecimal2(dailyCost),
                unit = "¥/天",
                accentColor = NeonYellow,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                icon = Icons.Default.Bolt,
                label = "总用电",
                value = Formatters.formatDecimal2(totalCons),
                unit = "度",
                accentColor = ElectricColor,
                modifier = Modifier.weight(1f)
            )
        } else {
            KpiCard(
                icon = Icons.Default.Bolt,
                label = "总用电",
                value = Formatters.formatDecimal2(totalCons),
                unit = "度",
                accentColor = ElectricColor,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                label = "日均用电",
                value = Formatters.formatDecimal2(avgDaily),
                unit = "度/天",
                accentColor = NeonYellow,
                modifier = Modifier.weight(1f)
            )
            if (billResult != null) {
                KpiCard(
                    icon = Icons.Filled.SwapHoriz,
                    label = "总费用",
                    value = Formatters.formatDecimal2(billResult.totalCost),
                    unit = "¥",
                    accentColor = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun KpiCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        DarkCard,
                        DarkCard.copy(alpha = 0.85f)
                    )
                )
            )
            .border(
                1.dp,
                accentColor.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp)
            )
            .drawBehind {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(accentColor.copy(alpha = 0.12f), accentColor.copy(alpha = 0.03f))
                    ),
                    style = Stroke(width = 1.5f),
                    cornerRadius = CornerRadius(14.dp.toPx())
                )
            }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 图标 — 发光圆形容器
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 数值
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = accentColor,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 标签
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontFamily = MonoFontFamily,
                maxLines = 1,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 账单明细面板
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BillBreakdownPanel(bill: BillData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, ElectricColor.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        // ── 标题 ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp, 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NeonYellow)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "账单明细",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${Formatters.formatDecimal1(bill.totalKwh)} 度",
                color = TextTertiary,
                fontFamily = MonoFontFamily,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val peak = bill.peakKwh.coerceAtLeast(0.0)
        val valley = bill.valleyKwh.coerceAtLeast(0.0)
        val flat = (bill.totalKwh - peak - valley).coerceAtLeast(0.0)
        val totalForBar = peak + flat + valley
        val hasPeakValley = peak > 0 || valley > 0

        if (hasPeakValley && totalForBar > 0) {
            // ── 占比条（含百分比标注） ──
            val peakRatio = peak / totalForBar
            val flatRatio = flat / totalForBar
            val valleyRatio = valley / totalForBar

            Column {
                // 占比条
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    horizontalArrangement = Arrangement.Start
                ) {
                    if (peakRatio > 0.01f) {
                        Box(
                            modifier = Modifier
                                .weight(peakRatio.toFloat())
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(ElectricPeakColor, ElectricPeakColor.copy(alpha = 0.8f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (peakRatio > 0.12f) {
                                Text(
                                    "${(peakRatio * 100).toInt()}%",
                                    color = Color.White,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (flatRatio > 0.01f) {
                        Box(
                            modifier = Modifier
                                .weight(flatRatio.toFloat())
                                .fillMaxHeight()
                                .background(OutlineDark),
                            contentAlignment = Alignment.Center
                        ) {
                            if (flatRatio > 0.12f) {
                                Text(
                                    "${(flatRatio * 100).toInt()}%",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontFamily = MonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (valleyRatio > 0.01f) {
                        Box(
                            modifier = Modifier
                                .weight(valleyRatio.toFloat())
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(ElectricValleyColor, ElectricValleyColor.copy(alpha = 0.8f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (valleyRatio > 0.12f) {
                                Text(
                                    "${(valleyRatio * 100).toInt()}%",
                                    color = Color.Black,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── 图例 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendItem(ElectricPeakColor, "峰", "${Formatters.formatDecimal1(peak)}度")
                    LegendItem(OutlineDark, "平", "${Formatters.formatDecimal1(flat)}度")
                    LegendItem(ElectricValleyColor, "谷", "${Formatters.formatDecimal1(valley)}度")
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── 价格信息 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriceChip("峰", bill.peakPrice, ElectricPeakColor)
                    PriceChip("平", bill.flatPrice, Color(0xFF888888))
                    PriceChip("谷", bill.valleyPrice, ElectricValleyColor)
                }
            }
        }

        // ── 水费行 ──
        if (bill.waterTons > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(WaterColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.WaterDrop, null,
                            tint = WaterColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "水费 ${Formatters.formatDecimal1(bill.waterTons)} 吨",
                        color = TextSecondary,
                        fontFamily = MonoFontFamily,
                        fontSize = 13.sp
                    )
                }
                Text(
                    "¥${Formatters.formatDecimal2(bill.waterCost)}",
                    color = WaterColor,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 总计行 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            ElectricColor.copy(alpha = 0.1f),
                            ElectricColor.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(1.dp, ElectricColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "合计 ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontFamily = MonoFontFamily
                )
                Text(
                    "¥${Formatters.formatDecimal2(bill.totalCost)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricColor,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "$label  $value",
            color = TextSecondary,
            fontFamily = MonoFontFamily,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PriceChip(label: String, price: Double, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = TextTertiary,
            fontFamily = MonoFontFamily,
            fontSize = 10.sp
        )
        Text(
            "¥${Formatters.formatDecimal2(price)}",
            color = accent,
            fontFamily = MonoFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 月度预测面板
// ═══════════════════════════════════════════════════════════════

@Composable
private fun PredictionPanel(
    prediction: com.example.energyflow.data.MonthPrediction,
    predictedBill: PredictedBill?,
    tracking: PredictionTracking? = null,
    showCost: Boolean = false,
    billResult: BillData? = null
) {
    // 费用换算
    val rate = if (billResult != null && billResult.totalKwh > 0)
        billResult.electricCost / billResult.totalKwh else 0.0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, ElectricColor.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        // ── 标题 ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp, 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ElectricColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "月度预测",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 月份进度条（带动画） ──
        val totalMonthDays = prediction.daysElapsed + prediction.daysRemaining
        val elapsedRatio = if (totalMonthDays > 0)
            prediction.daysElapsed.toFloat() / totalMonthDays.toFloat() else 0f

        val animatedElapsed by animateFloatAsState(
            targetValue = elapsedRatio,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            label = "elapsedRatio"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "本月进度",
                color = TextSecondary,
                fontFamily = MonoFontFamily,
                fontSize = 12.sp
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${prediction.daysElapsed}",
                    color = ElectricColor,
                    fontFamily = MonoFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    " / $totalMonthDays 天",
                    color = TextTertiary,
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 进度条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedElapsed)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(ElectricColor, NeonYellow)
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 关键数据 三列 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showCost) {
                val consumedCost = prediction.consumedSoFarKwh * rate
                val dailyCost = prediction.dailyRateKwh * rate
                val predictedCost = if (predictedBill != null) predictedBill.predictedCost
                    else prediction.predictedTotalKwh * rate
                PredictionStatCard(
                    label = "已花费",
                    value = "¥${Formatters.formatDecimal2(consumedCost)}",
                    accentColor = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                PredictionStatCard(
                    label = "日均",
                    value = "¥${Formatters.formatDecimal2(dailyCost)}",
                    accentColor = ElectricColor,
                    modifier = Modifier.weight(1f)
                )
                PredictionStatCard(
                    label = "预计全月",
                    value = "¥${Formatters.formatDecimal2(predictedCost)}",
                    accentColor = NeonYellow,
                    modifier = Modifier.weight(1f)
                )
            } else {
                PredictionStatCard(
                    label = "已消耗",
                    value = "${Formatters.formatDecimal1(prediction.consumedSoFarKwh)} 度",
                    accentColor = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                PredictionStatCard(
                    label = "日均",
                    value = Formatters.formatDailyConsumption(prediction.dailyRateKwh),
                    accentColor = ElectricColor,
                    modifier = Modifier.weight(1f)
                )
                PredictionStatCard(
                    label = "预计全月",
                    value = "${Formatters.formatDecimal1(prediction.predictedTotalKwh)} 度",
                    accentColor = NeonYellow,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── 消耗进度条（已用 / 预计） ──
        Spacer(modifier = Modifier.height(14.dp))
        val consumeRatio = if (prediction.predictedTotalKwh > 0)
            (prediction.consumedSoFarKwh / prediction.predictedTotalKwh).toFloat().coerceIn(0f, 1f)
        else 0f

        val animatedConsumeRatio by animateFloatAsState(
            targetValue = consumeRatio,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            label = "consumeRatio"
        )

        val isOverBudget = consumeRatio > 0.85f
        val consumeColor = if (isOverBudget) ErrorNeon else ElectricColor

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "消耗进度",
                color = TextTertiary,
                fontFamily = MonoFontFamily,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(DarkSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedConsumeRatio)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(consumeColor, consumeColor.copy(alpha = 0.6f))
                            )
                        )
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "${(consumeRatio * 100).toInt()}%",
                color = if (isOverBudget) ErrorNeon else TextSecondary,
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ── 预计账单 ──
        if (predictedBill != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SuccessGreen.copy(alpha = 0.06f))
                    .border(1.dp, SuccessGreen.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "预计账单  ",
                        color = SuccessGreen.copy(alpha = 0.7f),
                        fontFamily = MonoFontFamily,
                        fontSize = 13.sp
                    )
                    Text(
                        "¥${Formatters.formatDecimal2(predictedBill.predictedCost)}",
                        color = SuccessGreen,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
        }

        // ── 预测跟踪 ──
        if (tracking != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📊", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "预测跟踪（自第 ${tracking.savedDay} 日起）",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TrackingRow("预期", "${Formatters.formatDecimal1(tracking.predictedTodayKwh)} 度", TextSecondary)
                    TrackingRow("实际", "${Formatters.formatDecimal1(tracking.actualTodayKwh)} 度", ElectricColor)
                    val varianceColor = if (tracking.varianceKwh > 0) ErrorNeon else SuccessGreen
                    TrackingRow(
                        "偏差",
                        "${if (tracking.varianceKwh > 0) "+" else ""}${Formatters.formatDecimal1(tracking.varianceKwh)} 度 (${Formatters.formatDecimal1(tracking.variancePercent)}%)",
                        varianceColor
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackingRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextTertiary, fontFamily = MonoFontFamily, fontSize = 12.sp)
        Text(value, color = color, fontFamily = MonoFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PredictionStatCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            color = accentColor,
            fontFamily = MonoFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            color = TextTertiary,
            fontFamily = MonoFontFamily,
            fontSize = 10.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 事件耗能分析面板
// ═══════════════════════════════════════════════════════════════

@Composable
private fun EventImpactPanel(
    impacts: List<com.example.energyflow.data.EventImpact>,
    aiAnalysis: String? = null,
    aiLoading: Boolean = false,
    onTriggerAi: () -> Unit = {},
    showCost: Boolean = false,
    billResult: BillData? = null
) {
    val rate = if (billResult != null && billResult.totalKwh > 0)
        billResult.electricCost / billResult.totalKwh else 0.0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, ElectricColor.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        // ── 标题行 + AI 按钮 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(4.dp, 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ElectricColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "事件耗能分析",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            // AI 分析按钮
            if (!aiLoading && aiAnalysis == null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(NeonBlue.copy(alpha = 0.1f))
                        .clickable { onTriggerAi() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            "AI 分析",
                            color = NeonBlue,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        impacts.forEachIndexed { index, impact ->
            val deltaKwh = impact.deltaKwh
            val deltaSign = if (deltaKwh > 0) "+" else ""
            val deltaColor = if (deltaKwh > 0) ErrorNeon else SuccessGreen
            // 按费用显示时，使用费用值而非 kWh
            val eventVal = if (showCost) impact.eventDailyKwh * rate else impact.eventDailyKwh
            val nonEventVal = if (showCost) impact.nonEventDailyKwh * rate else impact.nonEventDailyKwh
            val maxVal = maxOf(eventVal, nonEventVal, 0.01)
            val deltaDisplay = if (showCost) {
                val costDelta = deltaKwh * rate
                "${if (costDelta > 0) "+" else ""}¥${Formatters.formatDecimal2(costDelta)}/天"
            } else {
                "$deltaSign${Formatters.formatDecimal1(kotlin.math.abs(deltaKwh))} 度/天"
            }
            fun barFormat(v: Double) = if (showCost) Formatters.formatDecimal2(v) else Formatters.formatDecimal1(v)

            Column(modifier = Modifier.fillMaxWidth()) {
                // ── 事件标签 ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ElectricColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        impact.tag,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── 对比柱状图（更宽的条） ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 事件日柱
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "事件日",
                            color = TextTertiary,
                            fontFamily = MonoFontFamily,
                            fontSize = 9.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(ErrorNeon.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((eventVal / maxVal).toFloat().coerceIn(0.03f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(ErrorNeon, ErrorNeon.copy(alpha = 0.7f))
                                        )
                                    ),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    "${barFormat(eventVal)}",
                                    color = Color.White,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // 非事件日柱
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "非事件日",
                            color = TextTertiary,
                            fontFamily = MonoFontFamily,
                            fontSize = 9.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonBlue.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((nonEventVal / maxVal).toFloat().coerceIn(0.03f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(NeonBlue, NeonBlue.copy(alpha = 0.6f))
                                        )
                                    ),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    "${barFormat(nonEventVal)}",
                                    color = Color.White,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // 差异值
                    Column {
                        Text(
                            deltaDisplay,
                            color = deltaColor,
                            fontFamily = MonoFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "差异",
                            color = TextTertiary,
                            fontFamily = MonoFontFamily,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 统计行
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "事件日 ${impact.eventDays.toInt()} 天",
                        color = TextTertiary,
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp
                    )
                    Text(
                        "非事件日 ${impact.nonEventDays.toInt()} 天",
                        color = TextTertiary,
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp
                    )
                }
            }

            if (index < impacts.lastIndex) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DarkSurface)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // ── AI 全局分析结果 ──
        if (aiLoading) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeonBlue.copy(alpha = 0.06f))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "AI 正在分析全局能耗数据...",
                        color = NeonBlue,
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp
                    )
                }
            }
        } else if (!aiAnalysis.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeonBlue.copy(alpha = 0.06f))
                    .border(1.dp, NeonBlue.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI 全局分析",
                            color = NeonBlue,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MarkdownText(
                        text = aiAnalysis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 事件标注列表
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AnnotationsList(annotations: List<MeterRecord>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, ElectricColor.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp, 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ElectricColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "事件标注",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        annotations.forEachIndexed { index, record ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 时间线圆点
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ElectricColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        record.timestamp.format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm")),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        record.note ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricColor,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }

            if (index < annotations.lastIndex) {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 空状态占位
// ═══════════════════════════════════════════════════════════════

@Composable
private fun EmptyChartPlaceholder(meterType: ChartViewModel.MeterType = ChartViewModel.MeterType.ELECTRIC) {
    val (icon, label, color) = when (meterType) {
        ChartViewModel.MeterType.ELECTRIC -> Triple(Icons.Default.Bolt, "电表", ElectricColor)
        ChartViewModel.MeterType.WATER -> Triple(Icons.Default.WaterDrop, "水表", WaterColor)
        ChartViewModel.MeterType.GAS -> Triple(Icons.Default.Bolt, "燃气", GasColor)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 脉冲光环
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                color.copy(alpha = 0.15f),
                                color.copy(alpha = 0.05f),
                                DarkCard
                            )
                        )
                    )
                    .border(1.dp, color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null,
                    tint = color.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "暂无${label}数据",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "在「记录」页面添加${label}读数后，\n分析图表将在此展示",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                fontFamily = MonoFontFamily,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 简易 Markdown 渲染
// ═══════════════════════════════════════════════════════════════

/** 匹配行内 **加粗** 语法 */
private val BoldRegex = Regex("""\*\*(.+?)\*\*""")

@Composable
private fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val lines = text.split("\n")
    Column(modifier = modifier) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                // 空行 → 间距
                trimmed.isEmpty() -> Spacer(modifier = Modifier.height(4.dp))

                // ## / ### 标题
                trimmed.startsWith("### ") -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    BoldAwareLine(
                        text = trimmed.removePrefix("### "),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
                trimmed.startsWith("## ") -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    BoldAwareLine(
                        text = trimmed.removePrefix("## "),
                        color = NeonBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 无序列表 - / ·
                trimmed.startsWith("- ") || trimmed.startsWith("· ") -> {
                    val body = trimmed.removePrefix("- ").removePrefix("· ")
                    Row(modifier = Modifier.padding(start = 4.dp)) {
                        Text("•", color = NeonBlue.copy(alpha = 0.7f),
                            fontFamily = MonoFontFamily, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        BoldAwareLine(text = body, color = TextSecondary,
                            fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }

                // 有序列表 1. / 2. / ...
                trimmed.matches(Regex("""^\d+\.\s.*""")) -> {
                    val num = trimmed.substringBefore(".")
                    val body = trimmed.substringAfter(". ").ifEmpty { trimmed.substringAfter(".") }
                    Row(modifier = Modifier.padding(start = 4.dp)) {
                        Text("$num.", color = NeonBlue.copy(alpha = 0.7f),
                            fontFamily = MonoFontFamily, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        BoldAwareLine(text = body, color = TextSecondary,
                            fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }

                // 圈号编号 ① ② ③ ④ ⑤
                trimmed.startsWith("①") || trimmed.startsWith("②") || trimmed.startsWith("③") ||
                trimmed.startsWith("④") || trimmed.startsWith("⑤") -> {
                    BoldAwareLine(text = trimmed, color = TextPrimary,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // 普通文本（可能含行内 **加粗**）
                else -> {
                    BoldAwareLine(text = trimmed, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

/** 渲染一行文本，将 **加粗** 部分用 Bold AnnotatedString 展示。 */
@Composable
private fun BoldAwareLine(
    text: String,
    color: Color = TextSecondary,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier
) {
    if (!text.contains("**")) {
        Text(text, color = color, fontFamily = MonoFontFamily,
            fontWeight = fontWeight, fontSize = fontSize, lineHeight = 18.sp,
            modifier = modifier)
        return
    }
    val annotated = buildAnnotatedString {
        var last = 0
        BoldRegex.findAll(text).forEach { m ->
            if (m.range.first > last) {
                withStyle(SpanStyle(color = color, fontWeight = fontWeight,
                    fontFamily = MonoFontFamily, fontSize = fontSize)) {
                    append(text.substring(last, m.range.first))
                }
            }
            withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold,
                fontFamily = MonoFontFamily, fontSize = fontSize)) {
                append(m.groupValues[1])
            }
            last = m.range.last + 1
        }
        if (last < text.length) {
            withStyle(SpanStyle(color = color, fontWeight = fontWeight,
                fontFamily = MonoFontFamily, fontSize = fontSize)) {
                append(text.substring(last))
            }
        }
    }
    Text(annotated, lineHeight = 18.sp, modifier = modifier)
}

// ═══════════════════════════════════════════════════════════════
// 电/水/气 表类型选择器
// ═══════════════════════════════════════════════════════════════

@Composable
private fun MeterTypeSelector(
    selected: ChartViewModel.MeterType,
    onSelect: (ChartViewModel.MeterType) -> Unit
) {
    val types = listOf(
        ChartViewModel.MeterType.ELECTRIC to ("⚡ 电表" to ElectricColor),
        ChartViewModel.MeterType.WATER to ("💧 水表" to WaterColor),
        ChartViewModel.MeterType.GAS to ("🔥 燃气" to GasColor)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .border(1.dp, ElectricColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            types.forEach { (type, pair) ->
                val (label, color) = pair
                val isSelected = type == selected

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent)
                        .then(
                            if (isSelected) Modifier.border(
                                1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(10.dp)
                            ) else Modifier
                        )
                        .clickable { onSelect(type) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontFamily = MonoFontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) color else TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 电表分析面板
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ElectricAnalysisSection(
    chartData: ChartData,
    billResult: BillData?,
    showCost: Boolean,
    prediction: MonthPrediction?,
    predictedBill: PredictedBill?,
    predictionTracking: PredictionTracking?,
    eventImpacts: List<EventImpact>,
    aiAnalysis: String?,
    aiLoading: Boolean,
    weatherData: List<DailyWeather>,
    weatherLoading: Boolean,
    weatherError: String?,
    showWeather: Boolean,
    onShowWeatherChange: (Boolean) -> Unit,
    selectedChartIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    viewModel: ChartViewModel,
    carbonData: CarbonResult? = null
) {
    // ═══ Hero KPIs ═══
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
    ) {
        Column {
            HeroKpiRow(chartData, billResult, showCost)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // ═══ 折线图 ═══
    if (chartData.dailyConsumptions.isNotEmpty()) {
        val consumptionDates = remember(chartData, weatherData) {
            chartData.dailyConsumptions.map { it.date.toLocalDate() }
        }
        val interpolatedWeather = remember(chartData, weatherData) {
            WeatherInterpolator.interpolate(weatherData, consumptionDates)
        }

        ChartSection(
            showCost = showCost,
            showWeather = showWeather && weatherData.isNotEmpty(),
            weatherLoading = weatherLoading,
            weatherError = weatherError,
            onToggleWeather = {
                onShowWeatherChange(!showWeather)
                if (weatherData.isEmpty() || weatherError != null) {
                    viewModel.refreshWeather()
                }
            },
            meterType = ChartViewModel.MeterType.ELECTRIC,
            chartContent = {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    ConsumptionLineChart(
                        consumptions = chartData.dailyConsumptions,
                        showCost = showCost,
                        selectedIndex = selectedChartIndex,
                        onSelectedIndexChange = onSelectedIndexChange,
                        weatherByDate = interpolatedWeather,
                        forecastConsumptions = chartData.forecastConsumptions
                    )
                    if (showWeather && weatherData.isNotEmpty()) {
                        val fullWeather = remember(consumptionDates, interpolatedWeather) {
                            consumptionDates.mapNotNull { d ->
                                interpolatedWeather[d]?.let {
                                    DailyWeather(date = d, tempMax = it.tempMax, tempMin = it.tempMin)
                                }
                            }
                        }
                        WeatherOverlay(
                            weatherData = fullWeather,
                            consumptionDates = consumptionDates,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    // ═══ 账单明细 ═══
    if (billResult != null) {
        BillBreakdownPanel(billResult)
        Spacer(modifier = Modifier.height(24.dp))
    }

    // ═══ 月度预测 ═══
    if (prediction != null) {
        PredictionPanel(prediction, predictedBill, predictionTracking, showCost, billResult)
        Spacer(modifier = Modifier.height(24.dp))
    }

    // ═══ 碳足迹摘要 ═══
    if (carbonData != null) {
        CarbonSummaryCard(carbonData)
        Spacer(modifier = Modifier.height(24.dp))
    }

    // ═══ 事件耗能分析 + AI ═══
    if (eventImpacts.isNotEmpty()) {
        EventImpactPanel(
            impacts = eventImpacts,
            aiAnalysis = aiAnalysis,
            aiLoading = aiLoading,
            onTriggerAi = { viewModel.triggerAiAnalysis() },
            showCost = showCost,
            billResult = billResult
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    // ═══ 事件标注 ═══
    if (chartData.annotations.isNotEmpty()) {
        AnnotationsList(chartData.annotations)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
// 碳足迹摘要卡片
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CarbonSummaryCard(carbonResult: CarbonResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, SuccessGreen.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        // ── 标题 ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp, 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SuccessGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "碳足迹",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text("🌿", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 关键指标行 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // CO2 kg
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${Formatters.formatDecimal1(carbonResult.totalKgCO2)}",
                        color = SuccessGreen,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        "kg CO₂",
                        color = TextTertiary,
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp
                    )
                }
            }

            // 其中电排放
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${Formatters.formatDecimal1(carbonResult.electricKgCO2)}",
                        color = ElectricColor,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        "电排放 kg",
                        color = TextTertiary,
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp
                    )
                }
            }

            // Tree days
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${carbonResult.treeDays}",
                        color = SuccessGreen,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        "等效树·天",
                        color = TextTertiary,
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 水表分析面板
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WaterAnalysisSection(
    chartData: ChartData,
    waterBillResult: WaterBillData?,
    waterPrediction: MonthPrediction?,
    showCost: Boolean
) {
    // ═══ 水表 KPI ═══
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
    ) {
        Column {
            WaterKpiRow(chartData, waterBillResult, showCost)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // ═══ 水表趋势图 ═══
    if (chartData.dailyConsumptions.isNotEmpty()) {
        var selectedIndex by remember { mutableIntStateOf(-1) }
        ChartSection(
            showCost = showCost,
            showWeather = false,
            weatherLoading = false,
            weatherError = null,
            onToggleWeather = {},
            meterType = ChartViewModel.MeterType.WATER,
            chartContent = {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    ConsumptionLineChart(
                        consumptions = chartData.dailyConsumptions,
                        showCost = showCost,
                        selectedIndex = selectedIndex,
                        onSelectedIndexChange = { selectedIndex = it },
                        accentColor = WaterColor,
                        unitLabel = "吨"
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    // ═══ 水费账单 ═══
    if (waterBillResult != null) {
        WaterBillPanel(waterBillResult)
        Spacer(modifier = Modifier.height(24.dp))
    }

    // ═══ 月度预测 ═══
    if (waterPrediction != null) {
        WaterPredictionPanel(waterPrediction, waterBillResult)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
// 气表分析面板
// ═══════════════════════════════════════════════════════════════

@Composable
private fun GasAnalysisSection(chartData: ChartData) {
    // ═══ 气表 KPI ═══
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
    ) {
        Column {
            GasKpiRow(chartData)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // ═══ 气表趋势图 ═══
    if (chartData.dailyConsumptions.isNotEmpty()) {
        var selectedIndex by remember { mutableIntStateOf(-1) }
        ChartSection(
            showCost = false,
            showWeather = false,
            weatherLoading = false,
            weatherError = null,
            onToggleWeather = {},
            meterType = ChartViewModel.MeterType.GAS,
            chartContent = {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    ConsumptionLineChart(
                        consumptions = chartData.dailyConsumptions,
                        showCost = false,
                        selectedIndex = selectedIndex,
                        onSelectedIndexChange = { selectedIndex = it },
                        accentColor = GasColor,
                        unitLabel = "m³"
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
// 水表 KPI 行
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WaterKpiRow(chartData: ChartData, waterBillResult: WaterBillData?, showCost: Boolean) {
    if (chartData.records.size < 2) return

    val first = chartData.records.first()
    val last = chartData.records.last()
    val totalTons = (last.waterTotal ?: 0.0) - (first.waterTotal ?: 0.0)
    val totalDays = ChronoUnit.DAYS.between(first.timestamp, last.timestamp) + 1
    val avgDaily = totalTons / totalDays

    val effectiveRate = if (waterBillResult != null && waterBillResult.totalTons > 0)
        waterBillResult.waterCost / waterBillResult.totalTons else 0.0
    val totalCost = totalTons * effectiveRate
    val dailyCost = avgDaily * effectiveRate

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showCost) {
            KpiCard(
                icon = Icons.Filled.SwapHoriz,
                label = "总水费",
                value = Formatters.formatDecimal2(totalCost),
                unit = "¥",
                accentColor = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                label = "日均水费",
                value = Formatters.formatDecimal2(dailyCost),
                unit = "¥/天",
                accentColor = NeonYellow,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                icon = Icons.Default.WaterDrop,
                label = "总用水",
                value = Formatters.formatDecimal1(totalTons),
                unit = "吨",
                accentColor = WaterColor,
                modifier = Modifier.weight(1f)
            )
        } else {
            KpiCard(
                icon = Icons.Default.WaterDrop,
                label = "总用水",
                value = Formatters.formatDecimal1(totalTons),
                unit = "吨",
                accentColor = WaterColor,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                label = "日均用水",
                value = Formatters.formatDecimal1(avgDaily),
                unit = "吨/天",
                accentColor = NeonYellow,
                modifier = Modifier.weight(1f)
            )
            if (waterBillResult != null) {
                KpiCard(
                    icon = Icons.Filled.SwapHoriz,
                    label = "总水费",
                    value = Formatters.formatDecimal2(waterBillResult.waterCost),
                    unit = "¥",
                    accentColor = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 气表 KPI 行
// ═══════════════════════════════════════════════════════════════

@Composable
private fun GasKpiRow(chartData: ChartData) {
    if (chartData.records.size < 2) return

    val first = chartData.records.first()
    val last = chartData.records.last()
    val totalM3 = (last.gasTotal ?: 0.0) - (first.gasTotal ?: 0.0)
    val totalDays = ChronoUnit.DAYS.between(first.timestamp, last.timestamp) + 1
    val avgDaily = totalM3 / totalDays

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        KpiCard(
            icon = Icons.Default.Bolt,
            label = "总用气",
            value = Formatters.formatDecimal1(totalM3),
            unit = "m³",
            accentColor = GasColor,
            modifier = Modifier.weight(1f)
        )
        KpiCard(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            label = "日均用气",
            value = Formatters.formatDecimal1(avgDaily),
            unit = "m³/天",
            accentColor = NeonYellow,
            modifier = Modifier.weight(1f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 水费账单面板
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WaterBillPanel(bill: WaterBillData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, WaterColor.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        // 标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp, 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(WaterColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "水费明细",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${Formatters.formatDecimal1(bill.totalTons)} 吨",
                color = TextTertiary,
                fontFamily = MonoFontFamily,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 阶梯水价进度
        val tier1Ratio = (bill.totalTons / bill.tier1Limit).toFloat().coerceAtMost(1f)
        val isOverTier1 = bill.totalTons > bill.tier1Limit

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(tier1Ratio)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            if (isOverTier1) listOf(ErrorNeon, ErrorNeon.copy(alpha = 0.6f))
                            else listOf(WaterColor, WaterColor.copy(alpha = 0.6f))
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 档位标注
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("一档 ≤${bill.tier1Limit.toInt()}吨", color = TextTertiary, fontFamily = MonoFontFamily, fontSize = 9.sp)
            Text("二档 ${bill.tier1Limit.toInt()}-${bill.tier2Limit.toInt()}吨", color = TextTertiary, fontFamily = MonoFontFamily, fontSize = 9.sp)
            Text("三档 >${bill.tier2Limit.toInt()}吨", color = TextTertiary, fontFamily = MonoFontFamily, fontSize = 9.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 总计
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WaterColor.copy(alpha = 0.06f))
                .border(1.dp, WaterColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("合计 ", color = TextSecondary, fontFamily = MonoFontFamily)
                Text(
                    "¥${Formatters.formatDecimal2(bill.waterCost)}",
                    color = WaterColor,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "(${Formatters.formatDecimal2(bill.waterPrice)}/吨)",
                    color = TextTertiary,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp
                )
            }
        }

        // 接近阈值提示
        val remaining = bill.tier1Limit - bill.totalTons
        if (remaining > 0 && remaining < 3) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "⚠ 距二档水价仅剩 ${Formatters.formatDecimal1(remaining)} 吨",
                color = WarningNeon,
                fontFamily = MonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 水表月度预测面板
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WaterPredictionPanel(
    prediction: MonthPrediction,
    waterBillResult: WaterBillData?
) {
    val rate = if (waterBillResult != null && waterBillResult.totalTons > 0)
        waterBillResult.waterCost / waterBillResult.totalTons else 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, WaterColor.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp, 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(WaterColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "水表月度预测",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 月份进度条
        val totalMonthDays = prediction.daysElapsed + prediction.daysRemaining
        val elapsedRatio = if (totalMonthDays > 0)
            prediction.daysElapsed.toFloat() / totalMonthDays.toFloat() else 0f

        val animatedElapsed by animateFloatAsState(
            targetValue = elapsedRatio,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            label = "elapsedRatio"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedElapsed)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(WaterColor, WaterColor.copy(alpha = 0.6f))))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 关键数据三列
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PredictionStatCard(
                label = "已消耗",
                value = "${Formatters.formatDecimal1(prediction.consumedSoFarKwh)} 吨",
                accentColor = TextSecondary,
                modifier = Modifier.weight(1f)
            )
            PredictionStatCard(
                label = "日均",
                value = "${Formatters.formatDecimal1(prediction.dailyRateKwh)} 吨/天",
                accentColor = WaterColor,
                modifier = Modifier.weight(1f)
            )
            PredictionStatCard(
                label = "预计全月",
                value = "${Formatters.formatDecimal1(prediction.predictedTotalKwh)} 吨",
                accentColor = NeonYellow,
                modifier = Modifier.weight(1f)
            )
        }

        if (rate > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            val predictedCost = prediction.predictedTotalKwh * rate
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(WaterColor.copy(alpha = 0.06f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("预计水费 ", color = TextSecondary, fontFamily = MonoFontFamily, fontSize = 13.sp)
                    Text(
                        "¥${Formatters.formatDecimal2(predictedCost)}",
                        color = WaterColor,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

