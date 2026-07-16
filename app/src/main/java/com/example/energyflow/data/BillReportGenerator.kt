package com.example.energyflow.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 账单报告生成器 — 生成结构化文本报告，可直接分享到微信/邮件/系统分享面板。
 *
 * 接收用户自定义的 [BillingRules] 确保阶梯档位、价格与设置页一致。
 */
object BillReportGenerator {

    data class ReportData(
        val yearMonth: String,
        val periodStart: LocalDate,
        val periodEnd: LocalDate,
        val periodDays: Long,
        val electricKwh: Double,
        val electricCost: Double,
        val peakKwh: Double,
        val valleyKwh: Double,
        val waterTons: Double,
        val waterCost: Double,
        val waterTierInfo: String,
        val gasM3: Double,
        val gasCost: Double,
        val totalCost: Double,
        val dailyAvgKwh: Double,
        val dailyAvgCost: Double,
        val peakPrice: Double,
        val valleyPrice: Double,
        val flatPrice: Double,
        val tierLevel: String,
        val recordCount: Int,
        val topNotes: List<String>
    )

    fun generateTextReport(data: ReportData): String = buildString {
        val sep = "══════════════════════════════════════"
        val sepThin = "─".repeat(38)

        fun costPct(cost: Double): String =
            if (data.totalCost > 0) "(${"%.0f".format(cost / data.totalCost * 100)}%)" else ""

        // ══════════════════════════════════════════
        //  Header
        // ══════════════════════════════════════════
        appendLine(sep)
        appendLine("   ⚡ 能耗手记 · 月度账单")
        appendLine("   ${data.periodStart.monthValue}月${data.periodStart.dayOfMonth}日 — ${data.periodEnd.monthValue}月${data.periodEnd.dayOfMonth}日  (${data.periodDays}天)")
        appendLine(sep)
        appendLine()

        // ── 费用总览 ──
        appendLine("  💰 费用总览")
        appendLine(sepThin)
        appendLine("    电费      ${"%-9s".format("¥${"%.2f".format(data.electricCost)}")}  ${costPct(data.electricCost)}")
        appendLine("    水费      ${"%-9s".format("¥${"%.2f".format(data.waterCost)}")}  ${costPct(data.waterCost)}")
        if (data.gasM3 > 0) {
            appendLine("    燃气费    ${"%-9s".format("¥${"%.2f".format(data.gasCost)}")}  ${costPct(data.gasCost)}")
        }
        appendLine("    ${"合计".padEnd(6)} ${"¥${"%.2f".format(data.totalCost)}".padStart(9)}")
        appendLine()

        // ── 用电 ──
        appendLine("  📊 用电明细")
        appendLine(sepThin)
        appendLine("    总用电    ${"%.1f".format(data.electricKwh)} kWh".padEnd(26) + "日均 ${"%.1f".format(data.dailyAvgKwh)} kWh")
        if (data.peakKwh > 0 || data.valleyKwh > 0) {
            val totalPv = data.peakKwh + data.valleyKwh
            val peakPct = if (totalPv > 0) data.peakKwh / totalPv * 100 else 0.0
            val valleyPct = if (totalPv > 0) data.valleyKwh / totalPv * 100 else 0.0
            val barLen = 16
            val peakBars = (peakPct / 100 * barLen).toInt().coerceIn(0, barLen)
            val valleyBars = (valleyPct / 100 * barLen).toInt().coerceIn(0, barLen)
            appendLine("    峰电 ${"%.1f".format(data.peakKwh)} ${"█".repeat(peakBars)}${"░".repeat((barLen - peakBars).coerceAtLeast(0))} ${"%.0f%%".format(peakPct)}")
            appendLine("    谷电 ${"%.1f".format(data.valleyKwh)} ${"█".repeat(valleyBars)}${"░".repeat((barLen - valleyBars).coerceAtLeast(0))} ${"%.0f%%".format(valleyPct)}")
        } else {
            appendLine("    单价     ¥${"%.4f".format(data.flatPrice)}/kWh")
        }
        appendLine("    阶梯     ${data.tierLevel}")
        appendLine()

        // ── 用水 ──
        appendLine("  💧 用水明细")
        appendLine(sepThin)
        appendLine("    总用水    ${"%.1f".format(data.waterTons)} 吨")
        if (data.waterTierInfo.isNotBlank()) {
            appendLine("    阶梯     ${data.waterTierInfo}")
        }
        appendLine()

        // ── 燃气 ──
        if (data.gasM3 > 0) {
            appendLine("  🔥 燃气明细")
            appendLine(sepThin)
            appendLine("    总用气    ${"%.1f".format(data.gasM3)} m³")
            appendLine()
        }

        // ── 统计 ──
        appendLine("  📈 统计")
        appendLine(sepThin)
        appendLine("    记录数    ${data.recordCount} 条")
        appendLine("    日均费用  ¥${"%.2f".format(data.dailyAvgCost)}")
        if (data.topNotes.isNotEmpty()) {
            appendLine("    标签      ${data.topNotes.joinToString(" · ")}")
        }
        appendLine()
        appendLine(sep)
        appendLine("    Energy Flow · 你的能耗小助手")
    }

