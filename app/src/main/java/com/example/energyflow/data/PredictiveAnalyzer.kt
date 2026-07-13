package com.example.energyflow.data

import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 预测分析器。
 *
 * 基于历史数据的线性斜率，预测：
 * - 本月预计总消耗 (kWh)
 * - 本月预计总账单 (¥)
 *
 * 算法：取最近 N 条记录的日均斜率，外推到月底。
 */
@Singleton
class PredictiveAnalyzer @Inject constructor() {

    /**
     * 月度预测。
     *
     * @param records 所有电表记录（按时间升序）
     * @return 预测结果
     */
    fun predictMonth(records: List<MeterRecord>): MonthPrediction? {
        val electricRecords = records
            .filter { it.isElectricRecorded && it.electricTotal != null }
            .sortedBy { it.timestamp }

        if (electricRecords.size < 2) return null

        // 取最近 5 条记录计算日均增速
        val recent = electricRecords.takeLast(5.coerceAtMost(electricRecords.size))
        if (recent.size < 2) return null

        val first = recent.first()
        val last = recent.last()
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(first.timestamp, last.timestamp).coerceAtLeast(1)
        val totalConsumption = last.electricTotal!! - first.electricTotal!!
        val dailyRate = totalConsumption / totalDays

        if (dailyRate <= 0) return null

        // 计算本月剩余天数
        val now = LocalDateTime.now()
        val monthEnd = YearMonth.from(now).atEndOfMonth().atTime(23, 59)
        val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(now, monthEnd).coerceAtLeast(0)
        val daysElapsed = now.dayOfMonth.coerceAtLeast(1)

        // 本月初到现在的消耗
        val monthStart = YearMonth.from(now).atDay(1).atStartOfDay()
        val monthRecords = electricRecords.filter { !it.timestamp.isBefore(monthStart) }
        val monthConsumptionSoFar = if (monthRecords.size >= 2) {
            val mFirst = monthRecords.minByOrNull { it.timestamp }!!
            val mLast = monthRecords.maxByOrNull { it.timestamp }!!
            mLast.electricTotal!! - mFirst.electricTotal!!
        } else {
            dailyRate * daysElapsed
        }

        // 预测：本月已消耗 + 剩余天数 × 日均
        val predictedRemaining = dailyRate * daysRemaining
        val predictedTotal = monthConsumptionSoFar + predictedRemaining

        return MonthPrediction(
            dailyRateKwh = dailyRate,
            daysElapsed = daysElapsed,
            daysRemaining = daysRemaining,
            consumedSoFarKwh = monthConsumptionSoFar,
            predictedRemainingKwh = predictedRemaining,
            predictedTotalKwh = predictedTotal
        )
    }
}

data class MonthPrediction(
    val dailyRateKwh: Double,         // 日均消耗 (kWh)
    val daysElapsed: Int,             // 本月已过天数
    val daysRemaining: Long,          // 剩余天数
    val consumedSoFarKwh: Double,     // 本月已消耗
    val predictedRemainingKwh: Double, // 预计剩余消耗
    val predictedTotalKwh: Double     // 预计全月总消耗
)
