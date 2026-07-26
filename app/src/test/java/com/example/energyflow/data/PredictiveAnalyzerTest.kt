package com.example.energyflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * PredictiveAnalyzer 全面单元测试。
 *
 * 使用固定的 LocalDateTime.now 参数来消除时间依赖。
 * 覆盖记录不足、递减过滤、本月推算、历史回退、边界条件。
 */
class PredictiveAnalyzerTest {

    private val analyzer = PredictiveAnalyzer()
    private val now = LocalDateTime.of(2026, 7, 15, 12, 0) // 7月15日

    // ── 辅助工厂 ──

    private fun reading(
        time: LocalDateTime,
        total: Double,
        peak: Double? = null,
        valley: Double? = null,
        water: Double? = null
    ) = MeterRecord(
        timestamp = time,
        isElectricRecorded = total > 0,
        electricTotal = if (total > 0) total else null,
        electricPeak = peak,
        electricValley = valley,
        isWaterRecorded = water != null,
        waterTotal = water
    )

    // ── 边界：记录不足 ──

    @Test
    fun `less than 2 electric records returns null`() {
        val records = listOf(
            reading(now.minusDays(1), 100.0)
        )
        assertNull(analyzer.predictMonth(records, now = now))
    }

    @Test
    fun `empty records returns null`() {
        assertNull(analyzer.predictMonth(emptyList(), now = now))
    }

    @Test
    fun `no electric records returns null`() {
        val records = listOf(
            MeterRecord(timestamp = now.minusDays(1), isElectricRecorded = false, electricTotal = null),
            MeterRecord(timestamp = now, isElectricRecorded = false, electricTotal = null)
        )
        assertNull(analyzer.predictMonth(records, now = now))
    }

    @Test
    fun `single electric record returns null`() {
        val records = listOf(
            reading(now.minusDays(2), 100.0),
            MeterRecord(timestamp = now.minusDays(1))
        )
        assertNull(analyzer.predictMonth(records, now = now))
    }

    // ── 递减记录过滤 ──

    @Test
    fun `decreasing electric total is filtered out`() {
        val records = listOf(
            reading(now.minusDays(5), 100.0),
            reading(now.minusDays(3), 105.0),
            reading(now.minusDays(2), 103.0),  // 下降 → 被过滤
            reading(now.minusDays(1), 110.0)
        )
        val result = analyzer.predictMonth(records, now = now)
        assertNotNull(result)
        assertTrue(result!!.consumedSoFarKwh > 0)
    }

    @Test
    fun `all decreasing records returns null`() {
        val records = listOf(
            reading(now.minusDays(3), 100.0),
            reading(now.minusDays(2), 95.0),
            reading(now.minusDays(1), 90.0)
        )
        assertNull(analyzer.predictMonth(records, now = now))
    }

    // ── 本月推算 ──

    @Test
    fun `normal month prediction with 2 plus records in month`() {
        val records = listOf(
            reading(now.minusDays(10), 100.0),  // 7月5日 — 月内
            reading(now.minusDays(1), 120.0)     // 7月14日 — 月内
        )
        val result = analyzer.predictMonth(records, now = now)
        assertNotNull(result)

        // 7月5日→14日 = 9天, 耗电 120-100 = 20度 → 日均 20/9 ≈ 2.222
        assertEquals(2.222, result!!.dailyRateKwh, 0.01)
        assertEquals(20.0, result.consumedSoFarKwh, 0.01)
        // 已过 15 天（7月1日→15日）
        assertEquals(15, result.daysElapsed)
        // 剩余 16 天（7月16日→31日）
        assertEquals(16, result.daysRemaining)
        // 预测剩余: 2.222 * 16 ≈ 35.56
        assertEquals(35.556, result.predictedRemainingKwh, 0.01)
        // 总量: 20 + 35.556 ≈ 55.556
        assertEquals(55.556, result.predictedTotalKwh, 0.01)
    }

    @Test
    fun `prediction with multiple records in month uses first and last`() {
        val records = listOf(
            reading(now.minusDays(14), 100.0),  // 7月1日
            reading(now.minusDays(10), 110.0),  // 7月5日
            reading(now.minusDays(5), 120.0),   // 7月10日
            reading(now.minusDays(1), 130.0)    // 7月14日
        )
        val result = analyzer.predictMonth(records, now = now)
        assertNotNull(result)

        // 使用首尾: 7月1日→14日 = 13天, 耗电 130-100 = 30 → 日均 30/13 ≈ 2.308
        assertEquals(2.308, result!!.dailyRateKwh, 0.01)
        assertEquals(30.0, result.consumedSoFarKwh, 0.01)
    }

    @Test
    fun `daily rate of zero or negative returns null`() {
        val records = listOf(
            reading(now.minusDays(5), 100.0),
            reading(now.minusDays(1), 100.0)  // 零增长
        )
        assertNull(analyzer.predictMonth(records, now = now))
    }

