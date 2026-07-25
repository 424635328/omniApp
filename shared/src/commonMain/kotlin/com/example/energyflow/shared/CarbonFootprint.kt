package com.example.energyflow.shared

import kotlin.math.round

/**
 * 碳足迹计算核心（纯逻辑，跨平台可用）。
 *
 * @property electricFactor 每 kWh 排放 kg CO2（中国电网平均 ~0.583）
 * @property gasFactor 每 m³ 天然气排放 kg CO2（~2.02）
 * @property treeKgPerYear 一棵树每年吸收 CO2 的 kg 数（~20）
 */
data class CarbonConfig(
    val electricFactor: Double = 0.583,
    val gasFactor: Double = 2.02,
    val treeKgPerYear: Double = 20.0
)

data class CarbonResult(
    val totalKgCO2: Double,
    val electricKgCO2: Double,
    val gasKgCO2: Double,
    val treeDays: Int
)

/**
 * 绿色徽章 — 表示用户在某方面取得的环保成就。
 */
enum class GreenBadge {
    FIRST_STEP,
    STREAK_3,
    ENERGY_SAVER,
    PEAK_SHIFTER,
    GREEN_MONTH,
    CARBON_MASTER
}

/**
 * 月统计条目，用于徽章计算。
 *
 * @property yearMonth "yyyy-MM" 格式
 * @property kwhDelta 该月用电量（度）
 * @property waterDelta 该月用水量（吨）
 * @property gasM3Delta 该月用气量（m³）
 * @property peakKwh 该月峰电量（可选，用于 PEAK_SHIFTER 徽章）
 * @property valleyKwh 该月谷电量（可选，用于 PEAK_SHIFTER 徽章）
 */
data class YearMonthStat(
    val yearMonth: String,
    val kwhDelta: Double,
    val waterDelta: Double,
    val gasM3Delta: Double,
    val peakKwh: Double = 0.0,
    val valleyKwh: Double = 0.0
)

object CarbonCalculator {

    /**
     * 计算碳排放。
     *
     * @param kwh 用电量（度）
     * @param gasM3 用气量（m³）
     * @param config 排放因子配置
     */
    fun calculate(kwh: Double, gasM3: Double, config: CarbonConfig = CarbonConfig()): CarbonResult {
        val electricKgCO2 = kwh * config.electricFactor
        val gasKgCO2 = gasM3 * config.gasFactor
        val totalKgCO2 = electricKgCO2 + gasKgCO2
        val treeDays = if (config.treeKgPerYear > 0) {
            round(totalKgCO2 / (config.treeKgPerYear / 365.0)).toInt()
        } else 0
        return CarbonResult(totalKgCO2, electricKgCO2, gasKgCO2, treeDays)
    }

    /**
     * 根据月统计数据列表计算已获得的绿色徽章。
     *
     * @param records 按月统计的记录，应按时间升序排列（最早的在前）。
     */
    fun badgesFromRecords(records: List<YearMonthStat>): List<GreenBadge> {
        val earned = mutableSetOf<GreenBadge>()

        if (records.isEmpty()) return emptyList()

        // FIRST_STEP — 有任何数据即获得
        earned.add(GreenBadge.FIRST_STEP)

        val sorted = records.sortedBy { it.yearMonth }

        // STREAK_3 — 3+ 个连续月份
        if (sorted.size >= 3) {
            var maxStreak = 1
            var currentStreak = 1
            for (i in 1 until sorted.size) {
                if (monthsApart(sorted[i - 1].yearMonth, sorted[i].yearMonth) == 1) {
                    currentStreak++
                    maxStreak = maxOf(maxStreak, currentStreak)
                } else {
                    currentStreak = 1
                }
            }
            if (maxStreak >= 3) {
                earned.add(GreenBadge.STREAK_3)
            }
        }

        // ENERGY_SAVER — 最近一个完整月 kWh < 前一个月
        if (sorted.size >= 2) {
            val latest = sorted.last()
            val prev = sorted[sorted.size - 2]
            if (latest.kwhDelta < prev.kwhDelta) {
                earned.add(GreenBadge.ENERGY_SAVER)
            }
        }

        // PEAK_SHIFTER — 谷电占比 > 30%（基于最近一个有峰谷数据的月份）
        for (i in sorted.indices.reversed()) {
            val stat = sorted[i]
            val totalPv = stat.peakKwh + stat.valleyKwh
            if (totalPv > 0) {
                val valleyRatio = stat.valleyKwh / totalPv
                if (valleyRatio > 0.3) {
                    earned.add(GreenBadge.PEAK_SHIFTER)
                }
                break
            }
        }

        // GREEN_MONTH — 最近月份的碳排放 < 平均值的 80%
        if (sorted.size >= 2) {
            val avgCarbon = sorted.sumOf {
                it.kwhDelta * 0.583 + it.gasM3Delta * 2.02
            } / sorted.size
            val latestCarbon =
                sorted.last().kwhDelta * 0.583 + sorted.last().gasM3Delta * 2.02
            if (avgCarbon > 0 && latestCarbon < avgCarbon * 0.8) {
                earned.add(GreenBadge.GREEN_MONTH)
            }
        }

        // CARBON_MASTER — 获得除自身外全部 5 个徽章
        if (earned.size >= 5) {
            earned.add(GreenBadge.CARBON_MASTER)
        }

        return earned.toList().sortedBy { it.ordinal }
    }

    /**
     * 计算两个 "yyyy-MM" 格式月份之间的月数差。
     */
    private fun monthsApart(a: String, b: String): Int {
        val partsA = a.split("-")
        val partsB = b.split("-")
        if (partsA.size != 2 || partsB.size != 2) return 0
        val ay = partsA[0].toIntOrNull() ?: return 0
        val am = partsA[1].toIntOrNull() ?: return 0
        val by = partsB[0].toIntOrNull() ?: return 0
        val bm = partsB[1].toIntOrNull() ?: return 0
        return (by * 12 + bm) - (ay * 12 + am)
    }
}
