package com.example.energyflow.shared

/**
 * 封装报告数据，用于生成年度/月度总结报告。
 */
data class WrappedReportData(
    val yearOrMonth: String,
    val totalKwh: Double,
    val totalCost: Double,
    val carbonKg: Double,
    val treeDays: Int,
    val peakKwh: Double,
    val valleyKwh: Double,
    val avgDailyKwh: Double,
    val badges: List<GreenBadge>,
    val insightText: String
)

/**
 * 根据聚合数据构建报告。
 */
object WrappedReportBuilder {

    /**
     * 构建一份封装报告。
     *
     * @param yearOrMonth 报告期，如 "2026" 或 "2026-07"
     * @param totalKwh 总用电量
     * @param totalCost 总费用
     * @param peakKwh 峰电量
     * @param valleyKwh 谷电量
     * @param carbonResult 碳足迹结果
     * @param badges 获得的绿色徽章
     * @param dayCount 统计天数
     * @param insightText 洞察文字
     */
    fun build(
        yearOrMonth: String,
        totalKwh: Double,
        totalCost: Double,
        peakKwh: Double,
        valleyKwh: Double,
        carbonResult: CarbonResult,
        badges: List<GreenBadge>,
        dayCount: Int,
        insightText: String = ""
    ): WrappedReportData {
        val avgDaily = if (dayCount > 0) totalKwh / dayCount else 0.0

        return WrappedReportData(
            yearOrMonth = yearOrMonth,
            totalKwh = totalKwh,
            totalCost = totalCost,
            carbonKg = carbonResult.totalKgCO2,
            treeDays = carbonResult.treeDays,
            peakKwh = peakKwh,
            valleyKwh = valleyKwh,
            avgDailyKwh = avgDaily,
            badges = badges,
            insightText = insightText
        )
    }
}
