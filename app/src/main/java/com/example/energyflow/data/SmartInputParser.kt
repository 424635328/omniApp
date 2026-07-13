package com.example.energyflow.data

import java.time.LocalDateTime
import java.time.Year

class SmartInputParser {
    companion object {
        private val currentYear = Year.now().value

        // 峰谷值待定范围（4000-9000，用于批量导入时的峰谷配对）
        private const val PENDING_PEAK_VALLEY_MIN = 4000.0
        private const val PENDING_PEAK_VALLEY_MAX = 9000.0
    }

    /**
     * 智能批量解析，支持上下文关联的多行数据。
     * @param thresholds 可选的自适应阈值，不传则使用默认值。
     */
    fun parseWithContext(input: String, thresholds: ClassificationThresholds? = null): List<ParseResult> {
        val lines = input.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val results = mutableListOf<ParseResult>()
        var currentMonth: Int? = null
        var currentDay: Int? = null
        var pendingElectric: PendingElectric? = null

        for (line in lines) {
            val result = parseLineWithContext(line, currentMonth, currentDay, pendingElectric, thresholds)

            when (result) {
                is ContextParseResult.DateHeader -> {
                    currentMonth = result.month
                    currentDay = result.day
                    pendingElectric?.let { pe ->
                        results.add(pe.toSuccess(currentMonth!!, currentDay!!))
                        pendingElectric = null
                    }
                }
                is ContextParseResult.RecordWithDate -> {
                    currentMonth = result.month
                    currentDay = result.day
                    pendingElectric?.let { pe ->
                        results.add(pe.toSuccess(currentMonth!!, currentMonth!!.let { currentDay!! }))
                        pendingElectric = null
                    }
                    results.add(result.result)
                }
                is ContextParseResult.Record -> {
                    if (currentMonth != null && currentDay != null) {
                        results.add(result.result)
                    } else {
                        results.add(ParseResult.Error("缺少日期上下文"))
                    }
                }
                is ContextParseResult.PendingPeakValley -> {
                    pendingElectric?.let { pe ->
                        results.add(pe.toSuccess(currentMonth!!, currentDay!!))
                    }
                    pendingElectric = result.pending
                }
                is ContextParseResult.Error -> {
                    results.add(ParseResult.Error(result.message))
                }
            }
        }

        pendingElectric?.let { pe ->
            if (currentMonth != null && currentDay != null) {
                results.add(pe.toSuccess(currentMonth, currentDay))
            }
        }

        return results
    }

