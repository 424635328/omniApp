package com.example.energyflow.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MeterRepository 单元测试。
 *
 * 使用 MockK 模拟 MeterRecordDao、AdaptiveClassifier、AnomalyDetector。
 * SmartInputParser 是内联实例化的纯逻辑（已有 41 用例覆盖），此处使用真实解析器。
 */
class MeterRepositoryTest {

    private val dao = mockk<MeterRecordDao>(relaxUnitFun = true)
    private val classifier = mockk<AdaptiveClassifier>(relaxUnitFun = true)
    private val detector = mockk<AnomalyDetector>(relaxUnitFun = true)
    private lateinit var repository: MeterRepository

    @Before
    fun setUp() {
        // classifier.getThresholds → 默认阈值
        coEvery { classifier.getThresholds() } returns ClassificationThresholds.DEFAULTS
        // 默认无异常告警
        coEvery { detector.checkParseResult(any()) } returns null
        coEvery { detector.checkParseResultForSpike(any()) } returns null
        // DAO insert → 返回 id=1
        coEvery { dao.insert(any()) } returns 1L
        // DAO 流 → 空
        coEvery { dao.getElectricRecords() } returns flowOf(emptyList())
        coEvery { dao.getWaterRecords() } returns flowOf(emptyList())
        // classifier.reLearn
        coEvery { classifier.reLearn() } returns Unit
        coEvery { classifier.getThresholds() } returns ClassificationThresholds.DEFAULTS

        repository = MeterRepository(dao, classifier, detector)
    }

    // ── smartInsert ──

    @Test
    fun `smartInsert with valid electric input returns Success`() = runTest {
        val result = repository.smartInsert("7.14 12.00 20000")
        assertTrue(result is InsertResult.Success)
        val success = result as InsertResult.Success
        assertEquals(1L, success.id)
        assertNotNull(success.record)
        coVerify { dao.insert(any()) }
        coVerify { classifier.reLearn() }
    }

    @Test
    fun `smartInsert with unparsable input returns Error`() = runTest {
        val result = repository.smartInsert("随便写点啥")
        assertTrue(result is InsertResult.Error)
    }

    @Test
    fun `smartInsert with monotonic anomaly returns Warning`() = runTest {
        coEvery { detector.checkParseResult(any()) } returns "读数异常：读数低于上次"

        val result = repository.smartInsert("7.14 12.00 20000")
        assertTrue(result is InsertResult.Warning)
        assertEquals("读数异常：读数低于上次", (result as InsertResult.Warning).message)
        coVerify(inverse = true) { dao.insert(any()) }
    }

    @Test
    fun `smartInsert with spike anomaly returns Warning`() = runTest {
        coEvery { detector.checkParseResultForSpike(any()) } returns "突增异常：日增幅过高"

        val result = repository.smartInsert("7.14 12.00 20000")
        assertTrue(result is InsertResult.Warning)
        assertEquals("突增异常：日增幅过高", (result as InsertResult.Warning).message)
    }

    @Test
    fun `smartInsert with force skips anomaly checks`() = runTest {
        coEvery { detector.checkParseResult(any()) } returns "读数异常"

        val result = repository.smartInsert("7.14 12.00 20000", force = true)
        assertTrue(result is InsertResult.Success)
    }

    // ── batchInsert ──

    @Test
    fun `batchInsert with valid input returns Success`() = runTest {
        val result = repository.batchInsert(
            """7.14 12.00 20000
7.15 12.00 21000"""
        )
        assertTrue(result is BatchInsertResult.Success)
        assertEquals(2, (result as BatchInsertResult.Success).count)
    }

    @Test
    fun `batchInsert with parse errors returns PartialSuccess`() = runTest {
        val result = repository.batchInsert(
            """7.14 12.00 20000
garbage input"""
        )
        assertTrue(result is BatchInsertResult.PartialSuccess)
        val ps = result as BatchInsertResult.PartialSuccess
        assertTrue(ps.successCount >= 1)
        assertTrue(ps.errors.isNotEmpty())
    }

    // ── calculateConsumption ──

    @Test
    fun `calculateConsumption returns Success when previous exists`() = runTest {
        val previous = MeterRecord(
            id = 1L,
            timestamp = NOW.minusDays(5),
            isElectricRecorded = true, electricTotal = 100.0,
            isWaterRecorded = true, waterTotal = 10.0
        )
        coEvery { dao.getPreviousRecord(any()) } returns previous

        val current = MeterRecord(
            timestamp = NOW,
            isElectricRecorded = true, electricTotal = 120.0,
            isWaterRecorded = true, waterTotal = 15.0
        )
        val result = repository.calculateConsumption(current)

        assertTrue(result is ConsumptionResult.Success)
        val s = result as ConsumptionResult.Success
        assertEquals(20.0, s.electricConsumption!!, 0.01)
        assertEquals(5.0, s.waterConsumption!!, 0.01)
        assertEquals(5, s.daysBetween)
        assertEquals(4.0, s.dailyElectricConsumption!!, 0.01)
        assertEquals(1.0, s.dailyWaterConsumption!!, 0.01)
    }

    @Test
    fun `calculateConsumption returns NoPreviousRecord when none exists`() = runTest {
        coEvery { dao.getPreviousRecord(any()) } returns null
        val current = MeterRecord(timestamp = NOW, isElectricRecorded = true, electricTotal = 100.0)
        val result = repository.calculateConsumption(current)
        assertTrue(result is ConsumptionResult.NoPreviousRecord)
    }

    @Test
    fun `calculateConsumption handles electric only records`() = runTest {
        val previous = MeterRecord(
            id = 1L, timestamp = NOW.minusDays(3),
            isElectricRecorded = true, electricTotal = 200.0
        )
        coEvery { dao.getPreviousRecord(any()) } returns previous

        val current = MeterRecord(
            timestamp = NOW,
            isElectricRecorded = true, electricTotal = 250.0
        )
        val result = repository.calculateConsumption(current) as ConsumptionResult.Success

        assertEquals(50.0, result.electricConsumption!!, 0.01)
        assertNull(result.waterConsumption)
        assertEquals(3, result.daysBetween)
    }

    // ── CRUD 委托 ──

    @Test
    fun `insert delegates to DAO and refreshes classifier`() = runTest {
        val record = MeterRecord(timestamp = NOW, isElectricRecorded = true, electricTotal = 100.0)
        val id = repository.insert(record)

        assertEquals(1L, id)
        coVerify { dao.insert(record) }
        coVerify { classifier.reLearn() }
    }

    @Test
    fun `update delegates to DAO and refreshes classifier`() = runTest {
        val record = MeterRecord(
            id = 1L, timestamp = NOW,
            isElectricRecorded = true, electricTotal = 100.0
        )
        repository.update(record)
        coVerify { dao.update(record) }
        coVerify { classifier.reLearn() }
    }

    @Test
    fun `delete delegates to DAO`() = runTest {
        val record = MeterRecord(id = 1L, timestamp = NOW)
        repository.delete(record)
        coVerify { dao.delete(record) }
    }

    @Test
    fun `getAllRecords delegates to DAO`() {
        coEvery { dao.getAllRecords() } returns flowOf(listOf(MeterRecord(timestamp = NOW)))
        val records = repository.getAllRecords()
        assertNotNull(records)
    }

    companion object {
        private val NOW = java.time.LocalDateTime.of(2026, 7, 14, 12, 0)
    }
}
