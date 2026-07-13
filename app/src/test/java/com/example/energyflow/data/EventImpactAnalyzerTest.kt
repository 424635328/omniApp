package com.example.energyflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class EventImpactAnalyzerTest {
    @Test
    fun `icebox event windows are compared with non event intervals`() {
        val start = LocalDateTime.of(2026, 7, 1, 0, 0)
        val records = listOf(
            reading(start, 100.0),
            reading(start.plusDays(1), 105.0),
            note(start.plusDays(1), "启用冰箱"),
            reading(start.plusDays(2), 115.0),
            reading(start.plusDays(3), 125.0),
            note(start.plusDays(3), "太吵了停止使用冰箱"),
            reading(start.plusDays(4), 130.0)
        )

        val impact = EventImpactAnalyzer().analyzeWithRecords(records).single()

        assertEquals("冰箱", impact.tag)
        assertEquals(10.0, impact.eventDailyKwh, 0.001)
        assertEquals(5.0, impact.nonEventDailyKwh, 0.001)
        assertEquals(5.0, impact.deltaKwh, 0.001)
        assertTrue(impact.eventDays > 0.0)
    }

    private fun reading(time: LocalDateTime, total: Double) = MeterRecord(
        timestamp = time,
        isElectricRecorded = true,
        electricTotal = total
    )

    private fun note(time: LocalDateTime, text: String) = MeterRecord(timestamp = time, note = text)
}