    suspend fun buildReportData(
        electricRecords: List<MeterRecord>,
        waterRecords: List<MeterRecord>,
        gasRecords: List<MeterRecord>,
        notesRecords: List<MeterRecord>,
        costEngine: CostEngine,
        rules: BillingRules? = null
    ): ReportData? {
        val now = YearMonth.now()
        val elecMonth = electricRecords.filter {
            it.electricTotal != null && YearMonth.from(it.timestamp) == now
        }.sortedBy { it.timestamp }

        if (elecMonth.size < 2) return null

        val first = elecMonth.first()
        val last = elecMonth.last()
        val kwh = (last.electricTotal!! - first.electricTotal!!).coerceAtLeast(0.0)
        val days = ChronoUnit.DAYS.between(first.timestamp.toLocalDate(), last.timestamp.toLocalDate()).coerceAtLeast(1)

        val peakKwh = if (last.electricPeak != null && first.electricPeak != null)
            (last.electricPeak!! - first.electricPeak!!).coerceAtLeast(0.0).coerceAtMost(kwh) else 0.0
        val valleyKwh = if (last.electricValley != null && first.electricValley != null)
            (last.electricValley!! - first.electricValley!!).coerceAtLeast(0.0).coerceAtMost(kwh - peakKwh) else 0.0

        val waterMonth = waterRecords.filter {
            it.waterTotal != null && YearMonth.from(it.timestamp) == now
        }.sortedBy { it.timestamp }
        val waterTons = if (waterMonth.size >= 2)
            (waterMonth.last().waterTotal!! - waterMonth.first().waterTotal!!).coerceAtLeast(0.0) else 0.0

        val gasMonth = gasRecords.filter {
            it.isGasRecorded && it.gasTotal != null && YearMonth.from(it.timestamp) == now
        }.sortedBy { it.timestamp }
        val gasM3 = if (gasMonth.size >= 2)
            (gasMonth.last().gasTotal!! - gasMonth.first().gasTotal!!).coerceAtLeast(0.0) else 0.0

        val bill = costEngine.calculateBill(kwh, peakKwh, valleyKwh, waterTons)

        // 当月标签统计（只取当月备注）
        val tagCounts = mutableMapOf<String, Int>()
        notesRecords.filter { YearMonth.from(it.timestamp) == now }.forEach { record ->
            record.note?.split(Regex("\\s+"))?.filter { it.isNotBlank() }?.forEach {
                tagCounts[it] = (tagCounts[it] ?: 0) + 1
            }
        }
        val topNotes = tagCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        // 使用用户自定义规则判断阶梯档位，不再硬编码默认值
        val activeRules = rules ?: BillingRules()
        val tierLevel = when {
            kwh > activeRules.electricTier2Limit -> "三档"
            kwh > activeRules.electricTier1Limit -> "二档"
            else -> "一档"
        }

        // 水价阶梯信息
        val waterTierInfo = buildWaterTierInfo(waterTons, activeRules)

        // 燃气费用（暂用固定单价 2.8 元/m³）
        val gasCost = gasM3 * 2.8

        return ReportData(
            yearMonth = "${now.year}-${now.monthValue}",
            periodStart = first.timestamp.toLocalDate(),
            periodEnd = last.timestamp.toLocalDate(),
            periodDays = days,
            electricKwh = kwh,
            electricCost = bill.electricTotalCost,
            peakKwh = peakKwh,
            valleyKwh = valleyKwh,
            waterTons = waterTons,
            waterCost = bill.waterTotalCost,
            waterTierInfo = waterTierInfo,
            gasM3 = gasM3,
            gasCost = gasCost,
            totalCost = bill.totalCost + gasCost,
            dailyAvgKwh = kwh / days,
            dailyAvgCost = (bill.totalCost + gasCost) / days,
            peakPrice = bill.peakPrice,
            valleyPrice = bill.valleyPrice,
            flatPrice = bill.flatPrice,
            tierLevel = tierLevel,
            recordCount = elecMonth.size,
            topNotes = topNotes
        )
    }

    /**
     * 生成水价阶梯说明，与 CostEngine.tieredWaterCost 逻辑一致。
     */
    private fun buildWaterTierInfo(tons: Double, rules: BillingRules): String {
        if (tons <= 0.0) return ""
        val tier2Start = rules.waterTier1Limit
        val tier3Start = rules.waterTier2Limit
        return when {
            tons <= tier2Start -> "一档 (¥${"%.2f".format(rules.waterTier1Price)}/吨)"
            tons <= tier3Start -> "二档 (¥${"%.2f".format(rules.waterTier2Price)}/吨)"
            else -> "三档 (¥${"%.2f".format(rules.waterTier3Price)}/吨)"
        }
    }
}
