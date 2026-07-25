package com.example.energyflow.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * AdaptiveClassifier 单元测试。
 *
 * 使用 MockK 模拟 MeterRecordDao 和 UserPreferences。
 * 覆盖无记录、纯电、纯水、混合、缓存命中/未命中、重学。
 */
class AdaptiveClassifierTest {

    private val dao = mockk<MeterRecordDao>(relaxUnitFun = true)
    private val prefs = mockk<UserPreferences>(relaxUnitFun = true)
    private lateinit var classifier: AdaptiveClassifier

    private val now = LocalDateTime.of(2026, 7, 14, 12, 0)

    @Before
    fun setUp() {
        // cacheThresholds 返回 typealias Preferences（dataStore.edit 的返回值）
        coEvery { prefs.cacheThresholds(any<ClassificationThresholds>()) } returns mockk()
        classifier = AdaptiveClassifier(dao, prefs)
    }

    // ── 辅助 ──

    private fun electricRecord(electricTotal: Double) = MeterRecord(
        timestamp = now, isElectricRecorded = true, electricTotal = electricTotal
    )

    private fun electricWithPeakValley(total: Double, peak: Double, valley: Double) = MeterRecord(
        timestamp = now, isElectricRecorded = true,
        electricTotal = total, electricPeak = peak, electricValley = valley
    )

    private fun waterRecord(waterTotal: Double) = MeterRecord(
        timestamp = now, isWaterRecorded = true, waterTotal = waterTotal
    )

    // ── 空记录 ──

    @Test
    fun `empty records defaults when no cache`() = runTest {
        coEvery { prefs.getCachedThresholds() } returns null
        coEvery { dao.getElectricRecords() } returns flowOf(emptyList())
        coEvery { dao.getWaterRecords() } returns flowOf(emptyList())

        val thresholds = classifier.getThresholds()
        assertEquals(ClassificationThresholds.DEFAULTS, thresholds)
        coVerify { prefs.cacheThresholds(ClassificationThresholds.DEFAULTS) }
    }

    @Test
    fun `empty records returns cached when available`() = runTest {
        val cached = ClassificationThresholds(
            totalElectricMin = 9999.0, peakMin = 1.0, peakMax = 2.0,
            valleyMin = 3.0, valleyMax = 4.0, waterMax = 5.0
        )
        coEvery { prefs.getCachedThresholds() } returns cached

        val thresholds = classifier.getThresholds()
        assertEquals(cached, thresholds)
    }

    // ── 纯电记录 ──

    @Test
    fun `electric only records compute correctly`() = runTest {
        coEvery { prefs.getCachedThresholds() } returns null
        coEvery { dao.getElectricRecords() } returns flowOf(
            listOf(
                electricRecord(10000.0),
                electricRecord(12000.0),
                electricRecord(11000.0)
            )
        )
        coEvery { dao.getWaterRecords() } returns flowOf(emptyList())

        val thresholds = classifier.getThresholds()
        // avg = (10000+12000+11000)/3 = 11000
        // totalElectricMin = 11000 * 0.85 = 9350
        assertEquals(9350.0, thresholds.totalElectricMin, 0.01)
        // 默认水上限
        assertEquals(ClassificationThresholds.DEFAULTS.waterMax, thresholds.waterMax, 0.01)
    }

    @Test
    fun `electric with peak valley computes correctly`() = runTest {
        coEvery { prefs.getCachedThresholds() } returns null
        coEvery { dao.getElectricRecords() } returns flowOf(
            listOf(
                electricWithPeakValley(10000.0, 5000.0, 3000.0),
                electricWithPeakValley(12000.0, 6000.0, 4000.0)
            )
        )
        coEvery { dao.getWaterRecords() } returns flowOf(emptyList())

        val thresholds = classifier.getThresholds()
        // peak: avg = (5000+6000)/2 = 5500, peakMin = 5500*0.85 = 4675, peakMax = 5500*1.15 = 6325
        assertEquals(4675.0, thresholds.peakMin, 0.01)
        assertEquals(6325.0, thresholds.peakMax, 0.01)
        // valley: avg = (3000+4000)/2 = 3500, valleyMin = 3500*0.85 = 2975, valleyMax = 3500*1.15 = 4025
        assertEquals(2975.0, thresholds.valleyMin, 0.01)
        assertEquals(4025.0, thresholds.valleyMax, 0.01)
    }

    // ── 纯水记录 ──

    @Test
    fun `water only records compute water max`() = runTest {
        coEvery { prefs.getCachedThresholds() } returns null
        coEvery { dao.getElectricRecords() } returns flowOf(emptyList())
        coEvery { dao.getWaterRecords() } returns flowOf(
            listOf(waterRecord(10.0), waterRecord(20.0), waterRecord(15.0))
        )

        val thresholds = classifier.getThresholds()
        // waterMax = 20 * 1.2 = 24
        assertEquals(24.0, thresholds.waterMax, 0.01)
        // 电默认值
        assertEquals(ClassificationThresholds.DEFAULTS.totalElectricMin, thresholds.totalElectricMin, 0.01)
    }

    // ── 混合 ──

    @Test
    fun `electric and water mixed records`() = runTest {
        coEvery { prefs.getCachedThresholds() } returns null
        coEvery { dao.getElectricRecords() } returns flowOf(
            listOf(electricRecord(10000.0), electricRecord(20000.0))
        )
        coEvery { dao.getWaterRecords() } returns flowOf(
            listOf(waterRecord(5.0), waterRecord(15.0))
        )

        val thresholds = classifier.getThresholds()
        // avg electric = 15000, min = 15000*0.85 = 12750
        assertEquals(12750.0, thresholds.totalElectricMin, 0.01)
        // max water = 15, waterMax = 15*1.2 = 18
        assertEquals(18.0, thresholds.waterMax, 0.01)
        coVerify { prefs.cacheThresholds(thresholds) }
    }

    // ── 重新学习 ──

    @Test
    fun `reLearn recomputes and caches`() = runTest {
        coEvery { dao.getElectricRecords() } returns flowOf(
            listOf(electricRecord(5000.0), electricRecord(7000.0))
        )
        coEvery { dao.getWaterRecords() } returns flowOf(emptyList())
        coEvery { prefs.getCachedThresholds() } returns ClassificationThresholds.DEFAULTS

        classifier.reLearn()
        coVerify { prefs.cacheThresholds(any()) }
    }

    // ── totalElectricMin 下限 ──

    @Test
    fun `total electric min uses average when records exist`() = runTest {
        coEvery { prefs.getCachedThresholds() } returns null
        coEvery { dao.getElectricRecords() } returns flowOf(
            listOf(electricRecord(100.0))  // avg=100, 100*0.85=85
        )
        coEvery { dao.getWaterRecords() } returns flowOf(emptyList())

        val thresholds = classifier.getThresholds()
        assertEquals(85.0, thresholds.totalElectricMin, 0.01) // 100 * 0.85
    }
}
