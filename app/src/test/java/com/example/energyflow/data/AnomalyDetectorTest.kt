package com.example.energyflow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class AnomalyDetectorTest {
    @Test
    fun `lower reading is blocked before save`() = runBlocking {
        val time = LocalDateTime.of(2026, 7, 14, 17, 17)
        val detector = AnomalyDetector(FakeDao(electric = listOf(reading(time.minusDays(1), 16776.0))))

        val warning = detector.checkElectricMonotonic(15800.0, time)

        assertNotNull(warning)
    }

    @Test
    fun `five hundred percent daily spike is warned`() = runBlocking {
        val start = LocalDateTime.of(2026, 7, 1, 12, 0)
        val detector = AnomalyDetector(
            FakeDao(electric = listOf(100.0, 110.0, 120.0, 130.0).mapIndexed { index, value ->
                reading(start.plusDays(index.toLong()), value)
            })
        )

        val warning = detector.checkElectricSpike(180.0, start.plusDays(4))

        assertNotNull(warning)
    }

    @Test
    fun `normal daily increase is accepted`() = runBlocking {
        val start = LocalDateTime.of(2026, 7, 1, 12, 0)
        val detector = AnomalyDetector(
            FakeDao(electric = listOf(100.0, 110.0, 120.0, 130.0).mapIndexed { index, value ->
                reading(start.plusDays(index.toLong()), value)
            })
        )

        assertNull(detector.checkElectricSpike(140.0, start.plusDays(4)))
    }

    private fun reading(time: LocalDateTime, total: Double) = MeterRecord(
        timestamp = time,
        isElectricRecorded = true,
        electricTotal = total
    )
}

private class FakeDao(
    private val electric: List<MeterRecord> = emptyList(),
    private val water: List<MeterRecord> = emptyList()
) : MeterRecordDao {
    override suspend fun insert(record: MeterRecord): Long = 1L
    override suspend fun update(record: MeterRecord) = Unit
    override suspend fun delete(record: MeterRecord) = Unit
    override fun getAllRecords(): Flow<List<MeterRecord>> = flowOf(electric + water)
    override fun getRecordsByTimeRange(startTime: LocalDateTime, endTime: LocalDateTime): Flow<List<MeterRecord>> = flowOf(emptyList())
    override suspend fun getLatestRecord(): MeterRecord? = (electric + water).maxByOrNull { it.timestamp }
    override suspend fun getPreviousRecord(currentTime: LocalDateTime): MeterRecord? = (electric + water).filter { it.timestamp < currentTime }.maxByOrNull { it.timestamp }
    override fun getElectricRecords(): Flow<List<MeterRecord>> = flowOf(electric)
    override fun getWaterRecords(): Flow<List<MeterRecord>> = flowOf(water)
    override fun getRecordsWithNotes(): Flow<List<MeterRecord>> = flowOf(emptyList())
    override fun getRecordCount(): Flow<Int> = flowOf(electric.size + water.size)
    override suspend fun deleteAll() = Unit
    override suspend fun getLatestElectricRecord(): MeterRecord? = electric.maxByOrNull { it.timestamp }
    override suspend fun getLatestWaterRecord(): MeterRecord? = water.maxByOrNull { it.timestamp }
}