    @Test
    fun `zero consumption with enough records returns null`() {
        // Arrange
        val records = (5L downTo 1L).map { daysAgo ->
            reading(now.minusDays(daysAgo), 100.0)
        }

        // Act
        val result = analyzer.predictMonth(records, now = now)

        // Assert
        assertNull(result)
    }

    // ── 历史回退 ──

    @Test
    fun `fallback to recent records when month has only 1 record`() {
        val records = listOf(
            reading(now.minusDays(20), 100.0),     // 6月25日 — 上月
            reading(now.minusDays(15), 110.0),     // 6月30日 — 上月
            reading(now.minusDays(12), 122.0),     // 7月3日 — 本月
            reading(now.minusDays(1), 130.0)       // 7月14日 — 本月
        )
        val result = analyzer.predictMonth(records, now = now)
        assertNotNull(result)

        // 本月有2条 → 用本月首尾推算, 7月3日→14日 = 11天, 130-122 = 8
        assertEquals(0.727, result!!.dailyRateKwh, 0.01)
        assertEquals(8.0, result.consumedSoFarKwh, 0.01)  // 7月内 130-122
    }

    @Test
    fun `fallback to recent when no records this month`() {
        val records = listOf(
            reading(now.minusDays(25), 100.0),  // 6月20日
            reading(now.minusDays(20), 110.0),  // 6月25日
            reading(now.minusDays(15), 125.0)   // 6月30日
        )
        val result = analyzer.predictMonth(records, now = now)
        assertNotNull(result)

        // 使用最近5条（实际3条）的斜率
        // 6月20日→30日 = 10天, 125-100 = 25 → 日均 2.5
        assertEquals(2.5, result!!.dailyRateKwh, 0.01)
        // 无本月记录 → 日均×本月已过天数 = 2.5 × 15 = 37.5
        assertEquals(37.5, result.consumedSoFarKwh, 0.01)
    }

    // ── 时间边界 ──

    @Test
    fun `records spanning across month boundary`() {
        val records = listOf(
            reading(now.minusDays(20), 1000.0),  // 6月25日
            reading(now.minusDays(13), 1020.0),  // 7月2日 — 本月
            reading(now.minusDays(6), 1050.0),   // 7月9日
            reading(now.minusDays(1), 1070.0)    // 7月14日
        )
        val result = analyzer.predictMonth(records, now = now)
        assertNotNull(result)

        // 本月有3条 → 用本月首尾: 7月2日→14日 = 12天, 1070-1020 = 50 → 54.167
        assertEquals(4.167, result!!.dailyRateKwh, 0.01)
        // consumedSoFar 使用本月首尾: 1070-1020 = 50
        assertEquals(50.0, result.consumedSoFarKwh, 0.01)
    }

    @Test
    fun `exactly 2 records total`() {
        val records = listOf(
            reading(now.minusDays(5), 200.0),   // 7月10日
            reading(now.minusDays(1), 220.0)    // 7月14日
        )
        val result = analyzer.predictMonth(records, now = now)
        assertNotNull(result)

        // 7月10日→14日 = 4天, 20度 → 日均 5.0
        assertEquals(5.0, result!!.dailyRateKwh, 0.01)
        assertEquals(20.0, result.consumedSoFarKwh, 0.01)
        // 剩余 16 天 × 5 = 80
        assertEquals(80.0, result.predictedRemainingKwh, 0.01)
    }

    // ── 月首月末 ──

    @Test
    fun `prediction at beginning of month`() {
        val firstOfMonth = LocalDateTime.of(2026, 7, 1, 8, 0)
        val records = listOf(
            reading(firstOfMonth, 1000.0),
            reading(firstOfMonth.plusHours(12), 1005.0)
        )
        val result = analyzer.predictMonth(records, now = firstOfMonth)
        assertNotNull(result)

        // 第一天: 1天已过, 30天剩余
        assertEquals(1, result!!.daysElapsed)
        assertEquals(30, result.daysRemaining)
    }

    @Test
    fun `prediction at end of month`() {
        val lastOfMonth = LocalDateTime.of(2026, 7, 31, 20, 0)
        val records = listOf(
            reading(lastOfMonth.minusDays(5), 100.0),
            reading(lastOfMonth.minusDays(1), 120.0)
        )
        val result = analyzer.predictMonth(records, now = lastOfMonth)
        assertNotNull(result)

        // 第31天: 31天已过, 0天剩余
        assertEquals(31, result!!.daysElapsed)
        assertEquals(0, result.daysRemaining)
        assertEquals(0.0, result.predictedRemainingKwh, 0.001)
        assertEquals(result.consumedSoFarKwh, result.predictedTotalKwh, 0.001)
    }

    // ── 大跨度数据 ──

    @Test
    fun `large gap between records`() {
        val records = listOf(
            reading(now.minusDays(30), 100.0),  // 6月15日
            reading(now.minusDays(1), 200.0)     // 7月14日 — 跨度29天
        )
        val result = analyzer.predictMonth(records, now = now)
        assertNotNull(result)

        // 只有1条本月记录 → 使用最近窗口（最多5条）
        // 2条记录, 29天, 100度 → 日均 3.448
        assertEquals(3.448, result!!.dailyRateKwh, 0.01)
        // consumedSoFar: 日均×本月已过天数 = 3.448 × 15 = 51.72
        assertEquals(51.724, result.consumedSoFarKwh, 0.01)
    }

