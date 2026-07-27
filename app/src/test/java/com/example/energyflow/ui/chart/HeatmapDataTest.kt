package com.example.energyflow.ui.chart

import com.example.energyflow.data.MeterRecord
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatmapDataTest {

    private fun electricRecord(day: Int, total: Double) = MeterRecord(
        timestamp = LocalDateTime.of(2026, 7, day, 12, 0),
        isElectricRecorded = true,
        electricTotal = total
    )

    @Test
    fun `multi-day gap spreads consumption evenly across days`() {
        val records = listOf(
            electricRecord(1, 100.0),
            electricRecord(4, 130.0)
        )

        val result = ChartViewModel.buildDailyConsumptionMap(
            records,
            ChartViewModel.MeterType.ELECTRIC
        )

        assertEquals(3, result.size)
        assertEquals(10.0, result.getValue(LocalDate.of(2026, 7, 2)), 1e-9)
        assertEquals(10.0, result.getValue(LocalDate.of(2026, 7, 3)), 1e-9)
        assertEquals(10.0, result.getValue(LocalDate.of(2026, 7, 4)), 1e-9)
    }

    @Test
    fun `negative diff is treated as zero`() {
        val records = listOf(
            electricRecord(1, 100.0),
            electricRecord(2, 50.0)
        )

        val result = ChartViewModel.buildDailyConsumptionMap(
            records,
            ChartViewModel.MeterType.ELECTRIC
        )

        assertEquals(0.0, result.getValue(LocalDate.of(2026, 7, 2)), 1e-9)
    }

    @Test
    fun `empty record list yields empty map`() {
        val result = ChartViewModel.buildDailyConsumptionMap(
            emptyList(),
            ChartViewModel.MeterType.ELECTRIC
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `single record yields no consumption`() {
        val result = ChartViewModel.buildDailyConsumptionMap(
            listOf(electricRecord(1, 100.0)),
            ChartViewModel.MeterType.ELECTRIC
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `water type reads waterTotal and ignores electric-only records`() {
        val records = listOf(
            MeterRecord(
                timestamp = LocalDateTime.of(2026, 7, 1, 12, 0),
                isWaterRecorded = true,
                waterTotal = 200.0
            ),
            electricRecord(2, 100.0),
            MeterRecord(
                timestamp = LocalDateTime.of(2026, 7, 3, 12, 0),
                isWaterRecorded = true,
                waterTotal = 204.0
            )
        )

        val result = ChartViewModel.buildDailyConsumptionMap(
            records,
            ChartViewModel.MeterType.WATER
        )

        assertEquals(2, result.size)
        assertEquals(2.0, result.getValue(LocalDate.of(2026, 7, 2)), 1e-9)
        assertEquals(2.0, result.getValue(LocalDate.of(2026, 7, 3)), 1e-9)
    }
}
