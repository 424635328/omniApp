package com.example.energyflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class EventImpactAnalyzerTest {

    private val analyzer = EventImpactAnalyzer()

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
        val impact = analyzer.analyzeWithRecords(records).single()
        assertEquals("冰箱", impact.tag)
        assertEquals(10.0, impact.eventDailyKwh, 0.001)
        assertEquals(5.0, impact.nonEventDailyKwh, 0.001)
        assertEquals(5.0, impact.deltaKwh, 0.001)
        assertTrue(impact.eventDays > 0.0)
    }

    @Test
    fun `no tags in notes returns empty`() {
        val records = listOf(
            reading(now, 100.0),
            reading(now.plusDays(1), 105.0),
            reading(now.plusDays(2), 110.0)
        )
        assertTrue(analyzer.analyzeWithRecords(records).isEmpty())
    }

    @Test
    fun `unpaired start leaves open window until end`() {
        val records = listOf(
            reading(now, 100.0),
            reading(now.plusDays(1), 105.0),   // 控制期：第1天
            reading(now.plusDays(2), 109.0),   // 控制期：第2天
            note(now.plusDays(3), "启用空调"), // ← 事件开始
            reading(now.plusDays(4), 125.0),   // 事件中
            reading(now.plusDays(5), 140.0)    // 事件中
        )
        val impacts = analyzer.analyzeWithRecords(records)
        assertEquals(1, impacts.size)
        assertEquals("空调", impacts[0].tag)
        assertTrue(impacts[0].eventDays > 0)
        assertTrue(impacts[0].nonEventDays > 0)
    }

    @Test
    fun `unpaired stop without start is ignored`() {
        val records = listOf(
            reading(now, 100.0),
            note(now.plusDays(1), "停止使用热水器"),
            reading(now.plusDays(2), 105.0)
        )
        assertTrue(analyzer.analyzeWithRecords(records).isEmpty())
    }

    @Test
    fun `less than 3 electric records returns empty`() {
        val records = listOf(
            reading(now, 100.0),
            note(now.plusDays(1), "启用冰箱")
        )
        assertTrue(analyzer.analyzeWithRecords(records).isEmpty())
    }

    @Test
    fun `hash tag style detection works`() {
        val records = listOf(
            reading(now, 100.0),
            reading(now.plusDays(1), 105.0),       // 控制期
            note(now.plusDays(2), "开启#新风扇"),  // start word + hash tag (不包含已知电器名)
            reading(now.plusDays(3), 115.0),        // 事件中
            note(now.plusDays(3), "关了"),          // stop
            reading(now.plusDays(4), 118.0)
        )
        val impacts = analyzer.analyzeWithRecords(records)
        assertEquals(1, impacts.size)
        assertEquals("新风扇", impacts[0].tag)
    }

    @Test
    fun `known appliance matched without hash`() {
        val records = listOf(
            reading(now, 100.0),
            note(now.plusDays(1), "开了洗衣机洗衣服"),
            reading(now.plusDays(2), 112.0),
            note(now.plusDays(2), "关了洗衣机"),
            reading(now.plusDays(3), 118.0)
        )
        val impacts = analyzer.analyzeWithRecords(records)
        assertEquals(1, impacts.size)
        assertEquals("洗衣机", impacts[0].tag)
    }

    @Test
    fun `multiple events with different tags`() {
        val records = listOf(
            reading(now, 100.0),
            note(now.plusDays(1), "启用冰箱"),
            reading(now.plusDays(2), 110.0),
            note(now.plusDays(2), "打开空调"),
            reading(now.plusDays(3), 125.0),
            note(now.plusDays(3), "停止使用冰箱"),
            reading(now.plusDays(4), 130.0),
            note(now.plusDays(4), "关了空调"),
            reading(now.plusDays(5), 135.0)
        )
        val impacts = analyzer.analyzeWithRecords(records)
        assertEquals(2, impacts.size)
        val tags = impacts.map { it.tag }
        assertTrue("冰箱" in tags)
        assertTrue("空调" in tags)
    }

    @Test
    fun `all empty notes returns empty`() {
        val records = listOf(
            reading(now, 100.0),
            MeterRecord(timestamp = now.plusDays(1), note = null),
            reading(now.plusDays(2), 110.0)
        )
        assertTrue(analyzer.analyzeWithRecords(records).isEmpty())
    }

    // ── 辅助 ──

    companion object {
        private val now = LocalDateTime.of(2026, 7, 1, 0, 0)
    }

    private fun reading(time: LocalDateTime, total: Double) = MeterRecord(
        timestamp = time,
        isElectricRecorded = true,
        electricTotal = total
    )

    private fun note(time: LocalDateTime, text: String) = MeterRecord(timestamp = time, note = text)
}
