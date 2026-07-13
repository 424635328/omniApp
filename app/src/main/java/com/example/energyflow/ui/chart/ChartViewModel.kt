package com.example.energyflow.ui.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.energyflow.data.CostEngine
import com.example.energyflow.data.DailyWeather
import com.example.energyflow.data.EventImpact
import com.example.energyflow.data.EventImpactAnalyzer
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.data.MeterRepository
import com.example.energyflow.data.MonthPrediction
import com.example.energyflow.data.PredictionSnapshot
import com.example.energyflow.data.PredictiveAnalyzer
import com.example.energyflow.data.UserPreferences
import com.example.energyflow.data.WeatherRepository
import com.example.energyflow.data.WeatherResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class ChartViewModel @Inject constructor(
    private val repository: MeterRepository,
    private val costEngine: CostEngine,
    private val predictiveAnalyzer: PredictiveAnalyzer,
    private val eventImpactAnalyzer: EventImpactAnalyzer,
    private val weatherRepository: WeatherRepository,
    private val userPreferences: UserPreferences,
    private val deepSeekRepository: com.example.energyflow.data.DeepSeekRepository
) : ViewModel() {

    val electricRecords: StateFlow<List<MeterRecord>> = repository.getElectricRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val waterRecords: StateFlow<List<MeterRecord>> = repository.getWaterRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notesRecords: StateFlow<List<MeterRecord>> = repository.getRecordsWithNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chartData = MutableStateFlow<ChartData>(ChartData.Empty)
    val chartData: StateFlow<ChartData> = _chartData.asStateFlow()

    private val _timeRange = MutableStateFlow(TimeRange.MONTH)
    val timeRange: StateFlow<TimeRange> = _timeRange.asStateFlow()

    // ── 计费/预测/事件 — UI 重算 ──────────────────────────

    private val _billResult = MutableStateFlow<BillData?>(null)
    val billResult: StateFlow<BillData?> = _billResult.asStateFlow()

    private val _prediction = MutableStateFlow<MonthPrediction?>(null)
    val prediction: StateFlow<MonthPrediction?> = _prediction.asStateFlow()

    private val _predictedBill = MutableStateFlow<PredictedBill?>(null)
    val predictedBill: StateFlow<PredictedBill?> = _predictedBill.asStateFlow()

    private val _predictionTracking = MutableStateFlow<PredictionTracking?>(null)
    val predictionTracking: StateFlow<PredictionTracking?> = _predictionTracking.asStateFlow()

    private val _eventImpacts = MutableStateFlow<List<EventImpact>>(emptyList())
    val eventImpacts: StateFlow<List<EventImpact>> = _eventImpacts.asStateFlow()

    // ── AI 分析 ───────────────────────────────────────────

    private val _aiAnalysis = MutableStateFlow<String?>(null)
    val aiAnalysis: StateFlow<String?> = _aiAnalysis.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    // ── kWh / ¥ 切换 ─────────────────────────────────────

    private val _showCost = MutableStateFlow(false)
    val showCost: StateFlow<Boolean> = _showCost.asStateFlow()

    // ── 天气 ──────────────────────────────────────────────

    private val _weatherData = MutableStateFlow<List<DailyWeather>>(emptyList())
    val weatherData: StateFlow<List<DailyWeather>> = _weatherData.asStateFlow()

    private val _weatherLoading = MutableStateFlow(false)
    val weatherLoading: StateFlow<Boolean> = _weatherLoading.asStateFlow()

    private val _weatherError = MutableStateFlow<String?>(null)
    val weatherError: StateFlow<String?> = _weatherError.asStateFlow()

    init {
        viewModelScope.launch {
            electricRecords.collect { records ->
                recalculateAnalytics(records)
            }
        }
        viewModelScope.launch {
            userPreferences.chartShowCost.collect { show ->
                _showCost.value = show
            }
        }
        viewModelScope.launch {
            userPreferences.billingRules.collect {
                recalculateAnalytics(electricRecords.value)
            }
        }
        viewModelScope.launch {
            notesRecords.collect {
                recalculateAnalytics(electricRecords.value)
            }
        }
        // 启动时自动获取一次 7 天预报（每日仅一次，Open-Meteo 免费）
        viewModelScope.launch {
            val today = java.time.LocalDate.now().toString()
            val cachedDate = userPreferences.weatherForecastDate.first()
            if (cachedDate != today) {
                autoFetchForecast()
            }
        }
    }

    /** 启动时自动获取 7 天预报并缓存到 _weatherData（每日仅一次）。 */
    private suspend fun autoFetchForecast() {
        val result = weatherRepository.fetch7DayForecast()
        if (result is WeatherResult.Success) {
            _weatherData.value = result.data
            userPreferences.cacheWeatherForecast("cached", java.time.LocalDate.now().toString())
        }
    }

    fun toggleShowCost() {
        val newValue = !_showCost.value
        _showCost.value = newValue
        viewModelScope.launch {
            userPreferences.setChartShowCost(newValue)
        }
    }

    fun setTimeRange(range: TimeRange) {
        _timeRange.value = range
        viewModelScope.launch {
            recalculateAnalytics(electricRecords.value)
        }
    }

    private fun updateChartData(records: List<MeterRecord>, estimatedCostPerKwh: Double = 0.0) {
        if (records.isEmpty()) {
            _chartData.value = ChartData.Empty
            return
        }

        val today = LocalDate.now()
        val windowStart = when (_timeRange.value) {
            TimeRange.WEEK  -> today.minusDays(6)   // 最近 7 天（含今天）
            TimeRange.MONTH -> today.minusDays(29)  // 最近 30 天
            TimeRange.YEAR  -> today.minusDays(364) // 最近 365 天
            TimeRange.ALL   -> null
        }

        // 窗口内的记录（给 KPI / TopBar 用）
        val inWindowRecords = if (windowStart != null) {
            records.filter { !it.timestamp.toLocalDate().isBefore(windowStart) }
                .sortedBy { it.timestamp }
        } else {
            records.sortedBy { it.timestamp }
        }

        if (inWindowRecords.isEmpty()) {
            _chartData.value = ChartData.Empty
            return
        }

        // 插值用列表：补入窗口前的一条基线记录
        val forInterpolation = if (windowStart != null) {
            prependBaseline(records, inWindowRecords, windowStart)
        } else {
            inWindowRecords
        }

        val dailies = calculateDailyConsumptions(forInterpolation, estimatedCostPerKwh)
        val annotations = notesRecords.value.filter { nr -> inWindowRecords.any { it.id == nr.id } }

        _chartData.value = ChartData(
            records = inWindowRecords,
            dailyConsumptions = dailies,
            annotations = annotations,
            timeRange = _timeRange.value
        )
    }

    private suspend fun recalculateAnalytics(records: List<MeterRecord>) {
        if (records.isEmpty()) {
            _chartData.value = ChartData.Empty
            _billResult.value = null
            _prediction.value = null
            _predictedBill.value = null
            _eventImpacts.value = emptyList()
            return
        }
        val today = LocalDate.now()
        val windowStart = when (_timeRange.value) {
            TimeRange.WEEK  -> today.minusDays(6)   // 最近 7 天（含今天）
            TimeRange.MONTH -> today.minusDays(29)  // 最近 30 天
            TimeRange.YEAR  -> today.minusDays(364) // 最近 365 天
            TimeRange.ALL   -> null
        }

        // 窗口内的记录（给账单/KPI 用）
        val inWindowRecords = if (windowStart != null) {
            records.filter { !it.timestamp.toLocalDate().isBefore(windowStart) }
                .sortedBy { it.timestamp }
        } else {
            records.sortedBy { it.timestamp }
        }

        // 插值用列表：补入窗口前的一条基线
        val forInterpolation = if (windowStart != null) {
            prependBaseline(records, inWindowRecords, windowStart)
        } else {
            inWindowRecords
        }

        var effectiveCostPerKwh = 0.0
        if (inWindowRecords.size >= 2) {
            val first = inWindowRecords.first()
            val last = inWindowRecords.last()
            val totalKwh = (last.electricTotal ?: 0.0) - (first.electricTotal ?: 0.0)

            // 峰/谷基线：若过滤后首条记录缺少峰谷数据，
            // 在全体记录中查找最近的含峰谷的记录作为基线
            val firstPeak = first.electricPeak
                ?: records.filter { it.timestamp < first.timestamp && it.electricPeak != null }
                    .maxByOrNull { it.timestamp }?.electricPeak
                ?: 0.0
            val firstValley = first.electricValley
                ?: records.filter { it.timestamp < first.timestamp && it.electricValley != null }
                    .maxByOrNull { it.timestamp }?.electricValley
                ?: 0.0
            val lastPeak = last.electricPeak ?: firstPeak
            val lastValley = last.electricValley ?: firstValley
            val peakKwh = (lastPeak - firstPeak).coerceAtLeast(0.0).coerceAtMost(totalKwh)
            val valleyKwh = (lastValley - firstValley).coerceAtLeast(0.0).coerceAtMost(totalKwh - peakKwh)

            // 水表基线同理
            val firstWater = if (first.isWaterRecorded) (first.waterTotal ?: 0.0)
                else records.filter { it.timestamp < first.timestamp && it.isWaterRecorded && it.waterTotal != null }
                    .maxByOrNull { it.timestamp }?.waterTotal ?: 0.0
            val lastWater = if (last.isWaterRecorded) (last.waterTotal ?: 0.0) else 0.0
            val waterTons = (lastWater - firstWater).coerceAtLeast(0.0)

            val bill = costEngine.calculateBill(totalKwh, peakKwh, valleyKwh, waterTons)
            effectiveCostPerKwh = if (totalKwh > 0.0) bill.electricTotalCost / totalKwh else 0.0
            _billResult.value = BillData(
                totalKwh = totalKwh,
                peakKwh = peakKwh,
                valleyKwh = valleyKwh,
                waterTons = waterTons,
                electricCost = bill.electricTotalCost,
                waterCost = bill.waterTotalCost,
                totalCost = bill.totalCost,
                peakPrice = bill.peakPrice,
                valleyPrice = bill.valleyPrice,
                flatPrice = bill.flatPrice,
                waterPrice = bill.waterPrice
            )
        } else {
            _billResult.value = null
        }

        // ── 月度预测（始终使用本月数据，与时间范围无关） ──
        val pred = predictiveAnalyzer.predictMonth(electricRecords.value)
        if (pred != null) {
            val totalInRange = _billResult.value?.totalKwh ?: 0.0
            val peakRatio = if (totalInRange > 0.0) (_billResult.value?.peakKwh ?: 0.0) / totalInRange else 0.0
            val valleyRatio = if (totalInRange > 0.0) (_billResult.value?.valleyKwh ?: 0.0) / totalInRange else 0.0
            val predictedPeak = pred.predictedTotalKwh * peakRatio
            val predictedValley = pred.predictedTotalKwh * valleyRatio
            val predBill = costEngine.calculateBill(
                totalKwh = pred.predictedTotalKwh,
                peakKwh = predictedPeak,
                valleyKwh = predictedValley
            ).electricTotalCost
            _prediction.value = pred
            _predictedBill.value = PredictedBill(
                totalKwh = pred.predictedTotalKwh,
                predictedCost = predBill
            )

            // ── 保存预测快照（仅当月第一次预测时保存，或日期间隔 ≥1 天时更新） ──
            val now = LocalDateTime.now()
            val ym = "${now.year}-${now.monthValue.toString().padStart(2, '0')}"
            val cachedJson = userPreferences.predictionSnapshot.first()
            val cachedSnapshot = if (cachedJson != null) {
                try { kotlinx.serialization.json.Json.decodeFromString<PredictionSnapshot>(cachedJson) }
                catch (_: Exception) { null }
            } else null
            if (cachedSnapshot?.savedYearMonth != ym || now.dayOfMonth - (cachedSnapshot?.savedDayOfMonth ?: 0) >= 1) {
                val snapshot = PredictionSnapshot(
                    savedYearMonth = ym,
                    savedDayOfMonth = now.dayOfMonth,
                    predictedTotalKwh = pred.predictedTotalKwh,
                    dailyRateKwh = pred.dailyRateKwh,
                    consumedSoFarAtSave = pred.consumedSoFarKwh
                )
                userPreferences.savePredictionSnapshot(
                    kotlinx.serialization.json.Json.encodeToString(PredictionSnapshot.serializer(), snapshot)
                )
            }

            // ── 计算预测跟踪（对比已保存的预测 vs 今日实际） ──
            computePredictionTracking(cachedSnapshot, pred)
        } else {
            _prediction.value = null
            _predictedBill.value = null
            _predictionTracking.value = null
        }

        // 事件标记可以是没有读数的独立记录，需要同电表记录一起分析。
        _eventImpacts.value = eventImpactAnalyzer.analyzeWithRecords(
            (electricRecords.value + notesRecords.value).distinctBy { it.id }
        )

        updateChartData(records, effectiveCostPerKwh)

        // 天气数据加载（仅在"月"或"周"时加载）
        if (_timeRange.value == TimeRange.WEEK || _timeRange.value == TimeRange.MONTH) {
            loadWeather(inWindowRecords)
        } else {
            _weatherData.value = emptyList()
        }
    }


    /** 用户手动触发 AI 全局能耗分析。 */
    fun triggerAiAnalysis() {
        val chart = _chartData.value
        if (chart.dailyConsumptions.isEmpty()) return

        _aiLoading.value = true
        _aiAnalysis.value = null

        viewModelScope.launch {
            try {
                val prompt = buildComprehensivePrompt(
                    chartData = _chartData.value,
                    bill = _billResult.value,
                    prediction = _prediction.value,
                    predictedBill = _predictedBill.value,
                    weather = _weatherData.value,
                    events = _eventImpacts.value
                )
                val result = deepSeekRepository.analyze(prompt)
                _aiAnalysis.value = result
            } catch (_: Exception) {
                _aiAnalysis.value = null
            } finally {
                _aiLoading.value = false
            }
        }
    }

    /** 组装全局分析提示词。 */
    private fun buildComprehensivePrompt(
        chartData: ChartData,
        bill: BillData?,
        prediction: MonthPrediction?,
        predictedBill: PredictedBill?,
        weather: List<DailyWeather>,
        events: List<EventImpact>
    ): String = buildString {
        val dailies = chartData.dailyConsumptions
        if (dailies.isEmpty()) return@buildString

        // 日期范围
        val firstDate = dailies.first().date.toLocalDate()
        val lastDate = dailies.last().date.toLocalDate()
        appendLine("## 数据概览")
        appendLine("- 时间范围：${firstDate} 至 ${lastDate}（${dailies.size} 天）")

        // 总览
        val totalKwh = dailies.sumOf { it.dailyConsumption }
        val avgKwh = totalKwh / dailies.size
        val maxDay = dailies.maxBy { it.dailyConsumption }
        val minDay = dailies.minBy { it.dailyConsumption }
        appendLine("- 总耗电：${"%.1f".format(totalKwh)} 度")
        appendLine("- 日均：${"%.1f".format(avgKwh)} 度")
        appendLine("- 最高日：${maxDay.date.toLocalDate()} ${"%.1f".format(maxDay.dailyConsumption)} 度")
        appendLine("- 最低日：${minDay.date.toLocalDate()} ${"%.1f".format(minDay.dailyConsumption)} 度")

        // 趋势
        val firstHalf = dailies.take(dailies.size / 2)
        val secondHalf = dailies.drop(dailies.size / 2)
        val firstAvg = if (firstHalf.isNotEmpty()) firstHalf.sumOf { it.dailyConsumption } / firstHalf.size else 0.0
        val secondAvg = if (secondHalf.isNotEmpty()) secondHalf.sumOf { it.dailyConsumption } / secondHalf.size else 0.0
        appendLine("- 前半段日均：${"%.1f".format(firstAvg)} 度")
        appendLine("- 后半段日均：${"%.1f".format(secondAvg)} 度")
        appendLine("- 趋势：${if (secondAvg > firstAvg * 1.05) "上升" else if (secondAvg < firstAvg * 0.95) "下降" else "平稳"}")

        // 异常检测
        val stdDev = kotlin.math.sqrt(dailies.map { (it.dailyConsumption - avgKwh).let { d -> d * d } }.average())
        val anomalies = dailies.filter { kotlin.math.abs(it.dailyConsumption - avgKwh) > stdDev * 1.5 }
        if (anomalies.isNotEmpty()) {
            appendLine("- 异常日（偏离均值 >1.5σ）：${anomalies.size} 天")
            anomalies.take(5).forEach {
                appendLine("  · ${it.date.toLocalDate()} ${"%.1f".format(it.dailyConsumption)} 度 " +
                    "(${if (it.dailyConsumption > avgKwh) "↑偏高" else "↓偏低"})")
            }
        }

        // 天气关联
        if (weather.isNotEmpty()) {
            val weatherByDate = weather.associateBy { it.date }
            val hotDays = dailies.filter { d ->
                val w = weatherByDate[d.date.toLocalDate().toString()]
                w != null && w.tempMax > 32
            }
            if (hotDays.isNotEmpty()) {
                val hotAvg = hotDays.sumOf { it.dailyConsumption } / hotDays.size
                val coolDays = dailies.filter { d ->
                    val w = weatherByDate[d.date.toLocalDate().toString()]
                    w != null && w.tempMax <= 32
                }
                val coolAvg = if (coolDays.isNotEmpty()) coolDays.sumOf { it.dailyConsumption } / coolDays.size else 0.0
                appendLine("- 高温日(>32°C)日均：${"%.1f".format(hotAvg)} 度 vs 非高温日均：${"%.1f".format(coolAvg)} 度")
            }
        }

        // 账单
        if (bill != null) {
            appendLine("- 预估电费：¥${"%.2f".format(bill.electricCost)}（${"%.1f".format(bill.totalKwh)} 度）")
            if (bill.peakKwh > 0 || bill.valleyKwh > 0) {
                appendLine("  峰 ${"%.1f".format(bill.peakKwh)} 度 / 谷 ${"%.1f".format(bill.valleyKwh)} 度")
            }
        }

        // 预测
        if (prediction != null && predictedBill != null) {
            appendLine("- 本月预计全月：${"%.1f".format(prediction.predictedTotalKwh)} 度 / ¥${"%.2f".format(predictedBill.predictedCost)}")
        }

        // 事件
        if (events.isNotEmpty()) {
            appendLine("## 事件影响")
            events.forEach { e ->
                val sign = if (e.deltaKwh > 0) "多耗" else "少耗"
                appendLine("- ${e.tag}：${sign} ${"%.1f".format(kotlin.math.abs(e.deltaKwh))} 度/天（${e.eventDays.toInt()}天 vs ${e.nonEventDays.toInt()}天）")
            }
        }

        appendLine()
        appendLine("请基于以上完整数据给出全局分析。")
    }

    /** 用户手动刷新天气（由按钮触发）。 */
    fun refreshWeather() {
        val currentTimeRange = _timeRange.value
        if (currentTimeRange != TimeRange.WEEK && currentTimeRange != TimeRange.MONTH) return
        val today = LocalDate.now()
        val records = when (currentTimeRange) {
            TimeRange.WEEK  -> electricRecords.value.filter { !it.timestamp.toLocalDate().isBefore(today.minusDays(6)) }
            TimeRange.MONTH -> electricRecords.value.filter { !it.timestamp.toLocalDate().isBefore(today.minusDays(29)) }
            else -> return
        }.sortedBy { it.timestamp }
        loadWeather(records)
    }

    private fun loadWeather(records: List<MeterRecord>) {
        viewModelScope.launch {
            _weatherLoading.value = true
            _weatherError.value = null
            try {
                if (records.isEmpty()) return@launch

                // Open-Meteo 历史天气（免费，无需 Key）
                val historicalResult = weatherRepository.fetchHistorical(
                    records.first().timestamp.toLocalDate(),
                    records.last().timestamp.toLocalDate()
                )
                when (historicalResult) {
                    is WeatherResult.Success -> {
                        if (historicalResult.data.isNotEmpty()) {
                            _weatherData.value = historicalResult.data
                        } else {
                            fallbackToForecast()
                        }
                    }
                    is WeatherResult.Error -> fallbackToForecast()
                }
            } catch (e: Exception) {
                _weatherError.value = "天气获取失败: ${e.message}"
            } finally {
                _weatherLoading.value = false
            }
        }
    }

    private suspend fun fallbackToForecast() {
        val forecast = weatherRepository.fetch7DayForecast()
        if (forecast is WeatherResult.Success && forecast.data.isNotEmpty()) {
            _weatherData.value = forecast.data
        } else {
            _weatherError.value = "无法获取天气数据"
        }
    }

    /** 对比已保存的预测快照 vs 今日实际，计算偏差。 */
    private fun computePredictionTracking(
        snapshot: PredictionSnapshot?,
        currentPrediction: MonthPrediction
    ) {
        val now = LocalDateTime.now()
        val ym = "${now.year}-${now.monthValue.toString().padStart(2, '0')}"
        if (snapshot == null || snapshot.savedYearMonth != ym) {
            _predictionTracking.value = null
            return
        }
        val daysSinceSave = now.dayOfMonth - snapshot.savedDayOfMonth
        if (daysSinceSave <= 0) {
            _predictionTracking.value = null
            return
        }
        // 预期到今天应消耗 = 保存时的已消耗 + 日均 × 经过天数
        val expectedToday = snapshot.consumedSoFarAtSave + snapshot.dailyRateKwh * daysSinceSave
        val actualToday = currentPrediction.consumedSoFarKwh
        val variance = actualToday - expectedToday
        val variancePct = if (expectedToday > 0.0) (variance / expectedToday * 100) else 0.0
        _predictionTracking.value = PredictionTracking(
            yearMonth = ym,
            savedDay = snapshot.savedDayOfMonth,
            predictedTodayKwh = expectedToday,
            actualTodayKwh = actualToday,
            varianceKwh = variance,
            variancePercent = variancePct
        )
    }

    /**
     * 如果窗口内第一条记录晚于窗口起点，补入窗口前的一条基线记录。
     * 这样 calculateDailyConsumptions 的插值可以覆盖到窗口第一天。
     */
    private fun prependBaseline(
        allRecords: List<MeterRecord>,
        inWindow: List<MeterRecord>,
        windowStart: LocalDate
    ): List<MeterRecord> {
        if (inWindow.isEmpty()) return inWindow
        val firstInWindow = inWindow.first()
        if (!firstInWindow.timestamp.toLocalDate().isAfter(windowStart)) return inWindow
        val baseline = allRecords
            .filter { it.timestamp.toLocalDate().isBefore(windowStart) && it.isElectricRecorded }
            .maxByOrNull { it.timestamp }
        return if (baseline != null) listOf(baseline) + inWindow else inWindow
    }

    /**
     * 计算每日消耗，并对缺失日期进行线性插值。
     *
     * 当两次读数间隔 N 天时，将总消耗均摊到每一天，
     * 生成 N 个数据点（每个点代表一天），消除图表中的日期缺口。
     *
     * 例如：7.1 读数 100，7.4 读数 130 → 消耗 30 度 / 3 天 = 10 度/天
     * 生成 3 个点：7.2(10度), 7.3(10度), 7.4(10度)
     */
    private fun calculateDailyConsumptions(
        records: List<MeterRecord>,
        estimatedCostPerKwh: Double
    ): List<DailyConsumption> {
        if (records.size < 2) return emptyList()

        val sorted = records.sortedBy { it.timestamp }
        val result = mutableListOf<DailyConsumption>()

        for (i in 0 until sorted.size - 1) {
            val prev = sorted[i]
            val current = sorted[i + 1]

            val totalConsumption =
                (current.electricTotal ?: 0.0) - (prev.electricTotal ?: 0.0)
            if (totalConsumption < 0.0) continue

            val days = ChronoUnit.DAYS.between(prev.timestamp, current.timestamp)
                .coerceAtLeast(1)

            val dailyAvg = totalConsumption / days

            // 为缺口中的每一天生成数据点
            for (d in 1..days) {
                val pointDate = prev.timestamp.plusDays(d)
                val isLast = d == days

                result.add(
                    DailyConsumption(
                        date = pointDate,
                        consumption = if (isLast) totalConsumption else dailyAvg,
                        dailyConsumption = dailyAvg,
                        daysBetween = if (isLast) days else 1,
                        estimatedCost = dailyAvg * estimatedCostPerKwh
                    )
                )
            }
        }

        // ── 去重：同一天只保留最新时间点 ──
        return result
            .groupBy { it.date.toLocalDate() }
            .map { (_, list) -> list.last() }
            .sortedBy { it.date }
    }
}

