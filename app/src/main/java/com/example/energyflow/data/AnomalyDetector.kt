package com.example.energyflow.data

import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Checks a candidate record against readings that existed before its timestamp. */
@Singleton
class AnomalyDetector @Inject constructor(
    private val dao: MeterRecordDao
) {
    suspend fun checkElectricMonotonic(newTotal: Double, timestamp: LocalDateTime): String? {
        val previous = electricHistoryBefore(timestamp).lastOrNull() ?: return null
        val oldTotal = previous.electricTotal ?: return null
        return if (newTotal < oldTotal) {
            "当前读数 ${format(newTotal)} 低于历史记录 ${format(oldTotal)}，请检查是否输入错误"
        } else {
            null
        }
    }

    suspend fun checkWaterMonotonic(newTotal: Double, timestamp: LocalDateTime): String? {
        val previous = dao.getWaterRecords().first()
            .asSequence()
            .filter { it.timestamp < timestamp && it.waterTotal != null }
            .maxByOrNull { it.timestamp }
            ?: return null
        val oldTotal = previous.waterTotal ?: return null
        return if (newTotal < oldTotal) {
            "当前水表读数 ${format(newTotal)} 低于历史记录 ${format(oldTotal)}，请检查是否输入错误"
        } else {
            null
        }
    }

    /**
     * Uses the candidate reading (not the previously persisted reading) to compare
     * its normalized daily usage to the last available historical intervals.
     */
    suspend fun checkElectricSpike(newTotal: Double, timestamp: LocalDateTime): String? {
        val previous = electricHistoryBefore(timestamp)
        val latest = previous.lastOrNull() ?: return null
        val latestTotal = latest.electricTotal ?: return null
        val candidateDaily = dailyUsage(latest, timestamp, newTotal) ?: return null

        val historicalDailies = previous
            .takeLast(4)
            .windowed(2)
            .mapNotNull { (before, after) ->
                dailyUsage(before, after.timestamp, after.electricTotal ?: return@mapNotNull null)
            }
            .filter { it > 0.0 }
        if (historicalDailies.size < 2 || candidateDaily <= 0.0 || newTotal < latestTotal) return null

        val baseline = historicalDailies.average()
        if (baseline <= 0.0) return null
        val ratio = candidateDaily / baseline
        return if (ratio >= SPIKE_RATIO) {
            "单日耗电量 ${format(candidateDaily)} 度/天，是此前日均 ${format(baseline)} 度/天的 ${(ratio * 100).toInt()}%，较往常大幅偏高"
        } else {
            null
        }
    }

    suspend fun checkParseResult(result: ParseResult.Success): String? = when {
        result.isElectric && result.electricTotal != null ->
            checkElectricMonotonic(result.electricTotal, result.timestamp)
        result.isWater && result.waterTotal != null ->
            checkWaterMonotonic(result.waterTotal, result.timestamp)
        else -> null
    }

    suspend fun checkParseResultForSpike(result: ParseResult.Success): String? {
        val total = result.electricTotal ?: return null
        return if (result.isElectric) checkElectricSpike(total, result.timestamp) else null
    }

    private suspend fun electricHistoryBefore(timestamp: LocalDateTime): List<MeterRecord> =
        dao.getElectricRecords().first()
            .filter { it.timestamp < timestamp && it.electricTotal != null }
            .sortedBy { it.timestamp }

    private fun dailyUsage(previous: MeterRecord, currentTime: LocalDateTime, currentTotal: Double): Double? {
        val previousTotal = previous.electricTotal ?: return null
        val days = Duration.between(previous.timestamp, currentTime).toMinutes() / MINUTES_PER_DAY
        if (days <= 0.0) return null
        return (currentTotal - previousTotal) / days
    }

    private fun format(value: Double): String = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        "%.1f".format(value)
    }

    private companion object {
        const val SPIKE_RATIO = 5.0
        const val MINUTES_PER_DAY = 24.0 * 60.0
    }
}

sealed class AnomalyWarning {
    data class ReadingLowerThanPrevious(val message: String) : AnomalyWarning()
    data class SpikeDetected(val detail: String) : AnomalyWarning()
}
