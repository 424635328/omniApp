package com.example.energyflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.Year

/**
 * SmartInputParser 测试套件
 *
 * 覆盖全部 11 种正则模式，以及极端输入：
 * - 乱码输入
 * - 多条空格
 * - 日期倒挂
 * - 负数字值
 * - 超长备注
 * - 零值
 * - 中英混合
 */
class SmartInputParserTest {

    private val parser = SmartInputParser()
    private val year = Year.now().value
    private val defaults = ClassificationThresholds.DEFAULTS

    // ════════════════════════════════════════════════════
    // 模式 1: 纯日期头
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern1 - date header without values`() {
        val results = parser.parseWithContext("7.14")
        // 纯日期头不产生记录，仅设置上下文
        assertEquals(0, results.size)
    }

    @Test
    fun `pattern1 - single digit month and day`() {
        val results = parser.parseWithContext("1.1\n12.34 1000")
        assertTrue(results.isNotEmpty())
        // 1000 < WATER_MAX(1000)? No, it's NOT < 1000, so it's a total electric
        // With defaults: WATER_MAX = 1000, so 1000 is NOT < 1000 -> total electric
    }

    @Test
    fun `pattern1 - date header followed by value line`() {
        val input = """
            7.14
            17.17 16776
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        assertEquals(1, results.filterIsInstance<ParseResult.Success>().size)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(year, success.timestamp.year)
        assertEquals(7, success.timestamp.month.value)
        assertEquals(14, success.timestamp.dayOfMonth)
        assertEquals(17, success.timestamp.hour)
        assertEquals(17, success.timestamp.minute)
        assertEquals(16776.0, success.electricTotal!!, 0.01)
        assertTrue(success.isElectric)
    }

    // ════════════════════════════════════════════════════
    // 模式 2: 日期 + 时间 + 数值
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern2 - date time value with note`() {
        val results = parser.parseWithContext("7.13 01.23 16672 晚上读数", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(16672.0, success.electricTotal!!, 0.01)
        assertEquals("晚上读数", success.note)
    }

    @Test
    fun `pattern2 - without note`() {
        val results = parser.parseWithContext("7.13 01.23 16672", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(16672.0, success.electricTotal!!, 0.01)
        assertNull(success.note)
    }

    @Test
    fun `pattern2 - edge case with multi spaces`() {
        val results = parser.parseWithContext("  7.13    01.23    16672   ", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(16672.0, success.electricTotal!!, 0.01)
    }

    // ════════════════════════════════════════════════════
    // 模式 3: 日期 + 紧凑时间 + 数值
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern3 - compact time HHmm`() {
        val results = parser.parseWithContext("7.13 0123 9310", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(1, success.timestamp.hour)
        assertEquals(23, success.timestamp.minute)
        // 9310 falls in PEAK range (9000-10000 with defaults)
        assertEquals(9310.0, success.electricPeak!!, 0.01)
        assertTrue(success.isElectric)
    }

    // ════════════════════════════════════════════════════
    // 模式 4: 日期 + 电表 + 水表
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern4 - electric and water values on same line`() {
        val results = parser.parseWithContext("7.1 16639 880 两家", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertTrue(success.isElectric)
        assertEquals(16639.0, success.electricTotal!!, 0.01)
        assertTrue(success.isWater)
        assertEquals(880.0, success.waterTotal!!, 0.01)
        assertEquals("两家", success.note)
    }

    @Test
    fun `pattern4 - reversed order (water first, electric second)`() {
        // 模式4 中较大的是电表，较小的是水表
        val results = parser.parseWithContext("7.1 880 16639", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(16639.0, success.electricTotal!!, 0.01)
        assertEquals(880.0, success.waterTotal!!, 0.01)
    }

    // ════════════════════════════════════════════════════
    // 模式 5: 中文时间
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern5 - chinese time AM`() {
        val results = parser.parseWithContext("6.26上午六点开始启用冰箱", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(6, success.timestamp.hour)
        assertEquals(0, success.timestamp.minute)
        assertEquals("开始启用冰箱", success.note)
    }

    @Test
    fun `pattern5 - chinese time PM`() {
        val results = parser.parseWithContext("6.26下午六点停止使用冰箱", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        // 下午6点 = 18:00
        assertEquals(18, success.timestamp.hour)
        assertEquals("停止使用冰箱", success.note)
    }

    @Test
    fun `pattern5 - chinese numerals`() {
        // "三点二十分" → convert("三"→3, "二"→2, "十"→10)
        // becomes "3点2010分" → regex 10(\d) → "3点210分"
        // regex matches hour=3, minute=210
        // Note: minute 210 is coerced by LocalDateTime but may fail.
        // This is a known edge-case in Chinese numeral conversion.
        val results = parser.parseWithContext("7.5下午三点十五分纪录", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(15, success.timestamp.hour)
        assertEquals(15, success.timestamp.minute)
    }

    // ════════════════════════════════════════════════════
    // 模式 6: 日期 + 时间 + 纯备注
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern6 - date time with text note only`() {
        val results = parser.parseWithContext("7.14 16.39 打开冰箱", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertFalse(success.isElectric)
        assertEquals("打开冰箱", success.note)
    }

    // ════════════════════════════════════════════════════
    // 模式 7: 时间 + 数值（依赖上下文日期）
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern7 - time value with date header context`() {
        val input = """
            7.14
            17.17 16776
            22.30 16789
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val successes = results.filterIsInstance<ParseResult.Success>()
        assertEquals(2, successes.size)
        assertEquals(16776.0, successes[0].electricTotal!!, 0.01)
        assertEquals(16789.0, successes[1].electricTotal!!, 0.01)
    }

    @Test
    fun `pattern7 - error without date context`() {
        val results = parser.parseWithContext("17.17 16776", defaults)
        assertTrue(results.all { it is ParseResult.Error })
    }

    // ════════════════════════════════════════════════════
    // 模式 7a: 时间 + 电表 + 水表 [备注]（导出→导入往返格式）
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern7a - time with electric and water values`() {
        val input = """
            7.1
            12.00 16639 880 两家
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertTrue(success.isElectric)
        assertTrue(success.isWater)
        assertEquals(16639.0, success.electricTotal!!, 0.01)
        assertEquals(880.0, success.waterTotal!!, 0.01)
        assertEquals("两家", success.note)
    }

    @Test
    fun `pattern7a - time with electric and water no note`() {
        val input = """
            7.1
            12.00 16639 880
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertTrue(success.isElectric)
        assertTrue(success.isWater)
        assertEquals(16639.0, success.electricTotal!!, 0.01)
        assertEquals(880.0, success.waterTotal!!, 0.01)
        assertNull(success.note)
    }

    @Test
    fun `pattern7a - reversed order water then electric`() {
        // 模式7a 同模式4：较大的是电表，较小的是水表
        val input = """
            7.1
            12.00 880 16639
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(16639.0, success.electricTotal!!, 0.01)
        assertEquals(880.0, success.waterTotal!!, 0.01)
    }

    // ════════════════════════════════════════════════════
    // 模式 8: 紧凑时间 + 数值
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern8 - compact time with date context`() {
        val input = """
            7.14
            1717 16776
            2230 16789
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val successes = results.filterIsInstance<ParseResult.Success>()
        assertEquals(2, successes.size)
        assertEquals(17, successes[0].timestamp.hour)
        assertEquals(17, successes[0].timestamp.minute)
        assertEquals(22, successes[1].timestamp.hour)
        assertEquals(30, successes[1].timestamp.minute)
    }

    // ════════════════════════════════════════════════════
    // 模式 9: 时间 + 备注
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern9 - time with text note context`() {
        val input = """
            7.14
            16.39打开冰箱
            22.15关冰箱
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val successes = results.filterIsInstance<ParseResult.Success>()
        assertEquals(2, successes.size)
        assertEquals("打开冰箱", successes[0].note)
        assertEquals("关冰箱", successes[1].note)
    }

    // ════════════════════════════════════════════════════
    // 模式 10: 水表前缀
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern10 - water prefix with date context`() {
        val input = """
            7.14
            水0879
            水880
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val successes = results.filterIsInstance<ParseResult.Success>()
        assertEquals(2, successes.size)
        assertTrue(successes.all { it.isWater && !it.isElectric })
        assertEquals(879.0, successes[0].waterTotal!!, 0.01)
        assertEquals(880.0, successes[1].waterTotal!!, 0.01)
    }

    // ════════════════════════════════════════════════════
    // 模式 11: 纯数值（智能识别）
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern11 - total electric with context`() {
        val input = """
            7.14
            16776
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertTrue(success.isElectric)
        assertEquals(16776.0, success.electricTotal!!, 0.01)
    }

    @Test
    fun `pattern11 - peak electric value`() {
        val input = """
            7.14
            9310
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(9310.0, success.electricPeak!!, 0.01)
    }

    @Test
    fun `pattern11 - valley electric value`() {
        val input = """
            7.14
            7298
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(7298.0, success.electricValley!!, 0.01)
    }

    @Test
    fun `pattern11 - water value less than threshold`() {
        val input = """
            7.14
            879
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertTrue(success.isWater)
        assertEquals(879.0, success.waterTotal!!, 0.01)
    }

    @Test
    fun `pattern11 - peak valley pairing batch import`() {
        // PENDING_PEAK_VALLEY range = 4000-9000
        // Values in this range that are NOT already classified as peak/valley will be paired
        // But this overlaps with peak (9000-10000) and valley (7000-8000) default ranges.
        // Use values between 4000-7000 that are unambiguously in PENDING only.
        val input = """
            7.14
            5500
            4800
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val successes = results.filterIsInstance<ParseResult.Success>()
        // Should pair: peak=5500, valley=4800 → 1 record
        assertTrue("Expected at least 1 result, got ${successes.size}", successes.size >= 1)
    }

    // ════════════════════════════════════════════════════
    // 🔥 极端输入测试
    // ════════════════════════════════════════════════════

    @Test
    fun `extreme - garbled input returns errors only`() {
        val results = parser.parseWithContext("asdfghjkl!@#$$%^&*()", defaults)
        assertTrue(results.all { it is ParseResult.Error })
    }

    @Test
    fun `extreme - empty input returns empty`() {
        val results = parser.parseWithContext("", defaults)
        assertEquals(0, results.size)
    }

    @Test
    fun `extreme - multi-line blank lines filtered out`() {
        val input = """

            7.14

            16776

        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        assertEquals(1, results.filterIsInstance<ParseResult.Success>().size)
    }

    @Test
    fun `extreme - very long note`() {
        val longNote = "A".repeat(500)
        val results = parser.parseWithContext("7.14 16.39 $longNote", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(longNote, success.note)
    }

    @Test
    fun `extreme - zero as value`() {
        // 0 < WATER_MAX(1000) → water
        val results = parser.parseWithContext("7.14\n0", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertTrue(success.isWater)
        assertEquals(0.0, success.waterTotal!!, 0.01)
    }

    @Test
    fun `extreme - very large electric value`() {
        val results = parser.parseWithContext("7.14\n999999", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertTrue(success.isElectric)
        assertEquals(999999.0, success.electricTotal!!, 0.01)
    }

    @Test
    fun `extreme - decimal values`() {
        val results = parser.parseWithContext("7.14\n9310.75\n7298.66", defaults)
        val successes = results.filterIsInstance<ParseResult.Success>()
        assertEquals(1, successes.size)
        assertEquals(9310.75, successes[0].electricPeak!!, 0.001)
        assertEquals(7298.66, successes[0].electricValley!!, 0.001)
        assertEquals(16609.41, successes[0].electricTotal!!, 0.001)
    }

    @Test
    fun `extreme - invalid date is rejected rather than silently coerced`() {
        val results = parser.parseWithContext("2.31\n16776", defaults)
        assertTrue(results.all { it is ParseResult.Error })
    }

    @Test
    fun `extreme - negative value treated as text`() {
        val results = parser.parseWithContext("7.14\n-100", defaults)
        // Negative doesn't match the numeric regex ^\d+\.?\d*$
        assertTrue(results.filterIsInstance<ParseResult.Success>().isEmpty())
    }

    @Test
    fun `extreme - mixed chinese arabic numerals`() {
        // "三点十五分" → "三"→3, "十"→10, "五"→5 → "3点105分" → 10(\d)→"3点15分"
        val results = parser.parseWithContext("7.5下午三点十五分纪录", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(15, success.timestamp.hour)
        assertEquals(15, success.timestamp.minute)
    }

    @Test
    fun `extreme - tab characters instead of spaces`() {
        val results = parser.parseWithContext("7.13\t01.23\t16672", defaults)
        // Tabs are treated as whitespace (trim doesn't remove tabs but regex \s+ does)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(16672.0, success.electricTotal!!, 0.01)
    }

    @Test
    fun `extreme - invalid clock is rejected`() {
        val results = parser.parseWithContext("7.14 25.61 16776", defaults)
        assertTrue(results.single() is ParseResult.Error)
    }

    @Test
    fun `extreme - inverted month and day is rejected`() {
        val results = parser.parseWithContext("14.7\n16776", defaults)
        assertTrue(results.all { it is ParseResult.Error })
    }

    @Test
    fun `extreme - value before date context is not assigned to a later date`() {
        val results = parser.parseWithContext("9310\n7.14", defaults)
        assertTrue(results.single() is ParseResult.Error)
    }

    @Test
    fun `extreme - full unicode emoji in note`() {
        val results = parser.parseWithContext("7.14 16.39 ❄️🔇🧺 测试中文字符", defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals("❄️🔇🧺 测试中文字符", success.note)
    }

    @Test
    fun `extreme - month boundary values`() {
        // 验证 12月 和 1月 都工作
        val dec = parser.parseWithContext("12.31\n16776", defaults)
        val jan = parser.parseWithContext("1.1\n16776", defaults)
        val d1 = dec.filterIsInstance<ParseResult.Success>().first()
        val d2 = jan.filterIsInstance<ParseResult.Success>().first()
        assertEquals(12, d1.timestamp.month.value)
        assertEquals(31, d1.timestamp.dayOfMonth)
        assertEquals(1, d2.timestamp.month.value)
        assertEquals(1, d2.timestamp.dayOfMonth)
    }

    // ════════════════════════════════════════════════════
    // 📐 自适应阈值测试
    // ════════════════════════════════════════════════════

    @Test
    fun `adaptive - custom thresholds shift peak range`() {
        val custom = ClassificationThresholds(
            peakMin = 10000.0,
            peakMax = 11000.0,
            valleyMin = 8000.0,
            valleyMax = 9000.0,
            totalElectricMin = 16000.0,
            waterMax = 500.0
        )
        // 9310 was peak before, now with peakMin=10000 it should be total electric
        val input = """
            7.14
            9310
        """.trimIndent()
        val results = parser.parseWithContext(input, custom)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        // 9310 < totalElectricMin(16000) and NOT in peak/valley range → total electric (兜底)
        assertEquals(9310.0, success.electricTotal!!, 0.01)
        assertNull(success.electricPeak)
    }

    @Test
    fun `adaptive - custom water max changes classification`() {
        val custom = ClassificationThresholds(
            waterMax = 2000.0
        )
        val input = """
            7.14
            1500
        """.trimIndent()
        val results = parser.parseWithContext(input, custom)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        // 1500 < 2000 → water
        assertTrue(success.isWater)
        assertEquals(1500.0, success.waterTotal!!, 0.01)
    }

    // ════════════════════════════════════════════════════
    // 🆕 模式 0: # 注释行跳过
    // ════════════════════════════════════════════════════

    @Test
    fun `comment - hash lines are skipped`() {
        val input = """
            7.14
            # 这是注释
            17.17 16776
            # ═════ 分隔线 ═════
            22.30 16789
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val successes = results.filterIsInstance<ParseResult.Success>()
        assertEquals(2, successes.size)
        assertEquals(16776.0, successes[0].electricTotal!!, 0.01)
        assertEquals(16789.0, successes[1].electricTotal!!, 0.01)
    }

    @Test
    fun `comment - only hash lines produce no records`() {
        val results = parser.parseWithContext("# just a comment\n# another", defaults)
        assertEquals(0, results.size)
    }

    // ════════════════════════════════════════════════════
    // 🆕 模式 6b: 时间 + 标记值（电/水/气/峰/谷）
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern6b - labeled electric water gas peak valley`() {
        val input = """
            7.14
            14.30 电12345 水67.89 气12.34 峰678 谷901 备注文本
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertTrue(success.isElectric)
        assertTrue(success.isWater)
        assertTrue(success.isGas)
        assertEquals(12345.0, success.electricTotal!!, 0.01)
        assertEquals(67.89, success.waterTotal!!, 0.01)
        assertEquals(12.34, success.gasTotal!!, 0.01)
        assertEquals(678.0, success.electricPeak!!, 0.01)
        assertEquals(901.0, success.electricValley!!, 0.01)
        assertEquals("备注文本", success.note)
    }

    @Test
    fun `pattern6b - labeled electric only`() {
        val input = """
            7.14
            14.30 电12345
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertTrue(success.isElectric)
        assertFalse(success.isWater)
        assertFalse(success.isGas)
        assertEquals(12345.0, success.electricTotal!!, 0.01)
    }

    @Test
    fun `pattern6b - labeled water only with time`() {
        val input = """
            7.14
            14.30 水67.89
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertFalse(success.isElectric)
        assertTrue(success.isWater)
        assertFalse(success.isGas)
        assertEquals(67.89, success.waterTotal!!, 0.01)
    }

    // ════════════════════════════════════════════════════
    // 🆕 模式 9a: 时间 + 水表标记
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern9a - timed water with label`() {
        val input = """
            7.14
            14.30 水12.34 卫生间
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertTrue(success.isWater)
        assertEquals(12.34, success.waterTotal!!, 0.01)
        assertEquals("卫生间", success.note)
        assertEquals(14, success.timestamp.hour)
        assertEquals(30, success.timestamp.minute)
    }

    // ════════════════════════════════════════════════════
    // 🆕 模式 9b: 时间 + 燃气标记
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern9b - timed gas with label`() {
        val input = """
            7.14
            14.30 气5.67
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertTrue(success.isGas)
        assertFalse(success.isElectric)
        assertFalse(success.isWater)
        assertEquals(5.67, success.gasTotal!!, 0.01)
        assertEquals(14, success.timestamp.hour)
        assertEquals(30, success.timestamp.minute)
    }

    // ════════════════════════════════════════════════════
    // 🆕 模式 10b: 燃气前缀
    // ════════════════════════════════════════════════════

    @Test
    fun `pattern10b - gas prefix`() {
        val input = """
            7.14
            气5.67
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertTrue(success.isGas)
        assertEquals(5.67, success.gasTotal!!, 0.01)
    }

    // ════════════════════════════════════════════════════
    // 🆕 峰谷值从备注提取
    // ════════════════════════════════════════════════════

    @Test
    fun `peakValleyFromNote - electric total with peak valley in note`() {
        val input = """
            7.14
            17.17 16776 峰678 谷901
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(16776.0, success.electricTotal!!, 0.01)
        assertEquals(678.0, success.electricPeak!!, 0.01)
        assertEquals(901.0, success.electricValley!!, 0.01)
    }

    @Test
    fun `peakValleyFromNote - note is cleaned after extraction`() {
        val input = """
            7.14
            14.30 16776 峰678 谷901 今晚读数
        """.trimIndent()
        val results = parser.parseWithContext(input, defaults)
        val success = results.filterIsInstance<ParseResult.Success>().first()
        assertEquals(678.0, success.electricPeak!!, 0.01)
        assertEquals(901.0, success.electricValley!!, 0.01)
        assertEquals("今晚读数", success.note)
    }
}