// ── Data 📊 ───────────────────────────────────────────────

data class ChartData(
    val records: List<MeterRecord>,
    val dailyConsumptions: List<DailyConsumption>,
    val annotations: List<MeterRecord>,
    val timeRange: TimeRange
) {
    companion object {
        val Empty = ChartData(emptyList(), emptyList(), emptyList(), TimeRange.MONTH)
    }
}

data class DailyConsumption(
    val date: LocalDateTime,
    val consumption: Double,
    val dailyConsumption: Double,
    val daysBetween: Long,
    val estimatedCost: Double = 0.0
)

enum class TimeRange { WEEK, MONTH, YEAR, ALL }

data class BillData(
    val totalKwh: Double,
    val peakKwh: Double,
    val valleyKwh: Double,
    val waterTons: Double,
    val electricCost: Double,
    val waterCost: Double,
    val totalCost: Double,
    val peakPrice: Double,
    val valleyPrice: Double,
    val flatPrice: Double,
    val waterPrice: Double
)

data class PredictedBill(
    val totalKwh: Double,
    val predictedCost: Double
)

/** 预测跟踪——对比已保存的预测 vs 实际进度。 */
data class PredictionTracking(
    val yearMonth: String,
    val savedDay: Int,
    val predictedTodayKwh: Double,
    val actualTodayKwh: Double,
    val varianceKwh: Double,
    val variancePercent: Double
)
