package com.example.energyflow.shared

import kotlinx.datetime.LocalDateTime

/**
 * 异常检测引擎（纯逻辑，跨平台可用）。
 */
object AnomalyDetectorShared {

    data class Reading(
        val timestamp: LocalDateTime,
        val electricTotal: Double?,
        val waterTotal: Double?,
        val gasTotal: Double?
    )

    sealed class Warning {
        data class ReadingLowerThanPrevious(val message: String) : Warning()
        data class SpikeDetected(val detail: String) : Warning()
    }

    /**
     * 检测记录中的异常。
     */
    fun detect(
        newRecord: Reading,
        latestRecord: Reading?,
        records: List<Reading>
    ): List<Warning> {
        val warnings = mutableListOf<Warning>()

        // 读数递减检测
        latestRecord?.let { prev ->
            newRecord.electricTotal?.let { newVal ->
                prev.electricTotal?.let { prevVal ->
                    if (newVal < prevVal * 0.5) {
                        warnings.add(
                            Warning.ReadingLowerThanPrevious(
                                "电表读数（$newVal）比上次（$prevVal）低，请确认或标记为换表"
                            )
                        )
                    }
                }
            }
        }

        // 尖峰检测
        if (records.size >= 3) {
            val recent = records.takeLast(3)
            val deltas = mutableListOf<Double>()
            for (i in 1 until recent.size) {
                val curr = recent[i].electricTotal ?: continue
                val prev = recent[i - 1].electricTotal ?: continue
                deltas.add(curr - prev)
            }
            if (deltas.size >= 2) {
                val lastDelta = deltas.last()
                val avgDelta = deltas.dropLast(1).average()
                if (avgDelta > 0 && lastDelta > avgDelta * 5 && lastDelta > 50) {
                    warnings.add(
                        Warning.SpikeDetected(
                            "电表突增 ${"%.1f".format(lastDelta)} 度" +
                            "（此前平均 ${"%.1f".format(avgDelta)} 度）"
                        )
                    )
                }
            }
        }

        return warnings
    }
}