    private fun parseLineWithContext(
        line: String,
        currentMonth: Int?,
        currentDay: Int?,
        pendingElectric: PendingElectric?,
        thresholds: ClassificationThresholds? = null
    ): ContextParseResult {
        // 模式1: 纯日期头
        Regex("""^(\d{1,2})\.(\d{1,2})$""").matchEntire(line)?.let { match ->
            return ContextParseResult.DateHeader(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }

        // 模式2: 日期 + 时间 + 数值
        Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d{1,2})\.(\d{2})\s+(\d+\.?\d*)\s*(.*)$""").matchEntire(line)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val hour = match.groupValues[3].toInt()
            val minute = match.groupValues[4].toInt()
            val value = match.groupValues[5].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            val note = match.groupValues[6].trim().ifEmpty { null }
            return ContextParseResult.RecordWithDate(month, day, classifyValue(month, day, hour, minute, value, note, thresholds))
        }

        // 模式3: 日期 + 紧凑时间 + 数值
        Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d{2})(\d{2})\s+(\d+\.?\d*)\s*(.*)$""").matchEntire(line)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val hour = match.groupValues[3].toInt()
            val minute = match.groupValues[4].toInt()
            val value = match.groupValues[5].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            val note = match.groupValues[6].trim().ifEmpty { null }
            return ContextParseResult.RecordWithDate(month, day, classifyValue(month, day, hour, minute, value, note, thresholds))
        }

        // 模式4: 日期 + 电表 + 水表
        Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d+\.?\d*)\s+(\d+\.?\d*)\s*(.*)$""").matchEntire(line)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val value1 = match.groupValues[3].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            val value2 = match.groupValues[4].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            val note = match.groupValues[5].trim().ifEmpty { null }
            // 较大的是电表，较小的是水表
            val (electric, water) = if (value1 > value2) value1 to value2 else value2 to value1
            return ContextParseResult.RecordWithDate(
                month, day,
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear, month, day, 12, 0),
                    isElectric = true, electricTotal = electric,
                    isWater = true, waterTotal = water,
                    note = note
                )
            )
        }

        // 模式5: 日期 + 中文时间 + 备注
        val convertedLine = convertChineseNumerals(line)
        Regex("""^(\d{1,2})\.(\d{1,2})\s*(上午|下午)?(\d{1,2})[点时](\d{0,2})分?\s*(.*)$""").matchEntire(convertedLine)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val period = match.groupValues[3]
            var hour = match.groupValues[4].toInt()
            val minute = match.groupValues[5].let { if (it.isEmpty()) 0 else it.toInt() }
            val note = match.groupValues[6].trim().ifEmpty { null }
            if (period == "下午" && hour < 12) hour += 12
            if (period == "上午" && hour == 12) hour = 0
            return ContextParseResult.RecordWithDate(
                month, day,
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear, month, day, hour, minute),
                    isElectric = false, electricTotal = null,
                    isWater = false, waterTotal = null,
                    note = note
                )
            )
        }

        // 模式5b: 日期 + 时间 无数值
        Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d{1,2})\.(\d{2})$""").matchEntire(line)?.let { match ->
            return ContextParseResult.DateHeader(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }

        // 模式6: 日期 + 时间 + 备注
        Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d{1,2})\.(\d{2})\s+(\D.+)$""").matchEntire(line)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val hour = match.groupValues[3].toInt()
            val minute = match.groupValues[4].toInt()
            val note = match.groupValues[5].trim()
            return ContextParseResult.RecordWithDate(
                month, day,
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear, month, day, hour, minute),
                    isElectric = false, electricTotal = null,
                    isWater = false, waterTotal = null,
                    note = note
                )
            )
        }

        // 模式7: 时间 + 数值
        Regex("""^(\d{1,2})\.(\d{2})\s+(\d+\.?\d*)\s*(.*)$""").matchEntire(line)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val value = match.groupValues[3].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            val note = match.groupValues[4].trim().ifEmpty { null }
            if (currentMonth == null || currentDay == null) return ContextParseResult.Error("缺少日期上下文")
            return ContextParseResult.Record(classifyValue(currentMonth, currentDay, hour, minute, value, note, thresholds))
        }

        // 模式8: 紧凑时间 + 数值
        Regex("""^(\d{2})(\d{2})\s+(\d+\.?\d*)\s*(.*)$""").matchEntire(line)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val value = match.groupValues[3].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            val note = match.groupValues[4].trim().ifEmpty { null }
            if (currentMonth == null || currentDay == null) return ContextParseResult.Error("缺少日期上下文")
            return ContextParseResult.Record(classifyValue(currentMonth, currentDay, hour, minute, value, note, thresholds))
        }

        // 模式9: 时间 + 备注
        Regex("""^(\d{1,2})\.(\d{2})\s*(\D.+)$""").matchEntire(line)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val note = match.groupValues[3].trim()
            if (currentMonth == null || currentDay == null) return ContextParseResult.Error("缺少日期上下文")
            return ContextParseResult.Record(
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear, currentMonth, currentDay, hour, minute),
                    isElectric = false, electricTotal = null,
                    isWater = false, waterTotal = null,
                    note = note
                )
            )
        }

        // 模式10: 水表前缀
        Regex("""^水\s*(\d+\.?\d*)$""").matchEntire(line)?.let { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            if (currentMonth == null || currentDay == null) return ContextParseResult.Error("缺少日期上下文")
            return ContextParseResult.Record(
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear, currentMonth, currentDay, 12, 0),
                    isElectric = false, electricTotal = null,
                    isWater = true, waterTotal = value,
                    note = null
                )
            )
        }

        // 模式11: 纯数值（智能识别）
        Regex("""^(\d+\.?\d*)$""").matchEntire(line)?.let { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")

            // 峰谷值配对逻辑（批量导入时，两个 4000-9000 的数值配对为峰+谷）
            if (value in PENDING_PEAK_VALLEY_MIN..PENDING_PEAK_VALLEY_MAX) {
                if (pendingElectric == null) {
                    return ContextParseResult.PendingPeakValley(PendingElectric(peak = value, valley = null, total = null))
                } else if (pendingElectric.valley == null && pendingElectric.peak != null) {
                    val completed = pendingElectric.copy(valley = value, total = pendingElectric.peak!! + value)
                    if (currentMonth != null && currentDay != null) {
                        return ContextParseResult.Record(completed.toSuccess(currentMonth, currentDay))
                    }
                }
            }

            if (currentMonth == null || currentDay == null) return ContextParseResult.Error("缺少日期上下文: $line")
            return ContextParseResult.Record(classifyValue(currentMonth, currentDay, 12, 0, value, null, thresholds))
        }

        return ContextParseResult.Error("无法解析: $line")
    }

    /**
     * 智能分类数值 — 使用动态阈值。
     *
     * 判断逻辑（由 thresholds 控制）：
     *   < waterMax            → 水表
     *   峰电区间 [peakMin, peakMax] → 峰电
     *   谷电区间 [valleyMin, valleyMax] → 谷电
     *   ≥ totalElectricMin    → 总电表
     *   其他                  → 默认为总电表
     */
    private fun classifyValue(
        month: Int, day: Int, hour: Int, minute: Int,
        value: Double, note: String?,
        thresholds: ClassificationThresholds? = null
    ): ParseResult.Success {
        val t = thresholds ?: ClassificationThresholds.DEFAULTS
        val timestamp = try {
            LocalDateTime.of(currentYear, month, day, hour, minute)
        } catch (e: Exception) {
            LocalDateTime.of(currentYear, month, day.coerceAtMost(28), hour, minute)
        }

        return when {
            // 水表
            value < t.waterMax -> ParseResult.Success(
                timestamp = timestamp,
                isElectric = false, electricTotal = null,
                isWater = true, waterTotal = value,
                note = note
            )
            // 峰电
            value in t.peakMin..t.peakMax -> ParseResult.Success(
                timestamp = timestamp,
                isElectric = true, electricTotal = value, electricPeak = value,
                isWater = false, waterTotal = null,
                note = note
            )
            // 谷电
            value in t.valleyMin..t.valleyMax -> ParseResult.Success(
                timestamp = timestamp,
                isElectric = true, electricTotal = value, electricValley = value,
                isWater = false, waterTotal = null,
                note = note
            )
            // 总电
            value >= t.totalElectricMin -> ParseResult.Success(
                timestamp = timestamp,
                isElectric = true, electricTotal = value,
                isWater = false, waterTotal = null,
                note = note
            )
            // 中间值：默认为总电
            else -> ParseResult.Success(
                timestamp = timestamp,
                isElectric = true, electricTotal = value,
                isWater = false, waterTotal = null,
                note = note
            )
        }
    }

    /**
     * 将中文数字转换为阿拉伯数字
     */
    private fun convertChineseNumerals(input: String): String {
        val chineseToArabic = mapOf(
            "零" to "0", "〇" to "0",
            "一" to "1", "壹" to "1",
            "二" to "2", "贰" to "2", "两" to "2",
            "三" to "3", "叁" to "3",
            "四" to "4", "肆" to "4",
            "五" to "5", "伍" to "5",
            "六" to "6", "陆" to "6",
            "七" to "7", "柒" to "7",
            "八" to "8", "捌" to "8",
            "九" to "9", "玖" to "9",
            "十" to "10", "拾" to "10"
        )
        var result = input
        chineseToArabic.forEach { (chinese, arabic) ->
            result = result.replace(chinese, arabic)
        }
        result = result.replace(Regex("10(\\d)"), "1$1")
        return result
    }
}

data class PendingElectric(
    val peak: Double?,
    val valley: Double?,
    val total: Double?
) {
    fun toSuccess(month: Int, day: Int): ParseResult.Success {
        return ParseResult.Success(
            timestamp = LocalDateTime.of(Year.now().value, month, day, 12, 0),
            isElectric = true,
            electricTotal = total ?: (peak ?: 0.0) + (valley ?: 0.0),
            electricPeak = peak,
            electricValley = valley,
            isWater = false,
            waterTotal = null,
            note = null
        )
    }
}

sealed class ContextParseResult {
    data class DateHeader(val month: Int, val day: Int) : ContextParseResult()
    data class RecordWithDate(val month: Int, val day: Int, val result: ParseResult.Success) : ContextParseResult()
    data class Record(val result: ParseResult) : ContextParseResult()
    data class PendingPeakValley(val pending: PendingElectric) : ContextParseResult()
    data class Error(val message: String) : ContextParseResult()
}

sealed class ParseResult {
    data class Success(
        val timestamp: LocalDateTime,
        val isElectric: Boolean,
        val electricTotal: Double? = null,
        val electricPeak: Double? = null,
        val electricValley: Double? = null,
        val isWater: Boolean,
        val waterTotal: Double? = null,
        val note: String? = null
    ) : ParseResult()

    data class Error(val message: String) : ParseResult()
}
