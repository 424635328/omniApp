package com.example.energyflow.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CsvExporter.buildCsv 单元测试 — 纯 JVM，不依赖 Android。
 */
class CsvExporterTest {

    private val timestamp = LocalDateTime.of(2026, 1, 15, 10, 30)

    private fun stripBom(csv: String): String = csv.removePrefix("\uFEFF")

    // ── BOM ──

    @Test
    fun `csv starts with utf-8 bom`() {
        val csv = CsvExporter.buildCsv(emptyList())
        assertTrue(csv.startsWith("\uFEFF"))
    }

    // ── 空列表 ──

    @Test
    fun `empty list produces header only`() {
        val csv = stripBom(CsvExporter.buildCsv(emptyList()))
        assertEquals("时间戳,电表总读数,峰,谷,水表,燃气,备注\n", csv)
    }

    // ── null 字段留空 ──

    @Test
    fun `null fields are left empty not zero`() {
        val record = MeterRecord(
            timestamp = timestamp,
            isElectricRecorded = true,
            electricTotal = 16776.0
        )
        val csv = stripBom(CsvExporter.buildCsv(listOf(record)))
        val dataLine = csv.lines()[1]
        assertEquals("2026-01-15T10:30:00,16776.0,,,,,", dataLine)
    }

    @Test
    fun `zero reading is written as zero`() {
        val record = MeterRecord(
            timestamp = timestamp,
            isWaterRecorded = true,
            waterTotal = 0.0
        )
        val csv = stripBom(CsvExporter.buildCsv(listOf(record)))
        val dataLine = csv.lines()[1]
        assertEquals("2026-01-15T10:30:00,,,,0.0,,", dataLine)
    }

    // ── 备注转义 ──

    @Test
    fun `note containing comma is quoted`() {
        val record = MeterRecord(
            timestamp = timestamp,
            note = "开空调,很热"
        )
        val csv = stripBom(CsvExporter.buildCsv(listOf(record)))
        val dataLine = csv.lines()[1]
        assertEquals("2026-01-15T10:30:00,,,,,,\"开空调,很热\"", dataLine)
    }

    @Test
    fun `note containing quotes is escaped per rfc4180`() {
        val record = MeterRecord(
            timestamp = timestamp,
            note = "他说\"太热\"了"
        )
        val csv = stripBom(CsvExporter.buildCsv(listOf(record)))
        val dataLine = csv.lines()[1]
        assertEquals("2026-01-15T10:30:00,,,,,,\"他说\"\"太热\"\"了\"", dataLine)
    }

    @Test
    fun `plain note is not quoted`() {
        val record = MeterRecord(
            timestamp = timestamp,
            note = "正常抄表"
        )
        val csv = stripBom(CsvExporter.buildCsv(listOf(record)))
        val dataLine = csv.lines()[1]
        assertEquals("2026-01-15T10:30:00,,,,,,正常抄表", dataLine)
    }

    // ── 完整记录 ──

    @Test
    fun `full record writes all columns`() {
        val record = MeterRecord(
            timestamp = timestamp,
            isElectricRecorded = true,
            electricTotal = 16776.0,
            electricPeak = 9000.5,
            electricValley = 7775.5,
            isWaterRecorded = true,
            waterTotal = 880.0,
            isGasRecorded = true,
            gasTotal = 120.3,
            note = "月度抄表"
        )
        val csv = stripBom(CsvExporter.buildCsv(listOf(record)))
        val dataLine = csv.lines()[1]
        assertEquals("2026-01-15T10:30:00,16776.0,9000.5,7775.5,880.0,120.3,月度抄表", dataLine)
    }
}
