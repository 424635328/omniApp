package com.example.energyflow.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 本地启发式洞察生成器 — 不依赖 API，基于历史数据和天气模式生成一句话洞察。
 */
object InsightGenerator {

    data class Insight(
        val emoji: String,
        val title: String,
        val detail: String,
        val level: Level = Level.INFO
    ) {
        enum class Level { INFO, WARNING, CRITICAL }
    }

    /**
     * 从记录和天气数据中生成洞察，返回第一个匹配的洞察。
     */
    fun generate(
        records: List<MeterRecord>,
        weather: List<DailyWeather> = emptyList(),
        rules: BillingRules = BillingRules()
    ): Insight? {
        if (records.size < 4) return null

        val sorted = records.filter { it.isElectricRecorded && it.electricTotal != null }
            .sortedBy { it.timestamp }

        if (sorted.size < 4) return null

        return listOfNotNull(
            checkTierWarning(sorted, rules),
            checkHighTempSpike(sorted, weather),
            checkValleyLow(sorted),
            checkWeekendAnomaly(sorted)
        ).firstOrNull()
    }

    /**
     * 阶梯预警：本月用电已超二档阈值 80%
     */
    private fun checkTierWarning(records: List<MeterRecord>, rules: BillingRules): Insight? {
        val now = YearMonth.now()
        val thisMonth = records.filter { YearMonth.from(it.timestamp) == now }
        if (thisMonth.size < 2) return null

        val kwh = ((thisMonth.last().electricTotal ?: 0.0) - (thisMonth.first().electricTotal ?: 0.0)).coerceAtLeast(0.0)
        val threshold80 = rules.electricTier2Limit * 0.8

        return if (kwh > threshold80) {
            val remaining = rules.electricTier2Limit - kwh
            Insight(
                emoji = "⚠️",
                title = "阶梯预警",
                detail = "本月用电 ${"%.0f".format(kwh)} 度，距二档加价仅剩 ${"%.0f".format(remaining)} 度",
                level = if (kwh > rules.electricTier2Limit) Insight.Level.CRITICAL else Insight.Level.WARNING
            )
        } else null
    }

    /**
     * 高温能耗飙升：最近3天高温 + 用电量显著高于前期
     */
    private fun checkHighTempSpike(records: List<MeterRecord>, weather: List<DailyWeather>): Insight? {
        if (weather.isEmpty()) return null

        val today = LocalDate.now()
        val recent3 = weather.filter { !it.date.isBefore(today.minusDays(2)) }
        val hotDays = recent3.filter { it.tempMax > 35 }
        if (hotDays.isEmpty()) return null

        // 最近 3 天日均 vs 之前 7 天日均
        val recentRecords = records.filter {
            !it.timestamp.toLocalDate().isBefore(today.minusDays(2))
        }.sortedBy { it.timestamp }

        val earlierRecords = records.filter {
            it.timestamp.toLocalDate().isBefore(today.minusDays(2)) &&
            !it.timestamp.toLocalDate().isBefore(today.minusDays(10))
        }.sortedBy { it.timestamp }

        if (recentRecords.size < 2 || earlierRecords.size < 2) return null

        val recentKwh = (recentRecords.last().electricTotal ?: 0.0) - (recentRecords.first().electricTotal ?: 0.0)
        val recentDays = ChronoUnit.DAYS.between(
            recentRecords.first().timestamp.toLocalDate(),
            recentRecords.last().timestamp.toLocalDate()
        ).coerceAtLeast(1)
        val recentDaily = recentKwh / recentDays

        val earlierKwh = (earlierRecords.last().electricTotal ?: 0.0) - (earlierRecords.first().electricTotal ?: 0.0)
        val earlierDays = ChronoUnit.DAYS.between(
            earlierRecords.first().timestamp.toLocalDate(),
            earlierRecords.last().timestamp.toLocalDate()
        ).coerceAtLeast(1)
        val earlierDaily = earlierKwh / earlierDays

        return if (recentDaily > earlierDaily * 1.5) {
            val avgTemp = hotDays.map { it.tempMax }.average()
            Insight(
                emoji = "🌡️",
                title = "高温影响",
                detail = "近3天最高温 ${"%.0f".format(avgTemp)}°C，日均用电从 ${"%.1f".format(earlierDaily)} 度飙升至 ${"%.1f".format(recentDaily)} 度",
                level = Insight.Level.WARNING
            )
        } else null
    }

    /**
     * 谷电使用偏低：最近7天谷电占比低于20%
     */
    private fun checkValleyLow(records: List<MeterRecord>): Insight? {
        val today = LocalDate.now()
        val recent = records.filter {
            !it.timestamp.toLocalDate().isBefore(today.minusDays(6)) &&
            it.electricPeak != null && it.electricValley != null
        }.sortedBy { it.timestamp }

        if (recent.size < 2) return null

        val totalPeak = (recent.last().electricPeak ?: 0.0) - (recent.first().electricPeak ?: 0.0)
        val totalValley = (recent.last().electricValley ?: 0.0) - (recent.first().electricValley ?: 0.0)
        val total = totalPeak + totalValley

        if (total <= 0) return null

        val valleyRatio = totalValley / total
        return if (valleyRatio < 0.20) {
            Insight(
                emoji = "⚡",
                title = "谷电偏低",
                detail = "近7天谷电占比仅 ${(valleyRatio * 100).toInt()}%，建议将洗衣机/热水器调至谷时段运行",
                level = Insight.Level.INFO
            )
        } else null
    }

    /**
     * 周末用电异常：周末用电量显著高于工作日
     */
    private fun checkWeekendAnomaly(records: List<MeterRecord>): Insight? {
        val today = LocalDate.now()
        val recent = records.filter {
            !it.timestamp.toLocalDate().isBefore(today.minusDays(13))
        }.sortedBy { it.timestamp }

        if (recent.size < 6) return null

        val weekdayKwh = mutableListOf<Double>()
        val weekendKwh = mutableListOf<Double>()

        for (i in 0 until recent.size - 1) {
            val curr = recent[i]
            val next = recent[i + 1]
            val days = ChronoUnit.DAYS.between(
                curr.timestamp.toLocalDate(), next.timestamp.toLocalDate()
            )
            if (days <= 0 || days > 3) continue

            val currTotal = curr.electricTotal ?: continue
            val nextTotal = next.electricTotal ?: continue
            val kwh = nextTotal - currTotal
            if (kwh < 0) continue

            val isWeekend = curr.timestamp.dayOfWeek == DayOfWeek.SATURDAY ||
                           curr.timestamp.dayOfWeek == DayOfWeek.SUNDAY

            if (isWeekend) weekendKwh.add(kwh / days)
            else weekdayKwh.add(kwh / days)
        }

        if (weekdayKwh.isEmpty() || weekendKwh.isEmpty()) return null

        val weekdayAvg = weekdayKwh.average()
        val weekendAvg = weekendKwh.average()

        return if (weekendAvg > weekdayAvg * 1.3) {
            Insight(
                emoji = "📅",
                title = "周末偏高",
                detail = "周末日均 ${"%.1f".format(weekendAvg)} 度，高于工作日 ${"%.1f".format(weekdayAvg)} 度 ${(weekendAvg / weekdayAvg * 100 - 100).toInt()}%",
                level = Insight.Level.INFO
            )
        } else null
    }
}
