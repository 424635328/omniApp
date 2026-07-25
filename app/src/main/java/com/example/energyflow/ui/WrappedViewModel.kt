package com.example.energyflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.energyflow.data.CostEngine
import com.example.energyflow.data.MeterRepository
import com.example.energyflow.data.PredictiveAnalyzer
import com.example.energyflow.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class WrappedViewModel @Inject constructor(
    private val repository: MeterRepository,
    private val costEngine: CostEngine,
    private val predictiveAnalyzer: PredictiveAnalyzer,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(WrappedState(
        yearMonth = YearMonth.now(),
        totalKwh = 0.0,
        totalCost = 0.0,
        totalCo2Kg = 0.0,
        treeDays = 0,
        badges = emptyList(),
        peakKwh = 0.0,
        valleyKwh = 0.0,
        flatKwh = 0.0,
        hotDays = 0,
        coldDays = 0,
        avgTemp = null,
        eventHighlights = emptyList(),
        previousPeriodKwh = null,
        previousPeriodCost = null,
        tips = emptyList(),
        recordCount = 0,
        isLoading = true
    ))
    val state: StateFlow<WrappedState> = _state.asStateFlow()

    fun loadReport(yearMonth: YearMonth) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            withContext(Dispatchers.IO) {
                val electricRecords = repository.getElectricRecords().first()
                val monthRecords = electricRecords.filter {
                    YearMonth.from(it.timestamp) == yearMonth && it.electricTotal != null
                }.sortedBy { it.timestamp }

                val prevMonth = yearMonth.minusMonths(1)
                val prevMonthRecords = electricRecords.filter {
                    YearMonth.from(it.timestamp) == prevMonth && it.electricTotal != null
                }.sortedBy { it.timestamp }

                if (monthRecords.size < 2) {
                    _state.value = WrappedState(
                        yearMonth = yearMonth,
                        totalKwh = 0.0, totalCost = 0.0, totalCo2Kg = 0.0,
                        treeDays = 0, badges = emptyList(),
                        peakKwh = 0.0, valleyKwh = 0.0, flatKwh = 0.0,
                        hotDays = 0, coldDays = 0, avgTemp = null,
                        eventHighlights = emptyList(),
                        previousPeriodKwh = null, previousPeriodCost = null,
                        tips = listOf("添加更多电表数据以生成报告"),
                        recordCount = monthRecords.size, isLoading = false
                    )
                    return@withContext
                }

                val first = monthRecords.first()
                val last = monthRecords.last()
                val totalKwh = (last.electricTotal!! - first.electricTotal!!).coerceAtLeast(0.0)

                val firstPeak = first.electricPeak ?: 0.0
                val firstValley = first.electricValley ?: 0.0
                val lastPeak = last.electricPeak ?: 0.0
                val lastValley = last.electricValley ?: 0.0
                val peakKwh = (lastPeak - firstPeak).coerceAtLeast(0.0).coerceAtMost(totalKwh)
                val valleyKwh = (lastValley - firstValley).coerceAtLeast(0.0).coerceAtMost(totalKwh - peakKwh)
                val flatKwh = (totalKwh - peakKwh - valleyKwh).coerceAtLeast(0.0)

                val bill = costEngine.calculateBill(totalKwh, peakKwh, valleyKwh)
                val totalCost = bill.totalCost

                val totalCo2Kg = totalKwh * 0.7
                val treeDays = (totalCo2Kg / 20.0).toInt().coerceAtLeast(0)

                val badges = mutableListOf<String>()
                if (totalKwh < 300) badges.add("⚡节能先锋")
                else if (totalKwh < 500) badges.add("⚡合理用电")
                if (treeDays > 5) badges.add("🌿绿色达人")
                if (peakKwh / totalKwh < 0.3 && totalKwh > 0) badges.add("🏔️错峰能手")
                if (totalKwh > 0) badges.add("📊坚持记录")
                if (badges.isEmpty()) badges.add("🌱初来乍到")

                val previousPeriodKwh: Double?
                val previousPeriodCost: Double?
                if (prevMonthRecords.size >= 2) {
                    val prevFirst = prevMonthRecords.first()
                    val prevLast = prevMonthRecords.last()
                    val prevKwh = (prevLast.electricTotal!! - prevFirst.electricTotal!!).coerceAtLeast(0.0)
                    previousPeriodKwh = prevKwh
                    val prevBill = costEngine.calculateBill(prevKwh)
                    previousPeriodCost = prevBill.totalCost
                } else {
                    previousPeriodKwh = null
                    previousPeriodCost = null
                }

                val tips = mutableListOf<String>()
                if (previousPeriodKwh != null && totalKwh > previousPeriodKwh) {
                    tips.add("本月用电较上月增加，建议关注峰谷时段")
                } else if (previousPeriodKwh != null) {
                    tips.add("本月用电较上月减少，继续保持！")
                }
                if (peakKwh > 0) {
                    tips.add("将大功率电器移至谷电时段使用可节省电费")
                }
                tips.add("定期记录数据，获取更精准的能耗分析")
                if (tips.isEmpty()) tips.add("继续坚持记录，下月将生成更详细的报告")

                _state.value = WrappedState(
                    yearMonth = yearMonth,
                    totalKwh = totalKwh,
                    totalCost = totalCost,
                    totalCo2Kg = totalCo2Kg,
                    treeDays = treeDays,
                    badges = badges,
                    peakKwh = peakKwh,
                    valleyKwh = valleyKwh,
                    flatKwh = flatKwh,
                    hotDays = 0,
                    coldDays = 0,
                    avgTemp = null,
                    eventHighlights = emptyList(),
                    previousPeriodKwh = previousPeriodKwh,
                    previousPeriodCost = previousPeriodCost,
                    tips = tips,
                    recordCount = monthRecords.size,
                    isLoading = false
                )
            }
        }
    }
}
