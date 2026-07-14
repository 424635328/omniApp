package com.example.energyflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * WeatherInterpolator 全面单元测试。
 *
 * 覆盖场景：
 * - 空输入、单点、全精确匹配
 * - 双向线性插值、单向近邻外推
 * - 降水/天气码插值、无效温度过滤
 * - 大跨度日期、混合场景
 */
class WeatherInterpolatorTest {

    // ── 辅助工厂 ──

    private fun w(
        date: String,
        tempMax: Double,
        tempMin: Double,
        textDay: String = "",
        weatherCode: Int? = null,
        precipitation: Double? = null
    ) = DailyWeather(
        date = LocalDate.parse(date),
        tempMax = tempMax,
        tempMin = tempMin,
        textDay = textDay,
        weatherCode = weatherCode,
        precipitation = precipitation
    )

    // ── 边界：空输入 ──

    @Test
    fun `empty weather returns empty map`() {
        val result = WeatherInterpolator.interpolate(
            weatherData = emptyList(),
            targetDates = listOf(LocalDate.of(2026, 7, 14))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty target dates returns empty map`() {
        val result = WeatherInterpolator.interpolate(
            weatherData = listOf(w("2026-07-14", 32.0, 24.0)),
            targetDates = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `both empty returns empty map`() {
        val result = WeatherInterpolator.interpolate(
            weatherData = emptyList(),
            targetDates = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    // ── 全精确匹配 ──

    @Test
    fun `all exact matches returns same data`() {
        val data = listOf(
            w("2026-07-14", 32.0, 24.0, "晴", 0),
            w("2026-07-15", 33.0, 25.0, "多云", 2),
            w("2026-07-16", 31.0, 23.0, "小雨", 61, 5.0)
        )
        val dates = data.map { it.date }
        val result = WeatherInterpolator.interpolate(data, dates)

        assertEquals(3, result.size)
        assertEquals(32.0, result[dates[0]]!!.tempMax, 0.001)
        assertEquals(33.0, result[dates[1]]!!.tempMax, 0.001)
        assertEquals("小雨", result[dates[2]]?.textDay)
        assertEquals(61, result[dates[2]]?.weatherCode)
        assertEquals(5.0, result[dates[2]]!!.precipitation!!, 0.001)
    }

    // ── 线性插值 ──

    @Test
    fun `linear interpolation between two known points`() {
        val data = listOf(
            w("2026-07-14", 30.0, 20.0),
            w("2026-07-16", 40.0, 30.0)
        )
        val dates = listOf(
            LocalDate.of(2026, 7, 14),
            LocalDate.of(2026, 7, 15),  // 中间插值
            LocalDate.of(2026, 7, 16)
        )
        val result = WeatherInterpolator.interpolate(data, dates)

        assertEquals(3, result.size)
        // 7月15日: 30 + (40-30) * 0.5 = 35.0
        assertEquals(35.0, result[dates[1]]!!.tempMax, 0.001)
        // 7月15日: 20 + (30-20) * 0.5 = 25.0
        assertEquals(25.0, result[dates[1]]!!.tempMin, 0.001)
        // 边界点不变
        assertEquals(30.0, result[dates[0]]!!.tempMax, 0.001)
        assertEquals(40.0, result[dates[2]]!!.tempMax, 0.001)
    }

    @Test
    fun `interpolation with multiple gaps`() {
        val data = listOf(
            w("2026-07-10", 28.0, 18.0),
            w("2026-07-13", 34.0, 24.0),
            w("2026-07-16", 38.0, 28.0)
        )
        val dates = (10..16).map { LocalDate.of(2026, 7, it) }
        val result = WeatherInterpolator.interpolate(data, dates)

        assertEquals(7, result.size)
        // 7月11日: 28 + (34-28) * 1/3 = 30.0
        assertEquals(30.0, result[dates[1]]!!.tempMax, 0.001)
        // 7月12日: 28 + (34-28) * 2/3 = 32.0
        assertEquals(32.0, result[dates[2]]!!.tempMax, 0.001)
        // 7月14日: 34 + (38-34) * 1/3 = 35.33...
        assertEquals(35.333, result[dates[4]]!!.tempMax, 0.01)
        // 7月15日: 34 + (38-34) * 2/3 = 36.66...
        assertEquals(36.667, result[dates[5]]!!.tempMax, 0.01)
    }

    // ── 外推（extrapolation）──

    @Test
    fun `target before all known dates uses first value`() {
        val data = listOf(
            w("2026-07-14", 32.0, 24.0),
            w("2026-07-15", 33.0, 25.0)
        )
        val dates = listOf(
            LocalDate.of(2026, 7, 12),  // 外推
            LocalDate.of(2026, 7, 13),  // 外推
            LocalDate.of(2026, 7, 14),
            LocalDate.of(2026, 7, 15)
        )
        val result = WeatherInterpolator.interpolate(data, dates)

        assertEquals(4, result.size)
        assertEquals(32.0, result[dates[0]]!!.tempMax, 0.001)  // = 7月14日
        assertEquals(32.0, result[dates[1]]!!.tempMax, 0.001)  // = 7月14日
    }

    @Test
    fun `target after all known dates uses last value`() {
        val data = listOf(
            w("2026-07-14", 32.0, 24.0),
            w("2026-07-15", 33.0, 25.0)
        )
        val dates = listOf(
            LocalDate.of(2026, 7, 14),
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 7, 16),  // 外推
            LocalDate.of(2026, 7, 17)   // 外推
        )
        val result = WeatherInterpolator.interpolate(data, dates)

        assertEquals(4, result.size)
        assertEquals(33.0, result[dates[2]]!!.tempMax, 0.001)  // = 7月15日
        assertEquals(33.0, result[dates[3]]!!.tempMax, 0.001)  // = 7月15日
    }

    // ── 单数据点 ──

    @Test
    fun `single known data point used for all targets`() {
        val data = listOf(
            w("2026-07-14", 30.0, 20.0)
        )
        val dates = (12..16).map { LocalDate.of(2026, 7, it) }
        val result = WeatherInterpolator.interpolate(data, dates)

        assertEquals(5, result.size)
        result.values.forEach {
            assertEquals(30.0, it.tempMax, 0.001)
            assertEquals(20.0, it.tempMin, 0.001)
        }
    }

    // ── 混合场景 ──

    @Test
    fun `mixed exact interpolation and extrapolation`() {
        val data = listOf(
            w("2026-07-14", 30.0, 20.0, "晴", 0),
            w("2026-07-16", 40.0, 30.0, "多云", 2)
        )
        val dates = listOf(
            LocalDate.of(2026, 7, 13),  // 外推（前）
            LocalDate.of(2026, 7, 14),  // 精确
            LocalDate.of(2026, 7, 15),  // 插值
            LocalDate.of(2026, 7, 16),  // 精确
            LocalDate.of(2026, 7, 17)   // 外推（后）
        )
        val result = WeatherInterpolator.interpolate(data, dates)

        assertEquals(5, result.size)
        assertEquals(30.0, result[dates[0]]!!.tempMax, 0.001)  // 外推
        assertEquals(30.0, result[dates[1]]!!.tempMax, 0.001)  // 精确
        assertEquals(35.0, result[dates[2]]!!.tempMax, 0.001)  // 插值
        assertEquals(40.0, result[dates[3]]!!.tempMax, 0.001)  // 精确
        assertEquals(40.0, result[dates[4]]!!.tempMax, 0.001)  // 外推
    }

    // ── 无效温度过滤 ──

    @Test
    fun `invalid temperatures are filtered out`() {
        val data = listOf(
            w("2026-07-14", -999.0, 20.0),  // 无效最高温
            w("2026-07-15", 32.0, -999.0),  // 无效最低温
            w("2026-07-16", 33.0, 25.0)     // 有效
        )
        val dates = listOf(
            LocalDate.of(2026, 7, 16)
        )
        val result = WeatherInterpolator.interpolate(data, dates)

        assertEquals(1, result.size)
        assertEquals(33.0, result[dates[0]]!!.tempMax, 0.001)
    }

    @Test
    fun `all invalid temperatures returns empty`() {
        val data = listOf(
            w("2026-07-14", -999.0, -999.0),
            w("2026-07-15", -1000.0, -1000.0)
        )
        val dates = listOf(LocalDate.of(2026, 7, 14))
        val result = WeatherInterpolator.interpolate(data, dates)

        assertTrue(result.isEmpty())
    }

    // ── 降水插值 ──

    @Test
    fun `precipitation interpolates between known values`() {
        val data = listOf(
            w("2026-07-14", 30.0, 20.0, precipitation = 0.0),
            w("2026-07-16", 32.0, 22.0, precipitation = 10.0)
        )
        val dates = (14..16).map { LocalDate.of(2026, 7, it) }
        val result = WeatherInterpolator.interpolate(data, dates)

        assertNotNull(result[dates[0]])
        assertNotNull(result[dates[1]])

        // 7月15日插值降水: 0 + (10-0) * 0.5 = 5.0
        assertEquals(0.0, result[dates[0]]!!.precipitation!!, 0.001)
        assertEquals(5.0, result[dates[1]]!!.precipitation!!, 0.001)
        assertEquals(10.0, result[dates[2]]!!.precipitation!!, 0.001)
    }

    @Test
    fun `precipitation null when both endpoints null`() {
        val data = listOf(
            w("2026-07-14", 30.0, 20.0, precipitation = null),
            w("2026-07-16", 32.0, 22.0, precipitation = null)
        )
        val dates = (14..16).map { LocalDate.of(2026, 7, it) }
        val result = WeatherInterpolator.interpolate(data, dates)

        result.values.forEach {
            assertEquals(null, it.precipitation)
        }
    }

    // ── 天气码/描述外推 ──

    @Test
    fun `weather text and code from nearest neighbor`() {
        val data = listOf(
            w("2026-07-14", 30.0, 20.0, "晴", 0),
            w("2026-07-20", 35.0, 25.0, "雷暴", 95)
        )
        val dates = listOf(
            LocalDate.of(2026, 7, 14),
            LocalDate.of(2026, 7, 15),  // 靠近14日 -> "晴"
            LocalDate.of(2026, 7, 18),  // 靠近20日 -> "雷暴"
            LocalDate.of(2026, 7, 20)
        )
        val result = WeatherInterpolator.interpolate(data, dates)

        assertEquals(4, result.size)
        assertEquals("晴", result[dates[0]]?.textDay)
        assertEquals("晴", result[dates[1]]?.textDay)    // f=0.166 < 0.5
        assertEquals("雷暴", result[dates[2]]?.textDay)  // f=0.666 > 0.5
        assertEquals("雷暴", result[dates[3]]?.textDay)

        assertEquals(0, result[dates[0]]?.weatherCode)
        assertEquals(0, result[dates[1]]?.weatherCode)
        assertEquals(95, result[dates[2]]?.weatherCode)
        assertEquals(95, result[dates[3]]?.weatherCode)
    }

    // ── 大跨度日期 ──

    @Test
    fun `large span with few known points`() {
        val data = listOf(
            w("2026-01-01", 5.0, -2.0),
            w("2026-07-01", 32.0, 24.0),
            w("2026-12-31", 8.0, 1.0)
        )
        val dates = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 4, 1),   // 插值
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 10, 1),  // 插值
            LocalDate.of(2026, 12, 31)
        )
        val result = WeatherInterpolator.interpolate(data, dates)

        assertEquals(5, result.size)
        assertNotNull(result[dates[1]])
        assertNotNull(result[dates[3]])

        // 4月1日: 从1月1日到7月1日插值
        // fraction = (4月1日 - 1月1日) / (7月1日 - 1月1日) = 91/181 ≈ 0.5028
        // tempMax = 5 + (32-5) * 0.5028 = 5 + 13.5756 ≈ 18.576
        assertTrue(result[dates[1]]!!.tempMax > 15.0)
        assertTrue(result[dates[1]]!!.tempMax < 22.0)
    }

    // ── 非连续目标日期 ──

    @Test
    fun `non-consecutive target dates`() {
        val data = listOf(
            w("2026-07-10", 28.0, 18.0),
            w("2026-07-20", 38.0, 28.0)
        )
        val dates = listOf(
            LocalDate.of(2026, 7, 10),
            LocalDate.of(2026, 7, 15),  // 插值
            LocalDate.of(2026, 7, 20)
        )
        val result = WeatherInterpolator.interpolate(data, dates)

        assertEquals(3, result.size)
        assertEquals(28.0, result[dates[0]]!!.tempMax, 0.001)
        // 7月15日: 28 + (38-28) * 5/10 = 33.0
        assertEquals(33.0, result[dates[1]]!!.tempMax, 0.001)
        assertEquals(38.0, result[dates[2]]!!.tempMax, 0.001)
    }
}
