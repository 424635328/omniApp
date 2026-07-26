package com.example.energyflow.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserPreferencesTest {

    @Before
    fun mockAndroidLog() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
    }

    /**
     * 创建按 key.name 返回值的 mock Preferences。
     * Preferences.Key 没有 equals 重写，不能用 stringPreferencesKey("x") 做 every 匹配。
     * 使用 firstArg<Preferences.Key<*>>().name 来按字符串名取值。
     */
    private fun prefsByKey(vararg pairs: Pair<String, Any?>): Preferences {
        val lookup = mapOf(*pairs)
        val p = mockk<Preferences>()
        every { p[any<Preferences.Key<*>>()] } answers {
            val keyName = firstArg<Preferences.Key<*>>().name
            lookup[keyName]  // 找不到返回 null
        }
        return p
    }

    /** 创建 DataStore mock，并为 dataStore.data 设置默认返回。 */
    private fun dataStoreWith(vararg pairs: Pair<String, Any?>): DataStore<Preferences> {
        val p = prefsByKey(*pairs)
        val ds = mockk<DataStore<Preferences>>(relaxed = true)
        coEvery { ds.data } returns flowOf(p)
        return ds
    }

    // ── 初始状态 ──

    @Test
    fun `default dark theme when no preference set`() = runTest {
        val prefs = UserPreferences(dataStoreWith())
        assertTrue(prefs.isDarkTheme.first())
    }

    @Test
    fun `chart show cost defaults to false`() = runTest {
        val prefs = UserPreferences(dataStoreWith())
        assertFalse(prefs.chartShowCost.first())
    }

    @Test
    fun `theme dist enabled defaults to true`() = runTest {
        val prefs = UserPreferences(dataStoreWith())
        assertTrue(prefs.themeDistEnabled.first())
    }

    @Test
    fun `weather api key defaults to legacy key`() = runTest {
        val prefs = UserPreferences(dataStoreWith())
        assertEquals("8e6f345a7e6041c7b046b049b8642a19", prefs.weatherApiKey.first())
    }

    // ── 计费规则 ──

    @Test
    fun `billing rules uses Nanjing defaults when version 0`() = runTest {
        val prefs = UserPreferences(dataStoreWith("billing_version" to 0))
        val rules = prefs.billingRules.first()
        assertEquals(0.5583, rules.peakPrice, 0.0001)
        assertEquals(230.0, rules.electricTier1Limit, 0.01)
    }

    @Test
    fun `billing rules uses saved values when version matches`() = runTest {
        val prefs = UserPreferences(dataStoreWith(
            "billing_version" to 3,
            "peak_price" to 0.6,
            "valley_price" to 0.35
        ))
        val rules = prefs.billingRules.first()
        assertEquals(0.6, rules.peakPrice, 0.001)
        assertEquals(0.35, rules.valleyPrice, 0.001)
    }

    @Test
    fun `migration resets billing rules to defaults`() = runTest {
        val prefs = UserPreferences(dataStoreWith(
            "billing_version" to 1,
            "peak_price" to 0.9
        ))
        val rules = prefs.billingRules.first()
        assertEquals(0.5583, rules.peakPrice, 0.0001)
    }

    // ── 阈值缓存 ──

    @Test
    fun `cached thresholds returns null when not set`() = runTest {
        val prefs = UserPreferences(dataStoreWith())
        assertNull(prefs.getCachedThresholds())
    }

    @Test
    fun `cached thresholds parses correctly`() = runTest {
        val prefs = UserPreferences(dataStoreWith(
            "th_total_electric_min" to 5000.0,
            "th_peak_min" to 3000.0,
            "th_peak_max" to 4000.0,
            "th_valley_min" to 2000.0,
            "th_valley_max" to 3000.0,
            "th_water_max" to 50.0
        ))
        val cached = prefs.getCachedThresholds()!!
        assertEquals(5000.0, cached.totalElectricMin, 0.01)
        assertEquals(50.0, cached.waterMax, 0.01)
    }

    // ── 天气预报缓存 ──

    @Test
    fun `weather forecast cache stored and retrieved`() = runTest {
        val prefs = UserPreferences(dataStoreWith(
            "weather_forecast_cache" to """{"temp":30}""",
            "weather_forecast_date" to "2026-07-14"
        ))
        assertEquals("""{"temp":30}""", prefs.weatherForecastCache.first())
        assertEquals("2026-07-14", prefs.weatherForecastDate.first())
    }

    // ── 写操作（验证不抛异常）──

    @Test
    fun `set theme runs without exception`() = runTest {
        UserPreferences(dataStoreWith()).setTheme(dark = false, followSystem = true)
    }

    @Test
    fun `cache thresholds runs without exception`() = runTest {
        UserPreferences(dataStoreWith()).cacheThresholds(ClassificationThresholds.DEFAULTS)
    }

    @Test
    fun `cache weather forecast runs without exception`() = runTest {
        UserPreferences(dataStoreWith()).cacheWeatherForecast("{}", "2026-07-14")
    }
}
