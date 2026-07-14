package com.example.energyflow.data

import io.ktor.client.HttpClient
import io.mockk.mockk
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * WeatherRepository.parseDailyResponse 全面单元测试。
 *
 * 使用 MockK 模拟 HttpClient（parseDailyResponse 只依赖传入的 JSON 字符串，
 * 不需要真实的 HTTP 调用）。
 *
 * 覆盖场景：
 * - 成功解析全部字段（温度、天气码、降水）
 * - WMO 天气码 → 中文描述映射
 * - API 错误响应检测
 * - 缺失字段、空数组、数组长度不一致
 * - 无效日期和无效温度跳过
 * - 部分字段为 null（同时不传）
 */
class WeatherRepositoryTest {

    private lateinit var repository: WeatherRepository

    @Before
    fun setUp() {
        // HttpClient 不会被使用，只需满足构造函数签名
        repository = WeatherRepository(mockk<HttpClient>(relaxed = true))
    }

    // ── 辅助：快速构建 JSON ──

    private fun dailyJson(
        times: List<String> = listOf("2026-07-14"),
        maxTemps: List<Double?>? = listOf(32.0),
        minTemps: List<Double?>? = listOf(24.0),
        weatherCodes: List<Int?>? = listOf(0),
        precipitations: List<Double?>? = listOf(0.0)
    ): String {
        val maxStr = maxTemps?.joinToString(",") { it?.toString() ?: "null" } ?: "null"
        val minStr = minTemps?.joinToString(",") { it?.toString() ?: "null" } ?: "null"
        val codeStr = weatherCodes?.joinToString(",") { it?.toString() ?: "null" } ?: "null"
        val precStr = precipitations?.joinToString(",") { it?.toString() ?: "null" } ?: "null"
        val timeStr = times.joinToString(",") { "\"$it\"" }

        return """
        {
            "daily": {
                "time": [$timeStr],
                "temperature_2m_max": [$maxStr],
                "temperature_2m_min": [$minStr],
                "weathercode": [$codeStr],
                "precipitation_sum": [$precStr]
            }
        }
        """.trimIndent()
    }

    // ── 成功解析 ──

