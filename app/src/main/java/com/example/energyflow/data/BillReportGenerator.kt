package com.example.energyflow.data

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 账单报告生成器 — 生成结构化文本报告，可直接分享到微信/邮件/系统分享面板。
 */
object BillReportGenerator {

    data class ReportData(
        val yearMonth: String,
        val electricKwh: Double,
        val electricCost: Double,
        val peakKwh: Double,
        val valleyKwh: Double,
        val waterTons: Double,
        val waterCost: Double,
        val gasM3: Double,
        val totalCost: Double,
        val dailyAvgKwh: Double,
        val dailyAvgCost: Double,
        val peakPrice: Double,
        val valleyPrice: Double,
        val tierLevel: String,
        val recordCount: Int,
        val topNotes: List<String>
    )

    fun generateTextReport(data: ReportData): String = buildString {
        val fmt = DateTimeFormatter.ofPattern("yyyy年M月")
        val now = YearMonth.now()
        appendLine("═══════════════════════")
        appendLine("   ⚡ 能耗手记 · 月度账单")
        appendLine("   ${now.format(fmt)}")
        appendLine("═══════════════════════")
        appendLine()
        appendLine("─── 📊 用电 ───")
        appendLine("总用电: ${"%.1f".format(data.electricKwh)} 度")
        appendLine("日均: ${"%.1f".format(data.dailyAvgKwh)} 度/天")
        if (data.peakKwh > 0 || data.valleyKwh > 0) {
            appendLine("峰电: ${"%.1f".format(data.peakKwh)} 度 (¥${"%.2f".format(data.peakPrice)}/度)")
            appendLine("谷电: ${"%.1f".format(data.valleyKwh)} 度 (¥${"%.2f".format(data.valleyPrice)}/度)")
        }
        appendLine("电费: ¥${"%.2f".format(data.electricCost)}")
        appendLine("阶梯档位: ${data.tierLevel}")
        appendLine()
        appendLine("─── 💧 用水 ───")
        appendLine("总用水: ${"%.1f".format(data.waterTons)} 吨")
        appendLine("水费: ¥${"%.2f".format(data.waterCost)}")
        appendLine()
        if (data.gasM3 > 0) {
            appendLine("─── 🔥 燃气 ───")
            appendLine("总用气: ${"%.1f".format(data.gasM3)} m³")
            appendLine()
        }
        appendLine("═══ 💰 合计费用: ¥${"%.2f".format(data.totalCost)} ═══")
        appendLine()
        appendLine("─── 📈 统计 ───")
        appendLine("记录条数: ${data.recordCount}")
        appendLine("日均费用: ¥${"%.2f".format(data.dailyAvgCost)}")
        if (data.topNotes.isNotEmpty()) {
            appendLine("高频标签: ${data.topNotes.joinToString("、")}")
        }
        appendLine()
        appendLine("──── Energy Flow ────")
    }

    suspend fun buildReportData(
        electricRecords: List<MeterRecord>,
        waterRecords: List<MeterRecord>,
        gasRecords: List<MeterRecord>,
        notesRecords: List<MeterRecord>,
        costEngine: CostEngine
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

        val tagCounts = mutableMapOf<String, Int>()
        notesRecords.forEach { record ->
            record.note?.split(Regex("\\s+"))?.filter { it.isNotBlank() }?.forEach {
                tagCounts[it] = (tagCounts[it] ?: 0) + 1
            }
        }
        val topNotes = tagCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        val billingRules = BillingRules()
        val tierLevel = when {
            kwh > billingRules.electricTier2Limit -> "三档"
            kwh > billingRules.electricTier1Limit -> "二档"
            else -> "一档"
        }

        return ReportData(
            yearMonth = "${now.year}-${now.monthValue}",
            electricKwh = kwh,
            electricCost = bill.electricTotalCost,
            peakKwh = peakKwh,
            valleyKwh = valleyKwh,
            waterTons = waterTons,
            waterCost = bill.waterTotalCost,
            gasM3 = gasM3,
            totalCost = bill.totalCost,
            dailyAvgKwh = kwh / days,
            dailyAvgCost = bill.totalCost / days,
            peakPrice = bill.peakPrice,
            valleyPrice = bill.valleyPrice,
            tierLevel = tierLevel,
            recordCount = elecMonth.size,
            topNotes = topNotes
        )
    }
}
