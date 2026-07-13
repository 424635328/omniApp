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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class ChartViewModel @Inject constructor(
    private val repository: MeterRepository,
    private val costEngine: CostEngine,
    private val predictiveAnalyzer: PredictiveAnalyzer,
    private val eventImpactAnalyzer: EventImpactAnalyzer,
    private val weatherRepository: WeatherRepository,
    private val userPreferences: UserPreferences
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

    private val _eventImpacts = MutableStateFlow<List<EventImpact>>(emptyList())
    val eventImpacts: StateFlow<List<EventImpact>> = _eventImpacts.asStateFlow()

    // ── kWh / ¥ 切换 ─────────────────────────────────────

    private val _showCost = MutableStateFlow(false)
    val showCost: StateFlow<Boolean> = _showCost.asStateFlow()

    // ── 天气 ──────────────────────────────────────────────

    private val _weatherData = MutableStateFlow<List<DailyWeather>>(emptyList())
    val weatherData: StateFlow<List<DailyWeather>> = _weatherData.asStateFlow()

    private val _weatherLoading = MutableStateFlow(false)
    val weatherLoading: StateFlow<Boolean> = _weatherLoading.asStateFlow()

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

        val now = LocalDateTime.now()
        val filtered = when (_timeRange.value) {
            TimeRange.WEEK  -> records.filter { ChronoUnit.DAYS.between(it.timestamp, now) <= 7 }
            TimeRange.MONTH -> records.filter { ChronoUnit.DAYS.between(it.timestamp, now) <= 30 }
            TimeRange.YEAR  -> records.filter { ChronoUnit.DAYS.between(it.timestamp, now) <= 365 }
            TimeRange.ALL   -> records
        }.sortedBy { it.timestamp }

        if (filtered.isEmpty()) {
            _chartData.value = ChartData.Empty
            return
        }

        val dailies = calculateDailyConsumptions(filtered, estimatedCostPerKwh)
        val annotations = notesRecords.value.filter { nr -> filtered.any { it.id == nr.id } }

        _chartData.value = ChartData(
            records = filtered,
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
        val filtered = when (_timeRange.value) {
            TimeRange.WEEK  -> records.filter { ChronoUnit.DAYS.between(it.timestamp, LocalDateTime.now()) <= 7 }
            TimeRange.MONTH -> records.filter { ChronoUnit.DAYS.between(it.timestamp, LocalDateTime.now()) <= 30 }
            TimeRange.YEAR  -> records.filter { ChronoUnit.DAYS.between(it.timestamp, LocalDateTime.now()) <= 365 }
            TimeRange.ALL   -> records
        }.sortedBy { it.timestamp }

        var effectiveCostPerKwh = 0.0
        // 账单计算：取过滤后的总消耗
        if (filtered.size >= 2) {
            val first = filtered.first()
            val last = filtered.last()
            val totalKwh = (last.electricTotal ?: 0.0) - (first.electricTotal ?: 0.0)
            val peakKwh = ((last.electricPeak ?: 0.0) - (first.electricPeak ?: 0.0)).coerceAtLeast(0.0)
            val valleyKwh = ((last.electricValley ?: 0.0) - (first.electricValley ?: 0.0)).coerceAtLeast(0.0)
            val waterTons = if (last.isWaterRecorded && first.isWaterRecorded) {
                ((last.waterTotal ?: 0.0) - (first.waterTotal ?: 0.0)).coerceAtLeast(0.0)
            } else 0.0

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

        // 月度预测
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
        } else {
            _prediction.value = null
            _predictedBill.value = null
        }

        // 事件标记可以是没有读数的独立记录，需要同电表记录一起分析。
        _eventImpacts.value = eventImpactAnalyzer.analyzeWithRecords(
            (electricRecords.value + notesRecords.value).distinctBy { it.id }
        )

        updateChartData(records, effectiveCostPerKwh)

        // 天气数据加载（仅在"月"或"周"时加载，全量范围没必要）
        if (_timeRange.value == TimeRange.WEEK || _timeRange.value == TimeRange.MONTH) {
            loadWeather(filtered)
        } else {
            _weatherData.value = emptyList()
        }
    }

    private fun loadWeather(records: List<MeterRecord>) {
        viewModelScope.launch {
            _weatherLoading.value = true
            try {
                val apiKey = userPreferences.weatherApiKey.first()
                val cityId = userPreferences.weatherCityId.first()
                if (apiKey.isBlank()) return@launch

                if (records.isEmpty()) return@launch

                val startDate = records.first().timestamp.toLocalDate()
                val endDate = records.last().timestamp.toLocalDate()

                when (val result = weatherRepository.fetchHistorical(startDate, endDate, cityId, apiKey)) {
                    is WeatherResult.Success -> _weatherData.value = result.data
                    is WeatherResult.Error -> { /* silently fail, user can check settings */ }
                }
            } catch (_: Exception) { } finally {
                _weatherLoading.value = false
            }
        }
    }

    private val _predictedBill = MutableStateFlow<PredictedBill?>(null)
    val predictedBill: StateFlow<PredictedBill?> = _predictedBill.asStateFlow()

    private fun calculateDailyConsumptions(
        records: List<MeterRecord>,
        estimatedCostPerKwh: Double
    ): List<DailyConsumption> {
        if (records.size < 2) return emptyList()
        return records.sortedBy { it.timestamp }.windowed(2).mapNotNull { (prev, current) ->
            val days = ChronoUnit.DAYS.between(prev.timestamp, current.timestamp)
            val consumption = (current.electricTotal ?: 0.0) - (prev.electricTotal ?: 0.0)
            if (consumption < 0.0) return@mapNotNull null
            val daily = if (days > 0) consumption / days else consumption
            DailyConsumption(
                date = current.timestamp,
                consumption = consumption,
                dailyConsumption = daily,
                daysBetween = days,
                estimatedCost = daily * estimatedCostPerKwh
            )
        }
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
