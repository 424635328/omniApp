package com.example.energyflow.data

import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 预测分析器。
 *
 * 基于本月已产生的实际记录计算日均耗量，外推到月底。
 * 数据不足时回退到最近 N 条记录的斜率。
 */
@Singleton
class PredictiveAnalyzer @Inject constructor() {

    fun predictMonth(
        records: List<MeterRecord>,
        now: LocalDateTime = LocalDateTime.now()
    ): MonthPrediction? {
        val electricRecords = records
            .filter { it.isElectricRecorded && it.electricTotal != null }
            .sortedBy { it.timestamp }

        if (electricRecords.size < 2) return null

        // 剔除异常递减的记录（累计电表读数不应下降）
        val cleanRecords = mutableListOf(electricRecords.first())
        for (i in 1 until electricRecords.size) {
            val prev = cleanRecords.last()
            val curr = electricRecords[i]
            val currTotal = curr.electricTotal ?: continue
            val prevTotal = prev.electricTotal ?: continue
            if (currTotal >= prevTotal) {
                cleanRecords.add(curr)
            }
        }
        if (cleanRecords.size < 2) return null

        val now = now
        val monthStart = YearMonth.from(now).atDay(1).atStartOfDay()
        val monthEnd = YearMonth.from(now).atEndOfMonth().atTime(23, 59)
        val daysElapsed = now.dayOfMonth.coerceAtLeast(1)
        val daysRemaining = ChronoUnit.DAYS.between(now, monthEnd).coerceAtLeast(0)

        // ── 日均斜率：优先用本月记录 ──
        val monthRecords = cleanRecords.filter { !it.timestamp.isBefore(monthStart) }

        val dailyRate: Double
        val monthConsumptionSoFar: Double

        if (monthRecords.size >= 2) {
            // 本月有 ≥2 条 → 用本月实际首尾差值 / 实际天数
            val mFirst = monthRecords.first()
            val mLast = monthRecords.last()
            val elapsedMin = ChronoUnit.MINUTES.between(mFirst.timestamp, mLast.timestamp)
            val actualDays = (elapsedMin / (24.0 * 60.0)).coerceAtLeast(1.0 / 24.0)
            val consumed = (mLast.electricTotal ?: 0.0) - (mFirst.electricTotal ?: 0.0)
            dailyRate = consumed / actualDays
            monthConsumptionSoFar = consumed
        } else {
            // 本月数据不足 → 用最近 N 条记录的斜率推算
            val windowSize = 5.coerceAtMost(cleanRecords.size)
            val recent = cleanRecords.takeLast(windowSize)
            val first = recent.first()
            val last = recent.last()
            val elapsedMin = ChronoUnit.MINUTES.between(first.timestamp, last.timestamp)
            val totalDays = (elapsedMin / (24.0 * 60.0)).coerceAtLeast(1.0 / 24.0)
            val totalConsumption = (last.electricTotal ?: return null) - (first.electricTotal ?: return null)
            dailyRate = totalConsumption / totalDays
            monthConsumptionSoFar = if (monthRecords.size == 1) {
                // 仅有 1 条本月记录 → 用日均推算
                dailyRate * daysElapsed
            } else {
                // 无本月记录 → 同上
                dailyRate * daysElapsed
            }
        }

        if (dailyRate <= 0) return null

        // 预测：已消耗 + 剩余天数 × 日均
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
    val dailyRateKwh: Double,
    val daysElapsed: Int,
    val daysRemaining: Long,
    val consumedSoFarKwh: Double,
    val predictedRemainingKwh: Double,
    val predictedTotalKwh: Double
)

/**
 * 预测快照——在月初/首次预测时保存，用于后续对比实际 vs 预测。
 * savedYearMonth: 快照对应的年月 "2026-07"
 */
@kotlinx.serialization.Serializable
data class PredictionSnapshot(
    val savedYearMonth: String,
    val savedDayOfMonth: Int,
    val predictedTotalKwh: Double,
    val dailyRateKwh: Double,
    val consumedSoFarAtSave: Double
)
