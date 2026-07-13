package com.example.energyflow.data

import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 异常检测器。
 *
 * 功能：
 * 1. 单调递增校验 — 新读数是手误少输了一位数字时拦截
 * 2. 耗量突增检测 — 单日耗电量突增 500%+ 时警告
 */
@Singleton
class AnomalyDetector @Inject constructor(
    private val dao: MeterRecordDao
) {
    /**
     * 保存前的全面校验：检查电表读数是否低于上次记录。
     */
    suspend fun checkElectricMonotonic(newTotal: Double): String? {
        val prev = dao.getLatestElectricRecord()
        if (prev?.electricTotal != null && newTotal < prev.electricTotal) {
            return "电表读数 ${format(newTotal)} 小于上次记录 ${format(prev.electricTotal)}，请检查是否输入错误"
        }
        return null
    }

    /**
     * 保存前的全面校验：检查水表读数是否低于上次记录。
     */
    suspend fun checkWaterMonotonic(newTotal: Double): String? {
        val prev = dao.getLatestWaterRecord()
        if (prev?.waterTotal != null && newTotal < prev.waterTotal) {
            return "水表读数 ${format(newTotal)} 小于上次记录 ${format(prev.waterTotal)}，请检查是否输入错误"
        }
        return null
    }

    /**
     * 耗量突增检测。
     */
    suspend fun checkElectricSpike(newTotal: Double): String? {
        val records = dao.getElectricRecords().first()
            .filter { it.electricTotal != null }
            .sortedBy { it.timestamp }

        if (records.size < 3) return null // 至少需要 3 条记录才有历史对比

        val latest = records.last()
        val previous = records.dropLast(1).last()

        val daysNew = ChronoUnit.DAYS.between(previous.timestamp, latest.timestamp).coerceAtLeast(1)
        val consumptionNew = (latest.electricTotal!! - previous.electricTotal!!)
        val dailyNew = consumptionNew / daysNew

        val olderPairs = records.dropLast(2)
        if (olderPairs.size < 1) return null
        val olderDailies = olderPairs.windowed(2).map { (a, b) ->
            val d = ChronoUnit.DAYS.between(a.timestamp, b.timestamp).coerceAtLeast(1)
            ((b.electricTotal!! - a.electricTotal!!)) / d
        }
        val avgPrevious = olderDailies.average()

        if (avgPrevious <= 0 || dailyNew <= 0) return null
        val ratio = dailyNew / avgPrevious
        if (ratio >= 5.0) {
            return "单日耗电量 ${format(dailyNew)} 度/天，是此前日均 ${format(avgPrevious)} 度/天的 ${(ratio * 100).toInt()}%，较往常大幅偏高"
        }
        return null
    }

    /**
     * 解析结果（批量导入场景）的单调递增校验。
     */
    suspend fun checkParseResult(result: ParseResult.Success): String? {
        if (result.isElectric && result.electricTotal != null) {
            val prev = dao.getLatestElectricRecord()
            if (prev?.electricTotal != null && result.electricTotal < prev.electricTotal) {
                return "电表读数 ${format(result.electricTotal)} 小于上次记录 ${format(prev.electricTotal)}，请确认是否正确"
            }
        }
        if (result.isWater && result.waterTotal != null) {
            val prev = dao.getLatestWaterRecord()
            if (prev?.waterTotal != null && result.waterTotal < prev.waterTotal) {
                return "水表读数 ${format(result.waterTotal)} 小于上次记录 ${format(prev.waterTotal)}，请确认是否正确"
            }
        }
        return null
    }

    /**
     * 批量导入场景的耗量突增检测。
     */
    suspend fun checkParseResultForSpike(result: ParseResult.Success): String? {
        if (!result.isElectric || result.electricTotal == null) return null
        return checkElectricSpike(result.electricTotal)
    }

    private fun format(v: Double): String {
        return if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
    }
}

sealed class AnomalyWarning {
    data class ReadingLowerThanPrevious(val message: String) : AnomalyWarning()

    data class SpikeDetected(val detail: String) : AnomalyWarning()
}