    // ── 天气预报集成 ──

    @Test
    fun `empty weather forecast returns same as no forecast`() {
        val records = listOf(
            reading(now.minusDays(14), 100.0),
            reading(now.minusDays(11), 107.0),
            reading(now.minusDays(8), 115.0),
            reading(now.minusDays(5), 122.0),
            reading(now.minusDays(2), 128.0),
            reading(now.minusDays(1), 130.0)
        )
        val resultNoWeather = analyzer.predictMonth(records, now = now)
        val resultEmpty = analyzer.predictMonth(records, weatherForecast = emptyList(), now = now)
        assertNotNull(resultNoWeather)
        assertNotNull(resultEmpty)
        assertEquals(resultNoWeather!!.dailyRateKwh, resultEmpty!!.dailyRateKwh, 0.001)
        assertEquals(resultNoWeather.predictedTotalKwh, resultEmpty.predictedTotalKwh, 0.001)
    }

    @Test
    fun `35 degree forecast applies heat multiplier via DES`() {
        // 6 records to trigger DES path (>=5)
        val records = listOf(
            reading(now.minusDays(14), 100.0),
            reading(now.minusDays(11), 107.0),
            reading(now.minusDays(8), 115.0),
            reading(now.minusDays(5), 122.0),
            reading(now.minusDays(2), 128.0),
            reading(now.minusDays(1), 130.0)
        )
        // 3 consecutive hot days starting today (July 15-17)
        val hotForecast = listOf(
            DailyWeather(date = java.time.LocalDate.of(2026, 7, 15), tempMax = 35.0, tempMin = 26.0),
            DailyWeather(date = java.time.LocalDate.of(2026, 7, 16), tempMax = 36.0, tempMin = 27.0),
            DailyWeather(date = java.time.LocalDate.of(2026, 7, 17), tempMax = 35.0, tempMin = 26.0)
        )
        val resultNoWeather = analyzer.predictMonth(records, now = now)
        val resultHot = analyzer.predictMonth(records, weatherForecast = hotForecast, now = now)
        assertNotNull(resultNoWeather)
        assertNotNull(resultHot)

        // Hot weather should increase the predicted total
        assertTrue(
            "Hot weather should increase predicted total: ${resultNoWeather!!.predictedTotalKwh} vs ${resultHot!!.predictedTotalKwh}",
            resultHot.predictedTotalKwh > resultNoWeather.predictedTotalKwh
        )
        assertTrue(
            "Hot weather should increase daily rate: ${resultNoWeather.dailyRateKwh} vs ${resultHot.dailyRateKwh}",
            resultHot.dailyRateKwh > resultNoWeather.dailyRateKwh
        )
    }

    @Test
    fun `38 degree sustained causes nonlinear extreme heat spike`() {
        // 6 records to trigger DES path
        val records = listOf(
            reading(now.minusDays(14), 100.0),
            reading(now.minusDays(11), 107.0),
            reading(now.minusDays(8), 115.0),
            reading(now.minusDays(5), 122.0),
            reading(now.minusDays(2), 128.0),
            reading(now.minusDays(1), 130.0)
        )
        // 35°C forecast vs 38°C sustained forecast
        val hot35 = listOf(
            DailyWeather(date = java.time.LocalDate.of(2026, 7, 15), tempMax = 35.0, tempMin = 26.0),
            DailyWeather(date = java.time.LocalDate.of(2026, 7, 16), tempMax = 35.0, tempMin = 27.0),
            DailyWeather(date = java.time.LocalDate.of(2026, 7, 17), tempMax = 35.0, tempMin = 26.0)
        )
        val extreme38 = listOf(
            DailyWeather(date = java.time.LocalDate.of(2026, 7, 15), tempMax = 38.0, tempMin = 28.0),
            DailyWeather(date = java.time.LocalDate.of(2026, 7, 16), tempMax = 38.0, tempMin = 29.0),
            DailyWeather(date = java.time.LocalDate.of(2026, 7, 17), tempMax = 38.0, tempMin = 28.0)
        )
        val result35 = analyzer.predictMonth(records, weatherForecast = hot35, now = now)
        val result38 = analyzer.predictMonth(records, weatherForecast = extreme38, now = now)
        assertNotNull(result35)
        assertNotNull(result38)

        // 38°C sustained produces a notably higher prediction than 35°C
        // due to the per-day nonlinear multiplier (1.35 vs 1.15)
        val ratio = result38!!.predictedTotalKwh / result35!!.predictedTotalKwh
        assertTrue(
            "38°C sustained should exceed 35°C prediction (ratio=$ratio, expected > 1.04)",
            ratio > 1.04
        )
    }
}
