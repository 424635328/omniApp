package com.example.energyflow.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedEnginesTest {
    @Test
    fun `cost engine handles zero usage without division by zero`() {
        val result = CostEngineShared.calculate(
            rules = BillingRules(),
            totalKwh = 0.0,
            waterTons = 0.0
        )

        assertEquals(0.0, result.electricTotalCost)
        assertEquals(0.0, result.waterTotalCost)
        assertEquals(0.0, result.totalCost)
    }

    @Test
    fun `carbon badges detect streak across year boundary`() {
        val badges = CarbonCalculator.badgesFromRecords(
            listOf(
                YearMonthStat("2025-12", 100.0, 0.0, 0.0),
                YearMonthStat("2026-01", 100.0, 0.0, 0.0),
                YearMonthStat("2026-02", 100.0, 0.0, 0.0)
            )
        )

        assertTrue(GreenBadge.FIRST_STEP in badges)
        assertTrue(GreenBadge.STREAK_3 in badges)
    }

    @Test
    fun `wrapped report uses zero average when day count is zero`() {
        val report = WrappedReportBuilder.build(
            yearOrMonth = "2026-07",
            totalKwh = 12.0,
            totalCost = 4.0,
            peakKwh = 8.0,
            valleyKwh = 4.0,
            carbonResult = CarbonCalculator.calculate(kwh = 12.0, gasM3 = 0.0),
            badges = emptyList(),
            dayCount = 0
        )

        assertEquals(0.0, report.avgDailyKwh)
    }
}
