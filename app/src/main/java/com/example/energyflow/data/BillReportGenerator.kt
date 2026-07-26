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
        val topNotes: List<String>,
        val co2Kg: Double = 0.0,
        val treeDays: Int = 0,
        // 新增：成就徽章和节能建议（统一数据源）
        val badges: List<String> = emptyList(),
        val tips: List<String> = emptyList(),
        val previousCost: Double? = null,
        val flatKwh: Double = 0.0
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
        val w = 44
        val sepThick = "═".repeat(w)
        val sepThin = "─".repeat(w)
        val sepDot = "·".repeat(w)

        // 中文字符宽度补偿：中文字符占2列，需要额外padding
        fun String.displayWidth(): Int = sumOf { if (it.code > 0x7F) 2 else 1 }
        fun String.padEndDisplay(totalWidth: Int, padChar: Char = ' '): String {
            val currentWidth = displayWidth()
            return if (currentWidth >= totalWidth) this
            else this + padChar.toString().repeat(totalWidth - currentWidth)
        }

        fun costPct(cost: Double): String =
            if (data.totalCost > 0) "%.0f%%".format(cost / data.totalCost * 100) else ""

        fun changeArrow(delta: Double): String = when {
            delta > 0.01 -> "↑"
            delta < -0.01 -> "↓"
            else -> "→"
        }

        fun changeStr(delta: Double, unit: String = ""): String {
            val arrow = changeArrow(delta)
            val abs = "%.1f".format(kotlin.math.abs(delta))
            return "$arrow $abs$unit"
        }

        // ── Header ──
        appendLine("╔$sepThick╗")
        appendLine("║  ⚡ 能耗手记 · 月度账单".padEndDisplay(w + 2) + "║")
        appendLine("║  ${data.periodStart.monthValue}月${data.periodStart.dayOfMonth}日 — ${data.periodEnd.monthValue}月${data.periodEnd.dayOfMonth}日  (${data.periodDays}天)".padEndDisplay(w + 2) + "║")
        appendLine("╚$sepThick╝")
        appendLine()

        // ── KPI Hero ──
        appendLine("┌$sepThin┐")
        appendLine("│${"💰 本月总费用".padStart(22).padEndDisplay(w)}│")
        appendLine("│${"¥${"%.2f".format(data.totalCost)}".padStart((w + 8) / 2).padEndDisplay(w)}│")
        appendLine("│${"日均 ¥${"%.2f".format(data.dailyAvgCost)}".padStart(25).padEndDisplay(w)}│")
        if (comparison != null) {
            val pct = if (comparison.prevTotalCost > 0)
                "%.1f%%".format(kotlin.math.abs(comparison.totalCostChange / comparison.prevTotalCost * 100)) else ""
            val arrow = changeArrow(comparison.totalCostChange)
            val compStr = "较上月 $arrow ¥${"%.2f".format(kotlin.math.abs(comparison.totalCostChange))} ($pct)"
            appendLine("│${compStr.padStart(24).padEndDisplay(w)}│")
        }
        appendLine("└$sepThin┘")
        appendLine()

        // ── 费用明细 ──
        appendLine("  📊 费用明细")
        appendLine("  $sepThin")
        fun costRow(label: String, cost: Double): String {
            val pct = costPct(cost)
            val labelPadded = label.padEndDisplay(6)
            return "  $labelPadded ¥${"%10s".format("%.2f".format(cost))}  ${pct.padStart(5)}"
        }
        appendLine(costRow("电费", data.electricCost))
        appendLine(costRow("水费", data.waterCost))
        if (data.gasM3 > 0) {
            appendLine(costRow("燃气费", data.gasCost))
        }
        appendLine("  $sepThin")
        appendLine("  ${"合计".padEndDisplay(6)} ¥${"%10s".format("%.2f".format(data.totalCost))}")
        appendLine()

        // ── 用电明细 ──
        appendLine("  ⚡ 用电明细")
        appendLine("  $sepThin")
        appendLine("  ${"总用电".padEndDisplay(7)} ${"%11s".format("%.1f kWh".format(data.electricKwh))}")
        appendLine("  ${"日均".padEndDisplay(7)} ${"%11s".format("%.1f kWh".format(data.dailyAvgKwh))}")
        if (data.peakKwh > 0 || data.valleyKwh > 0) {
            val totalPv = data.peakKwh + data.valleyKwh
            val peakPct = if (totalPv > 0) data.peakKwh / totalPv * 100 else 0.0
            val valleyPct = if (totalPv > 0) data.valleyKwh / totalPv * 100 else 0.0
            val barLen = 18
            val peakBars = (peakPct / 100 * barLen).toInt().coerceIn(0, barLen)
            val valleyBars = (valleyPct / 100 * barLen).toInt().coerceIn(0, barLen)
            appendLine("  ─── 峰谷分布 ───")
            appendLine("  峰 %5.1f  %s%s %3.0f%%".format(
                data.peakKwh, "▓".repeat(peakBars), "░".repeat((barLen - peakBars).coerceAtLeast(0)), peakPct
            ))
            appendLine("  谷 %5.1f  %s%s %3.0f%%".format(
                data.valleyKwh, "▓".repeat(valleyBars), "░".repeat((barLen - valleyBars).coerceAtLeast(0)), valleyPct
            ))
            if (comparison != null) {
                appendLine("  较上月  ${changeStr(comparison.kwhChange, " kWh")}")
            }
        } else {
            appendLine("  ${"单价".padEndDisplay(7)} ¥%.4f/kWh".format(data.flatPrice))
        }
        appendLine("  ${"阶梯".padEndDisplay(7)} ${data.tierLevel}")
        appendLine()

        // ── 用水明细 ──
        appendLine("  💧 用水明细")
        appendLine("  $sepThin")
        val waterDaily = if (data.periodDays > 0) data.waterTons / data.periodDays else 0.0
        appendLine("  ${"总用水".padEndDisplay(7)} ${"%11s".format("%.1f 吨".format(data.waterTons))}")
        appendLine("  ${"日均".padEndDisplay(7)} ${"%11s".format("%.2f 吨".format(waterDaily))}")
        if (data.waterTierInfo.isNotBlank()) {
            appendLine("  ${"阶梯".padEndDisplay(7)} ${data.waterTierInfo}")
        }
        if (comparison != null) {
            appendLine("  较上月  ${changeStr(comparison.waterChange, " 吨")}")
        }
        appendLine()

        // ── 燃气明细 ──
        if (data.gasM3 > 0) {
            appendLine("  🔥 燃气明细")
            appendLine("  $sepThin")
            val gasDaily = if (data.periodDays > 0) data.gasM3 / data.periodDays else 0.0
            appendLine("  ${"总用气".padEndDisplay(7)} ${"%11s".format("%.1f m³".format(data.gasM3))}")
            appendLine("  ${"日均".padEndDisplay(7)} ${"%11s".format("%.2f m³".format(gasDaily))}")
            appendLine()
        }

        // ── 碳排放 ──
        val co2Kg = data.co2Kg
        val treeEquiv = if (data.treeDays > 0) data.treeDays.toDouble() else co2Kg / 20.0
        appendLine("  🌳 碳排放")
        appendLine("  $sepThin")
        appendLine("  ${"排放量".padEndDisplay(7)} %.1f kg CO₂".format(co2Kg))
        appendLine("  ${"等效".padEndDisplay(7)} ≈ %.1f 棵树/天吸收".format(treeEquiv))
        appendLine()

        // ── 统计 ──
        appendLine("  📈 统计")
        appendLine("  $sepThin")
        appendLine("  ${"记录数".padEndDisplay(7)} %d 条".format(data.recordCount))
        if (data.topNotes.isNotEmpty()) {
            appendLine("  ${"标签".padEndDisplay(7)} ${data.topNotes.joinToString(" · ")}")
        }
        appendLine()
        appendLine("  $sepDot")
        appendLine("  Energy Flow · 你的能耗小助手")
        appendLine("  💚 感谢你为节能减排做出的贡献！")
    }

    // ════════════════════════════════════════════
    //  HTML 报告（适合邮件/笔记类 App）
    // ════════════════════════════════════════════

    fun generateHtmlReport(data: ReportData, comparison: ComparisonData? = null, isDarkTheme: Boolean = true): String = buildString {
        val electricAccent: String
        val electricEnd: String
        val peakColor: String
        val valleyColor: String
        val waterAccent: String
        val gasAccent: String
        val bgDark: String
        val cardBg: String
        val textPrimary: String
        val textSecondary: String
        val textTertiary: String
        val red: String
        val green: String
        val accent: String

        if (isDarkTheme) {
            // Obsidian 暗色主题
            electricAccent = "#00A8FF"
            electricEnd = "#0058DD"
            peakColor = "#FF9922"
            valleyColor = "#9977EE"
            waterAccent = "#00DDBB"
            gasAccent = "#FF8844"
            bgDark = "#0A0C14"
            cardBg = "#1A1E30"
            textPrimary = "#E2E8F0"
            textSecondary = "#94A3B8"
            textTertiary = "#64748B"
            red = "#FF4466"
            green = "#00DD99"
            accent = electricAccent
        } else {
            // Pearl 亮色主题
            electricAccent = "#0058DD"
            electricEnd = "#003399"
            peakColor = "#DD7700"
            valleyColor = "#7755CC"
            waterAccent = "#009988"
            gasAccent = "#DD6622"
            bgDark = "#F5F6FA"
            cardBg = "#FFFFFF"
            textPrimary = "#0F172A"
            textSecondary = "#475569"
            textTertiary = "#718096"
            red = "#DD2244"
            green = "#00AA77"
            accent = electricAccent
        }

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
        if (isDarkTheme) {
            appendLine("""body{background:radial-gradient(ellipse at 50% 0%,#121830 0%,$bgDark 70%);color:$textPrimary;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;padding:16px;line-height:1.6;-webkit-font-smoothing:antialiased;max-width:600px;margin:0 auto}""")
        } else {
            appendLine("""body{background:radial-gradient(ellipse at 50% 0%,#E8EAF0 0%,$bgDark 70%);color:$textPrimary;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;padding:16px;line-height:1.6;-webkit-font-smoothing:antialiased;max-width:600px;margin:0 auto}""")
        }
        appendLine(""".hero{text-align:center;padding:24px 16px;background:linear-gradient(135deg,$electricAccent 0%,$electricEnd 100%);border-radius:16px;margin-bottom:14px;color:#fff;box-shadow:0 4px 20px rgba(0,152,255,0.25)}""")
        appendLine(""".hero-icon{font-size:40px;margin-bottom:6px}""")
        appendLine(""".hero-title{font-size:20px;font-weight:700;margin-bottom:2px}""")
        appendLine(""".hero-sub{font-size:12px;opacity:0.85;margin-bottom:12px}""")
        appendLine(""".hero-total{font-size:40px;font-weight:800;letter-spacing:-1px}""")
        appendLine(""".hero-daily{font-size:14px;opacity:0.8;margin-top:4px}""")
        appendLine("""h2{font-size:14px;font-weight:600;margin-bottom:10px;display:flex;align-items:center;gap:6px}""")
        appendLine(""".h2-dot{display:inline-block;width:10px;height:10px;border-radius:3px;flex-shrink:0}""")
        if (isDarkTheme) {
            appendLine(""".card{background:$cardBg;border-radius:14px;padding:16px;margin-bottom:10px;border:1px solid rgba(255,255,255,0.05);box-shadow:0 2px 8px rgba(0,0,0,0.2);transition:transform .15s ease,box-shadow .15s ease}""")
            appendLine(""".card:hover{transform:scale(1.01);box-shadow:0 4px 16px rgba(0,0,0,0.35)}""")
            appendLine(""".card-sep{height:1px;background:linear-gradient(90deg,transparent,rgba(255,255,255,0.06),transparent);margin:16px 0}""")
            appendLine(""".row{display:flex;justify-content:space-between;align-items:center;padding:6px 0}""")
            appendLine(""".row+.row{border-top:1px solid rgba(255,255,255,0.04)}""")
        } else {
            appendLine(""".card{background:$cardBg;border-radius:14px;padding:16px;margin-bottom:10px;border:1px solid rgba(0,0,0,0.06);box-shadow:0 2px 8px rgba(0,0,0,0.08);transition:transform .15s ease,box-shadow .15s ease}""")
            appendLine(""".card:hover{transform:scale(1.01);box-shadow:0 4px 16px rgba(0,0,0,0.12)}""")
            appendLine(""".card-sep{height:1px;background:linear-gradient(90deg,transparent,rgba(0,0,0,0.06),transparent);margin:16px 0}""")
            appendLine(""".row{display:flex;justify-content:space-between;align-items:center;padding:6px 0}""")
            appendLine(""".row+.row{border-top:1px solid rgba(0,0,0,0.04)}""")
        }
        appendLine(""".label{color:$textSecondary;font-size:13px}""")
        appendLine(""".value{font-size:14px;font-weight:600}""")
        appendLine(""".dot{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:6px;flex-shrink:0}""")
        appendLine(""".badge{display:inline-block;padding:2px 10px;border-radius:10px;font-size:11px;font-weight:600}""")
        appendLine(""".stacked-bar{display:flex;height:28px;border-radius:8px;overflow:hidden;margin:8px 0}""")
        appendLine(""".seg-peak{height:100%;transition:width .3s;display:flex;align-items:center;justify-content:center;font-size:10px;font-weight:700;color:#fff;background:linear-gradient(90deg,$peakColor,#FFAA33)}""")
        appendLine(""".seg-valley{height:100%;transition:width .3s;display:flex;align-items:center;justify-content:center;font-size:10px;font-weight:700;color:#fff;background:linear-gradient(90deg,#7755CC,$valleyColor)}""")
        appendLine(""".legend{display:flex;gap:16px;font-size:12px;color:$textSecondary;margin-top:6px}""")
        appendLine(""".legend-item{display:flex;align-items:center;gap:4px}""")
        if (isDarkTheme) {
            appendLine(""".comparison-box{background:rgba(0,152,255,0.06);border-radius:10px;padding:10px 14px;margin-top:12px;border:1px solid rgba(0,152,255,0.1)}""")
        } else {
            appendLine(""".comparison-box{background:rgba(0,88,221,0.06);border-radius:10px;padding:10px 14px;margin-top:12px;border:1px solid rgba(0,88,221,0.1)}""")
        }
        appendLine(""".comp-row{display:flex;justify-content:space-between;font-size:12px;padding:3px 0}""")
        if (isDarkTheme) {
            appendLine(""".tag{display:inline-block;padding:2px 8px;border-radius:8px;background:rgba(0,152,255,0.1);color:$accent;font-size:11px;margin:2px 3px}""")
        } else {
            appendLine(""".tag{display:inline-block;padding:2px 8px;border-radius:8px;background:rgba(0,88,221,0.1);color:$accent;font-size:11px;margin:2px 3px}""")
        }
        if (isDarkTheme) {
            appendLine(""".footer{text-align:center;color:$textTertiary;font-size:11px;margin-top:18px;padding-top:14px;border-top:1px solid rgba(255,255,255,0.05)}""")
        } else {
            appendLine(""".footer{text-align:center;color:$textTertiary;font-size:11px;margin-top:18px;padding-top:14px;border-top:1px solid rgba(0,0,0,0.05)}""")
        }
        appendLine("""</style></head><body>""")

        // ── Hero Section ──
        appendLine("""<div class="hero">""")
        appendLine("""<div class="hero-icon">⚡</div>""")
        appendLine("""<div class="hero-title">能耗手记 · 月度账单</div>""")
        appendLine("""<div class="hero-sub">${data.periodStart.monthValue}月${data.periodStart.dayOfMonth}日 — ${data.periodEnd.monthValue}月${data.periodEnd.dayOfMonth}日 · ${data.periodDays}天</div>""")
        appendLine("""<div class="hero-total">¥${fmt(data.totalCost)}</div>""")
        appendLine("""<div class="hero-daily">日均 ¥${fmt(data.dailyAvgCost)}""")
        if (comparison != null) {
            val arrow = if (comparison.totalCostChange > 0.01) "↑" else if (comparison.totalCostChange < -0.01) "↓" else "→"
            append(""" · 较上月 $arrow ¥${fmt(kotlin.math.abs(comparison.totalCostChange))}""")
        }
        appendLine("""</div>""")
        appendLine("""</div>""")

        // ── 费用明细 ──
        appendLine("""<div class="card">""")
        appendLine("""<h2><span class="h2-dot" style="background:$electricAccent"></span>💰 费用明细</h2>""")
        appendLine("""<div class="row"><span class="label"><span class="dot" style="background:$electricAccent"></span>电费</span><span class="value" style="color:$electricAccent">¥${fmt(data.electricCost)}</span></div>""")
        appendLine("""<div class="row"><span class="label"><span class="dot" style="background:$waterAccent"></span>水费</span><span class="value" style="color:$waterAccent">¥${fmt(data.waterCost)}</span></div>""")
        if (data.gasM3 > 0) {
            appendLine("""<div class="row"><span class="label"><span class="dot" style="background:$gasAccent"></span>燃气费</span><span class="value" style="color:$gasAccent">¥${fmt(data.gasCost)}</span></div>""")
        }
        appendLine("""</div>""")

        // ── 用电 ──
        appendLine("""<div class="card">""")
        appendLine("""<h2><span class="h2-dot" style="background:$electricAccent"></span>📊 用电明细</h2>""")
        appendLine("""<div class="row"><span class="label">总用电</span><span class="value">${fmt1(data.electricKwh)} kWh</span></div>""")
        appendLine("""<div class="row"><span class="label">日均用电</span><span class="value">${fmt1(data.dailyAvgKwh)} kWh</span></div>""")
        if (data.peakKwh > 0 || data.valleyKwh > 0) {
            val totalPv = data.peakKwh + data.valleyKwh
            val peakPct = if (totalPv > 0) (data.peakKwh / totalPv * 100).toInt() else 0
            val valleyPct = if (totalPv > 0) (data.valleyKwh / totalPv * 100).toInt() else 0
            val flatPct = 100 - peakPct - valleyPct
            appendLine("""<div class="card-sep"></div>""")
            appendLine("""<div style="font-size:13px;color:$textSecondary;margin-bottom:4px">峰谷分布</div>""")
            appendLine("""<div class="stacked-bar">""")
            if (peakPct > 0) appendLine("""<div class="seg-peak" style="width:${peakPct}%">${peakPct}%</div>""")
            if (flatPct > 0) appendLine("""<div style="height:100%;width:${flatPct}%;background:${if (isDarkTheme) "#30364B" else "#D8DCE6"}"></div>""")
            if (valleyPct > 0) appendLine("""<div class="seg-valley" style="width:${valleyPct}%">${valleyPct}%</div>""")
            appendLine("""</div>""")
            appendLine("""<div class="legend">""")
            appendLine("""<span class="legend-item"><span class="dot" style="background:$peakColor"></span>峰电 ${fmt1(data.peakKwh)} kWh</span>""")
            appendLine("""<span class="legend-item"><span class="dot" style="background:$valleyColor"></span>谷电 ${fmt1(data.valleyKwh)} kWh</span>""")
            appendLine("""</div>""")
            if (comparison != null) {
                appendLine("""<div class="comparison-box"><div class="comp-row"><span>较上月用电</span><span>${changeBadge(comparison.kwhChange)}</span></div></div>""")
            }
        } else {
            appendLine("""<div class="row"><span class="label">单价</span><span class="value">¥${"%.4f".format(data.flatPrice)}/kWh</span></div>""")
        }
        appendLine("""<div class="row" style="margin-top:4px"><span class="label">阶梯</span><span class="badge" style="background:rgba(0,152,255,0.12);color:$accent">${data.tierLevel}</span></div>""")
        appendLine("""</div>""")

        // ── 用水 ──
        appendLine("""<div class="card">""")
        appendLine("""<h2><span class="h2-dot" style="background:$waterAccent"></span>💧 用水明细</h2>""")
        appendLine("""<div class="row"><span class="label">总用水</span><span class="value" style="color:$waterAccent">${fmt1(data.waterTons)} 吨</span></div>""")
        val waterDaily = if (data.periodDays > 0) data.waterTons / data.periodDays else 0.0
        appendLine("""<div class="row"><span class="label">日均用水</span><span class="value">${fmt1(waterDaily)} 吨</span></div>""")
        if (data.waterTierInfo.isNotBlank()) {
            appendLine("""<div class="row"><span class="label">阶梯</span><span class="value" style="font-size:12px;color:$textSecondary">${data.waterTierInfo}</span></div>""")
        }
        if (comparison != null) {
            appendLine("""<div class="comparison-box"><div class="comp-row"><span>较上月用水</span><span>${changeBadge(comparison.waterChange)}</span></div></div>""")
        }
        appendLine("""</div>""")

        // ── 燃气（如果有） ──
        if (data.gasM3 > 0) {
            appendLine("""<div class="card">""")
            appendLine("""<h2><span class="h2-dot" style="background:$gasAccent"></span>🔥 燃气明细</h2>""")
            appendLine("""<div class="row"><span class="label">总用气</span><span class="value" style="color:$gasAccent">${fmt1(data.gasM3)} m³</span></div>""")
            val gasDaily = if (data.periodDays > 0) data.gasM3 / data.periodDays else 0.0
            appendLine("""<div class="row"><span class="label">日均用气</span><span class="value">${fmt1(gasDaily)} m³</span></div>""")
            appendLine("""</div>""")
        }

        // ── 碳排放 ──
        if (data.co2Kg > 0) {
            val treeEquiv = if (data.treeDays > 0) data.treeDays.toDouble() else data.co2Kg / 20.0
            appendLine("""<div class="card">""")
            appendLine("""<h2><span class="h2-dot" style="background:$green"></span>🌳 碳排放</h2>""")
            appendLine("""<div class="row"><span class="label">排放量</span><span class="value">${fmt1(data.co2Kg)} kg CO₂</span></div>""")
            appendLine("""<div class="row"><span class="label">等效植树</span><span class="value" style="color:$green">≈ ${fmt1(treeEquiv)} 棵树/天吸收</span></div>""")
            appendLine("""</div>""")
        }

        // ── 统计 ──
        appendLine("""<div class="card">""")
        appendLine("""<h2><span class="h2-dot" style="background:$textTertiary"></span>📈 统计</h2>""")
        appendLine("""<div class="row"><span class="label">记录数</span><span class="value">${data.recordCount} 条</span></div>""")
        if (data.topNotes.isNotEmpty()) {
            val tagsHtml = data.topNotes.joinToString("") { "<span class=\"tag\">$it</span>" }
            appendLine("""<div class="row"><span class="label">标签</span><span>$tagsHtml</span></div>""")
        }
        appendLine("""</div>""")

        // ── Footer ──
        appendLine("""<div class="footer">""")
        appendLine("""Energy Flow · 你的能耗小助手""")
        appendLine("""💚 感谢你为节能减排做出的贡献！""")
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
        val kwh = ((last.electricTotal ?: 0.0) - (first.electricTotal ?: 0.0)).coerceAtLeast(0.0)
        val days = ChronoUnit.DAYS.between(first.timestamp.toLocalDate(), last.timestamp.toLocalDate()).coerceAtLeast(1)

        val peakKwh = if (last.electricPeak != null && first.electricPeak != null)
            ((last.electricPeak ?: 0.0) - (first.electricPeak ?: 0.0)).coerceAtLeast(0.0).coerceAtMost(kwh) else 0.0
        val valleyKwh = if (last.electricValley != null && first.electricValley != null)
            ((last.electricValley ?: 0.0) - (first.electricValley ?: 0.0)).coerceAtLeast(0.0).coerceAtMost(kwh - peakKwh) else 0.0

        val waterMonth = waterRecords.filter {
            it.waterTotal != null && YearMonth.from(it.timestamp) == targetMonth
        }.sortedBy { it.timestamp }
        val waterTons = if (waterMonth.size >= 2)
            ((waterMonth.last().waterTotal ?: 0.0) - (waterMonth.first().waterTotal ?: 0.0)).coerceAtLeast(0.0) else 0.0

        val gasMonth = gasRecords.filter {
            it.isGasRecorded && it.gasTotal != null && YearMonth.from(it.timestamp) == targetMonth
        }.sortedBy { it.timestamp }
        val gasM3 = if (gasMonth.size >= 2)
            ((gasMonth.last().gasTotal ?: 0.0) - (gasMonth.first().gasTotal ?: 0.0)).coerceAtLeast(0.0) else 0.0

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

        // Default carbon calculation using standard China grid factor
        val co2Kg = kwh * 0.583
        val treeDays = (co2Kg / 20.0).toInt().coerceAtLeast(0)
        val flatKwh = (kwh - peakKwh - valleyKwh).coerceAtLeast(0.0)

        // 生成成就徽章
        val badges = mutableListOf<String>()
        if (kwh < 300) badges.add("⚡节能先锋")
        else if (kwh < 500) badges.add("⚡合理用电")
        if (treeDays > 5) badges.add("🌿绿色达人")
        if (kwh > 0 && peakKwh / kwh < 0.3) badges.add("🏔️错峰能手")
        if (kwh > 0) badges.add("📊坚持记录")
        if (badges.isEmpty()) badges.add("🌱初来乍到")

        // 生成节能建议
        val tips = mutableListOf<String>()
        if (peakKwh > 0) tips.add("将大功率电器移至谷电时段使用可节省电费")
        tips.add("定期记录数据，获取更精准的能耗分析")

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
            topNotes = topNotes,
            co2Kg = co2Kg,
            treeDays = treeDays,
            badges = badges,
            tips = tips,
            flatKwh = flatKwh
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
     * 将 ReportData 转换为 ReportExporter.ReportContent（统一数据源）。
     */
    fun toReportContent(data: ReportData, isDarkTheme: Boolean = true): ReportExporter.ReportContent {
        return ReportExporter.ReportContent(
            period = "${data.periodStart.year}年${data.periodStart.monthValue}月",
            totalKwh = data.electricKwh,
            totalCost = data.totalCost,
            co2Kg = data.co2Kg,
            peakKwh = data.peakKwh,
            valleyKwh = data.valleyKwh,
            flatKwh = data.flatKwh,
            treeDays = data.treeDays,
            badges = data.badges,
            previousCost = data.previousCost,
            tips = data.tips,
            recordCount = data.recordCount,
            waterTons = data.waterTons,
            waterCost = data.waterCost,
            gasM3 = data.gasM3,
            gasCost = data.gasCost,
            isDarkTheme = isDarkTheme
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