    @Test
    fun `parse valid response with all fields`() {
        val json = dailyJson()
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Success)
        val data = (result as WeatherResult.Success).data
        assertEquals(1, data.size)
        assertEquals(LocalDate.of(2026, 7, 14), data[0].date)
        assertEquals(32.0, data[0].tempMax, 0.001)
        assertEquals(24.0, data[0].tempMin, 0.001)
        assertEquals("晴", data[0].textDay)
        assertEquals(0, data[0].weatherCode)
        assertEquals(0.0, data[0].precipitation!!, 0.001)
    }

    @Test
    fun `parse multiple days correctly`() {
        val json = dailyJson(
            times = listOf("2026-07-14", "2026-07-15", "2026-07-16"),
            maxTemps = listOf(30.0, 33.0, 35.0),
            minTemps = listOf(20.0, 23.0, 25.0),
            weatherCodes = listOf(0, 2, 61),
            precipitations = listOf(0.0, 0.0, 5.0)
        )
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Success)
        val data = (result as WeatherResult.Success).data
        assertEquals(3, data.size)

        assertEquals(LocalDate.of(2026, 7, 14), data[0].date)
        assertEquals(30.0, data[0].tempMax, 0.001)
        assertEquals("晴", data[0].textDay)

        assertEquals(LocalDate.of(2026, 7, 15), data[1].date)
        assertEquals(33.0, data[1].tempMax, 0.001)
        assertEquals("多云", data[1].textDay)

        assertEquals(LocalDate.of(2026, 7, 16), data[2].date)
        assertEquals(35.0, data[2].tempMax, 0.001)
        assertEquals("小雨", data[2].textDay)
        assertEquals(61, data[2].weatherCode)
        assertEquals(5.0, data[2].precipitation!!, 0.001)
    }

    // ── WMO 天气码映射 ──

    @Test
    fun `wmo code 0 maps to clear`() {
        val json = dailyJson(weatherCodes = listOf(0))
        val data = (repository.parseDailyResponse(json) as WeatherResult.Success).data
        assertEquals("晴", data[0].textDay)
    }

    @Test
    fun `wmo code 61 maps to light rain`() {
        val json = dailyJson(weatherCodes = listOf(61))
        val data = (repository.parseDailyResponse(json) as WeatherResult.Success).data
        assertEquals("小雨", data[0].textDay)
    }

    @Test
    fun `wmo code 95 maps to thunderstorm`() {
        val json = dailyJson(weatherCodes = listOf(95))
        val data = (repository.parseDailyResponse(json) as WeatherResult.Success).data
        assertEquals("雷暴", data[0].textDay)
    }

    @Test
    fun `unknown wmo code returns empty text`() {
        val json = dailyJson(weatherCodes = listOf(999))
        val data = (repository.parseDailyResponse(json) as WeatherResult.Success).data
        assertEquals("", data[0].textDay)
    }

    @Test
    fun `null weather code returns empty text`() {
        val json = dailyJson(weatherCodes = listOf(null as Int?))
        val data = (repository.parseDailyResponse(json) as WeatherResult.Success).data
        assertEquals("", data[0].textDay)
        assertNull(data[0].weatherCode)
    }

    // ── 错误响应检测 ──

    @Test
    fun `api error response returns error`() {
        val json = """
        {
            "error": true,
            "reason": "Invalid parameter. Latitude must be between -90 and 90."
        }
        """.trimIndent()
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Error)
        assertTrue((result as WeatherResult.Error).message.contains("Latitude"))
    }

    @Test
    fun `api error without reason returns generic error`() {
        val json = """{"error": true}""".trimIndent()
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Error)
        assertTrue((result as WeatherResult.Error).message.contains("Open-Meteo"))
    }

    // ── 缺失字段 ──

    @Test
    fun `missing daily field returns error`() {
        val json = """{}""".trimIndent()
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Error)
        assertTrue((result as WeatherResult.Error).message.contains("daily"))
    }

    @Test
    fun `empty time array returns error`() {
        val json = """
        {
            "daily": {
                "time": [],
                "temperature_2m_max": [],
                "temperature_2m_min": [],
                "weathercode": [],
                "precipitation_sum": []
            }
        }
        """.trimIndent()
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Error)
        assertTrue((result as WeatherResult.Error).message.contains("无天气日期数据"))
    }

    @Test
    fun `missing temperature fields causes all rows to be skipped`() {
        val json = """
        {
            "daily": {
                "time": ["2026-07-14"]
            }
        }
        """.trimIndent()
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Error)
        assertTrue((result as WeatherResult.Error).message.contains("有效"))
    }

    // ── 数组长度不一致 ──

    @Test
    fun `mismatched array lengths returns error`() {
        val json = dailyJson(
            times = listOf("2026-07-14", "2026-07-15"),
            maxTemps = listOf(30.0)  // 少了1个
        )
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Error)
        assertTrue((result as WeatherResult.Error).message.contains("长度不一致"))
    }

    // ── 空值处理 ──

    @Test
    fun `null temperatures cause that day to be skipped`() {
        val json = dailyJson(
            times = listOf("2026-07-14", "2026-07-15"),
            maxTemps = listOf(30.0, null),
            minTemps = listOf(20.0, 22.0),
            weatherCodes = listOf(0, 2),
            precipitations = listOf(0.0, 0.0)
        )
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Success)
        val data = (result as WeatherResult.Success).data
        assertEquals(1, data.size)  // 7月15日被跳过
        assertEquals(LocalDate.of(2026, 7, 14), data[0].date)
    }

    // ── 无效日期 ──

    @Test
    fun `invalid date is skipped gracefully`() {
        val json = dailyJson(
            times = listOf("not-a-date", "2026-07-14"),
            maxTemps = listOf(30.0, 32.0),
            minTemps = listOf(20.0, 24.0),
            weatherCodes = listOf(0, 2),
            precipitations = listOf(0.0, 0.0)
        )
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Success)
        val data = (result as WeatherResult.Success).data
        assertEquals(1, data.size)
        assertEquals(LocalDate.of(2026, 7, 14), data[0].date)
    }

    // ── 无降水字段 ──

    @Test
    fun `response without precipitation works`() {
        val json = """
        {
            "daily": {
                "time": ["2026-07-14"],
                "temperature_2m_max": [32.0],
                "temperature_2m_min": [24.0],
                "weathercode": [0]
            }
        }
        """.trimIndent()
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Success)
        val data = (result as WeatherResult.Success).data
        assertEquals(1, data.size)
        assertNull(data[0].precipitation)
    }

    @Test
    fun `response without weathercode works`() {
        val json = """
        {
            "daily": {
                "time": ["2026-07-14"],
                "temperature_2m_max": [32.0],
                "temperature_2m_min": [24.0]
            }
        }
        """.trimIndent()
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Success)
        val data = (result as WeatherResult.Success).data
        assertEquals(1, data.size)
        assertNull(data[0].weatherCode)
        assertEquals("", data[0].textDay)
    }

    // ── 边缘：边界温度值 ──

    @Test
    fun `extreme temperatures are accepted`() {
        val json = dailyJson(
            maxTemps = listOf(45.0),   // 高温
            minTemps = listOf(-15.0)   // 低温
        )
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Success)
        val data = (result as WeatherResult.Success).data
        assertEquals(45.0, data[0].tempMax, 0.001)
        assertEquals(-15.0, data[0].tempMin, 0.001)
    }

    // ── 混合：部分字段有值，部分缺失 ──

    @Test
    fun `mix of valid and null fields across multiple days`() {
        val json = dailyJson(
            times = listOf("2026-07-14", "2026-07-15", "2026-07-16"),
            maxTemps = listOf(30.0, null, 35.0),
            minTemps = listOf(20.0, 22.0, 25.0),
            weatherCodes = listOf(0, 61, null),
            precipitations = listOf(null, 5.0, 0.0)
        )
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Success)
        val data = (result as WeatherResult.Success).data
        assertEquals(2, data.size)  // 7月15日（null maxTemp）被跳过

        // 7月14日
        assertEquals(LocalDate.of(2026, 7, 14), data[0].date)
        assertEquals(30.0, data[0].tempMax, 0.001)
        assertEquals("晴", data[0].textDay)
        assertNull(data[0].precipitation)

        // 7月16日
        assertEquals(LocalDate.of(2026, 7, 16), data[1].date)
        assertEquals(35.0, data[1].tempMax, 0.001)
        assertEquals("", data[1].textDay)   // weatherCode is null
        assertEquals(0.0, data[1].precipitation!!, 0.001)
    }

    // ── 全部无效数据 ──

    @Test
    fun `all null temperatures returns error`() {
        val json = dailyJson(
            times = listOf("2026-07-14", "2026-07-15"),
            maxTemps = listOf(null as Double?, null as Double?),
            minTemps = listOf(null as Double?, null as Double?),
            weatherCodes = listOf(null as Int?, null as Int?),
            precipitations = listOf(null as Double?, null as Double?)
        )
        val result = repository.parseDailyResponse(json)

        assertTrue(result is WeatherResult.Error)
        assertTrue((result as WeatherResult.Error).message.contains("有效"))
    }
}
