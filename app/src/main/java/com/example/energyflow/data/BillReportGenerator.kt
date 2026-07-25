package com.example.energyflow.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 账单报告生成器 — 生成结构化文本/HTML 报告，可直接分享到微信/邮件/系统分享面板。
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

    data class ComparisonData(
        val kwhChange: Double,
        val costChange: Double,
        val waterChange: Double,
        val totalCostChange: Double,
        val prevKwh: Double,
        val prevCost: Double,
        val prevWater: Double,
        val prevTotalCost: Double,
        val prevDays: Long
    )

    // ════════════════════════════════════════════
    //  纯文本报告
    // ════════════════════════════════════════════

    fun generateTextReport(data: ReportData, comparison: ComparisonData? = null): String = buildString {
        val sep = "══════════════════════════════════════"
        val sepThin = "─".repeat(38)

        fun costPct(cost: Double): String =
            if (data.totalCost > 0) "(${"%.0f".format(cost / data.totalCost * 100)}%)" else ""

        fun changeArrow(delta: Double): String = when {
            delta > 0.01 -> "↑"
            delta < -0.01 -> "↓"
            else -> "→"
        }

        // ── Header ──
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

        // ── 环比对比 ──
        if (comparison != null) {
            appendLine(sepThin)
            val arrow = changeArrow(comparison.totalCostChange)
            val absPct = if (comparison.prevTotalCost > 0)
                "%.1f%%".format(kotlin.math.abs(comparison.totalCostChange / comparison.prevTotalCost * 100)) else ""
            appendLine("    较上月   $arrow ${"%.2f".format(kotlin.math.abs(comparison.totalCostChange))}  ($absPct)")
        }
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
            // 环比
            if (comparison != null) {
                val kwhArrow = changeArrow(comparison.kwhChange)
                appendLine("    较上月   $kwhArrow ${"%.1f".format(kotlin.math.abs(comparison.kwhChange))} kWh")
            }
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
        appendLine("    （通过 App 设置页分享）")
    }

    // ════════════════════════════════════════════
    //  HTML 报告（适合邮件/笔记类 App）
    // ════════════════════════════════════════════

    fun generateHtmlReport(data: ReportData, comparison: ComparisonData? = null): String = buildString {
        val accent = "#00FFC4"
        val electricColor = "#00FFC4"
        val peakColor = "#FF8800"
        val valleyColor = "#4488FF"
        val waterColor = "#00E5FF"
        val gasColor = "#FF9100"
        val bgDark = "#0D0F12"
        val cardBg = "#1F242F"
        val textPrimary = "#E2E8F0"
        val textSecondary = "#94A3B8"
        val textTertiary = "#64748B"
        val red = "#FF3366"
        val green = "#00E676"

        fun fmt(v: Double) = "%.2f".format(v)
        fun fmt1(v: Double) = "%.1f".format(v)

        fun changeBadge(delta: Double): String {
            return if (delta > 0.01) {
                """<span style="color:$red;font-size:13px">↑ ${fmt(kotlin.math.abs(delta))}</span>"""
            } else if (delta < -0.01) {
                """<span style="color:$green;font-size:13px">↓ ${fmt(kotlin.math.abs(delta))}</span>"""
            } else {
                """<span style="color:$textSecondary;font-size:13px">→ 持平</span>"""
            }
        }

        appendLine("""<!DOCTYPE html>""")
        appendLine("""<html lang="zh-CN">""")
        appendLine("""<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">""")
        appendLine("""<title>能耗手记 · 月度账单</title>""")
        appendLine("""<style>""")
        appendLine("""*{margin:0;padding:0;box-sizing:border-box}""")
        appendLine("""body{background:$bgDark;color:$textPrimary;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;padding:16px;line-height:1.6;-webkit-font-smoothing:antialiased}""")
        appendLine("""h1{font-size:22px;font-weight:700;margin-bottom:4px}""")
        appendLine("""h2{font-size:15px;font-weight:600;margin-bottom:12px;display:flex;align-items:center;gap:6px}""")
        appendLine(""".sub{color:$textSecondary;font-size:13px;margin-bottom:20px}""")
        appendLine(""".card{background:$cardBg;border-radius:12px;padding:16px;margin-bottom:12px;border:1px solid rgba(255,255,255,0.06)}""")
        appendLine(""".row{display:flex;justify-content:space-between;align-items:center;padding:8px 0}""")
        appendLine(""".row+.row{border-top:1px solid rgba(255,255,255,0.05)}""")
        appendLine(""".label{color:$textSecondary;font-size:14px}""")
        appendLine(""".value{font-size:14px;font-weight:600}""")
        appendLine(""".total-row{display:flex;justify-content:space-between;align-items:center;padding:12px 0 0}""")
        appendLine(""".total-label{font-size:15px;font-weight:700}""")
        appendLine(""".total-value{font-size:22px;font-weight:700;color:$accent}""")
        appendLine(""".badge{display:inline-block;padding:2px 10px;border-radius:10px;font-size:11px;font-weight:600}""")
        appendLine(""".bar-bg{height:8px;background:rgba(255,255,255,0.08);border-radius:4px;overflow:hidden;margin:4px 0 8px}""")
        appendLine(""".bar-fill{height:100%;border-radius:4px;transition:width .3s}""")
        appendLine(""".pv-row{display:flex;align-items:center;gap:8px;font-size:13px;padding:2px 0}""")
        appendLine(""".pv-dot{width:8px;height:8px;border-radius:50%;flex-shrink:0}""")
        appendLine(""".pv-bar{flex:1;height:6px;background:rgba(255,255,255,0.06);border-radius:3px;overflow:hidden}""")
        appendLine(""".pv-fill{height:100%;border-radius:3px}""")
        appendLine(""".pv-val{min-width:60px;text-align:right;font-size:12px;color:$textSecondary}""")
        appendLine(""".tag{display:inline-block;padding:1px 8px;border-radius:8px;background:rgba(0,255,196,0.1);color:$accent;font-size:11px;margin:2px 3px}""")
        appendLine(""".footer{text-align:center;color:$textTertiary;font-size:11px;margin-top:20px;padding-top:16px;border-top:1px solid rgba(255,255,255,0.06)}""")
        appendLine(""".comparison{background:rgba(0,255,196,0.05);border-radius:8px;padding:12px;margin-top:12px;border:1px solid rgba(0,255,196,0.12)}""")
        appendLine(""".comp-title{font-size:12px;color:$textSecondary;margin-bottom:8px}""")
        appendLine(""".comp-row{display:flex;justify-content:space-between;font-size:13px;padding:4px 0}""")
        appendLine("""</style></head><body>""")

        // ── Header ──
        appendLine("""<div style="text-align:center;padding:8px 0 16px">""")
        appendLine("""<div style="font-size:32px;margin-bottom:4px">⚡</div>""")
        appendLine("""<h1>能耗手记</h1>""")
        appendLine("""<div class="sub">${data.periodStart.monthValue}月${data.periodStart.dayOfMonth}日 — ${data.periodEnd.monthValue}月${data.periodEnd.dayOfMonth}日 · ${data.periodDays}天</div>""")
        appendLine("""</div>""")

        // ── 总费用 ──
        appendLine("""<div class="card">""")
        appendLine("""<div class="total-row"><span class="total-label">本月总费用</span><span class="total-value">¥${fmt(data.totalCost)}</span></div>""")
        if (comparison != null) {
            appendLine("""<div class="comp-row" style="margin-top:8px;border-top:1px solid rgba(255,255,255,0.05);padding-top:8px">""")
            appendLine("""<span class="label">上月</span><span style="font-size:13px;color:$textSecondary">¥${fmt(comparison.prevTotalCost)}</span>""")
            appendLine("""<span>${changeBadge(comparison.totalCostChange)}</span>""")
            appendLine("""</div>""")
        }
        appendLine("""</div>""")

        // ── 费用明细 ──
        appendLine("""<div class="card">""")
        appendLine("""<h2>💰 费用明细</h2>""")
        appendLine("""<div class="row"><span class="label"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:$electricColor;margin-right:8px"></span>电费</span><span class="value" style="color:$electricColor">¥${fmt(data.electricCost)}</span></div>""")
        appendLine("""<div class="row"><span class="label"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:$waterColor;margin-right:8px"></span>水费</span><span class="value" style="color:$waterColor">¥${fmt(data.waterCost)}</span></div>""")
        if (data.gasM3 > 0) {
            appendLine("""<div class="row"><span class="label"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:$gasColor;margin-right:8px"></span>燃气费</span><span class="value" style="color:$gasColor">¥${fmt(data.gasCost)}</span></div>""")
        }
        appendLine("""</div>""")

        // ── 用电 ──
        appendLine("""<div class="card">""")
        appendLine("""<h2>📊 用电明细</h2>""")
        appendLine("""<div class="row"><span class="label">总用电</span><span class="value">${fmt1(data.electricKwh)} kWh</span></div>""")
        appendLine("""<div class="row"><span class="label">日均用电</span><span class="value">${fmt1(data.dailyAvgKwh)} kWh</span></div>""")
        if (data.peakKwh > 0 || data.valleyKwh > 0) {
            val totalPv = data.peakKwh + data.valleyKwh
            val peakPct = if (totalPv > 0) data.peakKwh / totalPv * 100 else 0.0
            val valleyPct = if (totalPv > 0) data.valleyKwh / totalPv * 100 else 0.0
            appendLine("""<div style="margin-top:8px">""")
            appendLine("""<div class="pv-row"><span class="pv-dot" style="background:$peakColor"></span><span style="flex:1;font-size:12px">峰电</span><span style="font-size:12px;color:$peakColor">${fmt1(data.peakKwh)} kWh</span></div>""")
            appendLine("""<div class="pv-bar"><div class="pv-fill" style="width:${"%.0f".format(peakPct)}%;background:$peakColor"></div></div>""")
            appendLine("""<div class="pv-row"><span class="pv-dot" style="background:$valleyColor"></span><span style="flex:1;font-size:12px">谷电</span><span style="font-size:12px;color:$valleyColor">${fmt1(data.valleyKwh)} kWh</span></div>""")
            appendLine("""<div class="pv-bar"><div class="pv-fill" style="width:${"%.0f".format(valleyPct)}%;background:$valleyColor"></div></div>""")
            if (comparison != null) {
                appendLine("""<div class="pv-row" style="margin-top:6px;border-top:1px solid rgba(255,255,255,0.05);padding-top:6px">""")
                appendLine("""<span style="font-size:12px;color:$textSecondary">较上月</span><span>${changeBadge(comparison.kwhChange)}</span>""")
                appendLine("""</div>""")
            }
            appendLine("""</div>""")
        } else {
            appendLine("""<div class="row"><span class="label">单价</span><span class="value">¥${"%.4f".format(data.flatPrice)}/kWh</span></div>""")
        }
        appendLine("""<div class="row"><span class="label">阶梯</span><span class="badge" style="background:rgba(0,255,196,0.12);color:$accent">${data.tierLevel}</span></div>""")
        appendLine("""</div>""")

        // ── 用水 ──
        appendLine("""<div class="card">""")
        appendLine("""<h2>💧 用水明细</h2>""")
        appendLine("""<div class="row"><span class="label">总用水</span><span class="value" style="color:$waterColor">${fmt1(data.waterTons)} 吨</span></div>""")
        if (data.waterTierInfo.isNotBlank()) {
            appendLine("""<div class="row"><span class="label">阶梯</span><span class="value" style="font-size:12px;color:$textSecondary">${data.waterTierInfo}</span></div>""")
        }
        if (comparison != null) {
            val waterArrow = if (comparison.waterChange > 0.01) "↑" else if (comparison.waterChange < -0.01) "↓" else "→"
            val waterColorStr = if (comparison.waterChange > 0.01) red else if (comparison.waterChange < -0.01) green else textSecondary
            appendLine("""<div class="row"><span class="label">较上月</span><span style="color:$waterColorStr;font-size:13px">$waterArrow ${fmt1(kotlin.math.abs(comparison.waterChange))} 吨</span></div>""")
        }
        appendLine("""</div>""")

        // ── 燃气（如果有） ──
        if (data.gasM3 > 0) {
            appendLine("""<div class="card">""")
            appendLine("""<h2>🔥 燃气明细</h2>""")
            appendLine("""<div class="row"><span class="label">总用气</span><span class="value" style="color:$gasColor">${fmt1(data.gasM3)} m³</span></div>""")
            appendLine("""</div>""")
        }

        // ── 统计 ──
        appendLine("""<div class="card">""")
        appendLine("""<h2>📈 统计</h2>""")
        appendLine("""<div class="row"><span class="label">记录数</span><span class="value">${data.recordCount} 条</span></div>""")
        appendLine("""<div class="row"><span class="label">日均费用</span><span class="value">¥${fmt(data.dailyAvgCost)}</span></div>""")
        if (data.topNotes.isNotEmpty()) {
            val tagsHtml = data.topNotes.joinToString("") { "<span class=\"tag\">$it</span>" }
            appendLine("""<div class="row"><span class="label">标签</span><span>$tagsHtml</span></div>""")
        }
        appendLine("""</div>""")

        // ── Footer ──
        appendLine("""<div class="footer">""")
        appendLine("""Energy Flow · 你的能耗小助手""")
        appendLine("""</div>""")

        appendLine("""</body></html>""")
    }

    // ════════════════════════════════════════════
    //  buildReportData — 支持任意月份 & 环比
    // ════════════════════════════════════════════

    suspend fun buildReportData(
        electricRecords: List<MeterRecord>,
        waterRecords: List<MeterRecord>,
        gasRecords: List<MeterRecord>,
        notesRecords: List<MeterRecord>,
        costEngine: CostEngine,
        rules: BillingRules? = null,
        targetMonth: YearMonth = YearMonth.now()
    ): ReportData? {
        val elecMonth = electricRecords.filter {
            it.electricTotal != null && YearMonth.from(it.timestamp) == targetMonth
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
            it.waterTotal != null && YearMonth.from(it.timestamp) == targetMonth
        }.sortedBy { it.timestamp }
        val waterTons = if (waterMonth.size >= 2)
            (waterMonth.last().waterTotal!! - waterMonth.first().waterTotal!!).coerceAtLeast(0.0) else 0.0

        val gasMonth = gasRecords.filter {
            it.isGasRecorded && it.gasTotal != null && YearMonth.from(it.timestamp) == targetMonth
        }.sortedBy { it.timestamp }
        val gasM3 = if (gasMonth.size >= 2)
            (gasMonth.last().gasTotal!! - gasMonth.first().gasTotal!!).coerceAtLeast(0.0) else 0.0

        val bill = costEngine.calculateBill(kwh, peakKwh, valleyKwh, waterTons)

        // 当月标签统计
        val tagCounts = mutableMapOf<String, Int>()
        notesRecords.filter { YearMonth.from(it.timestamp) == targetMonth }.forEach { record ->
            record.note?.split(Regex("\\s+"))?.filter { it.isNotBlank() }?.forEach {
                tagCounts[it] = (tagCounts[it] ?: 0) + 1
            }
        }
        val topNotes = tagCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        val activeRules = rules ?: BillingRules()
        val tierLevel = when {
            kwh > activeRules.electricTier2Limit -> "三档"
            kwh > activeRules.electricTier1Limit -> "二档"
            else -> "一档"
        }
        val waterTierInfo = buildWaterTierInfo(waterTons, activeRules)
        val gasCost = gasM3 * activeRules.gasUnitPrice

        return ReportData(
            yearMonth = "${targetMonth.year}-${targetMonth.monthValue}",
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
     * 构建环比数据，供报告生成使用。
     */
    suspend fun buildComparison(
        current: ReportData,
        electricRecords: List<MeterRecord>,
        waterRecords: List<MeterRecord>,
        gasRecords: List<MeterRecord>,
        notesRecords: List<MeterRecord>,
        costEngine: CostEngine,
        rules: BillingRules?
    ): ComparisonData? {
        val targetMonth = try {
            val parts = current.yearMonth.split("-")
            YearMonth.of(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) { return null }
        val prevMonth = targetMonth.minusMonths(1)
        val prevData = buildReportData(
            electricRecords, waterRecords, gasRecords, notesRecords,
            costEngine, rules, prevMonth
        ) ?: return null

        return ComparisonData(
            kwhChange = current.electricKwh - prevData.electricKwh,
            costChange = current.electricCost - prevData.electricCost,
            waterChange = current.waterTons - prevData.waterTons,
            totalCostChange = current.totalCost - prevData.totalCost,
            prevKwh = prevData.electricKwh,
            prevCost = prevData.electricCost,
            prevWater = prevData.waterTons,
            prevTotalCost = prevData.totalCost,
            prevDays = prevData.periodDays
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
