package com.example.energyflow.data

import java.time.LocalDateTime
import java.time.Year

class SmartInputParser {
    companion object {
        private fun currentYear() = Year.now().value

        // 峰谷值待定范围（4000-9000，用于批量导入时的峰谷配对）
        private const val PENDING_PEAK_VALLEY_MIN = 4000.0
        private const val PENDING_PEAK_VALLEY_MAX = 9000.0
    }

    /**
     * 智能批量解析，支持上下文关联的多行数据。
     * @param thresholds 可选的自适应阈值，不传则使用默认值。
     */
    fun parseWithContext(input: String, thresholds: ClassificationThresholds? = null): List<ParseResult> {
        val lines = input.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val results = mutableListOf<ParseResult>()
        var currentMonth: Int? = null
        var currentDay: Int? = null
        var pendingElectric: PendingElectric? = null

        for (line in lines) {
            val result = parseLineWithContext(line, currentMonth, currentDay, pendingElectric, thresholds)

            when (result) {
                is ContextParseResult.DateHeader -> {
                    pendingElectric?.let { pe ->
                        if (currentMonth != null && currentDay != null) {
                            results.add(pe.toSuccess(currentMonth, currentDay))
                        } else {
                            results.add(ParseResult.Error("缺少日期上下文"))
                        }
                    }
                    currentMonth = result.month
                    currentDay = result.day
                    pendingElectric = null
                }
                is ContextParseResult.RecordWithDate -> {
                    pendingElectric?.let { pe ->
                        if (currentMonth != null && currentDay != null) {
                            results.add(pe.toSuccess(currentMonth, currentDay))
                        }
                    }
                    currentMonth = result.month
                    currentDay = result.day
                    pendingElectric = null
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
                    if (result.pending.peak != null && result.pending.valley != null) {
                        if (currentMonth != null && currentDay != null) {
                            results.add(result.pending.toSuccess(currentMonth, currentDay))
                        } else {
                            results.add(ParseResult.Error("缺少日期上下文"))
                        }
                        pendingElectric = null
                    } else {
                        pendingElectric?.let { pe ->
                            if (currentMonth != null && currentDay != null) {
                                results.add(pe.toSuccess(currentMonth, currentDay))
                            }
                        }
                        pendingElectric = result.pending
                    }
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
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            return if (isValidDate(month, day)) ContextParseResult.DateHeader(month, day)
            else ContextParseResult.Error("日期无效: $line")
        }

        // 模式2: 日期 + 时间 + 数值
        Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d{1,2})\.(\d{2})\s+(\d+\.?\d*)\s*(.*)$""").matchEntire(line)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val hour = match.groupValues[3].toInt()
            val minute = match.groupValues[4].toInt()
            if (!isValidDateTime(month, day, hour, minute)) return ContextParseResult.Error("日期或时间无效: $line")
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
            if (!isValidDateTime(month, day, hour, minute)) return@let null  // fall through => 后续 pattern 可能匹配
            val value = match.groupValues[5].toDoubleOrNull() ?: return@let null
            val note = match.groupValues[6].trim().ifEmpty { null }
            return ContextParseResult.RecordWithDate(month, day, classifyValue(month, day, hour, minute, value, note, thresholds))
        }

        // 模式4: 日期 + 电表 + 水表
        Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d+\.?\d*)\s+(\d+\.?\d*)\s*(.*)$""").matchEntire(line)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val value1 = match.groupValues[3].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            val value2 = match.groupValues[4].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            if (!isValidDate(month, day)) return@let null  // fall through => 后续 pattern 可能匹配
            val note = match.groupValues[5].trim().ifEmpty { null }
            // 较大的是电表，较小的是水表
            val (electric, water) = if (value1 > value2) value1 to value2 else value2 to value1
            return ContextParseResult.RecordWithDate(
                month, day,
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear(), month, day, 12, 0),
                    isElectric = true, electricTotal = electric,
                    isWater = true, waterTotal = water,
                    note = note
                )
            )
        }

        // 模式5: 日期 + 中文时间 + 备注。先按中文数词解析，避免“二十分”被逐字替换成 210 分。
        Regex("""^(\d{1,2})\.(\d{1,2})\s*(上午|下午)?([零〇一二两三四五六七八九十壹贰叁肆伍陆柒捌玖拾]+)[点时]([零〇一二两三四五六七八九十壹贰叁肆伍陆柒捌玖拾]*)分?\s*(.*)$""").matchEntire(line)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val period = match.groupValues[3]
            var hour = chineseNumber(match.groupValues[4]) ?: return ContextParseResult.Error("时间格式错误: $line")
            val minute = match.groupValues[5].takeIf { it.isNotBlank() }?.let(::chineseNumber) ?: 0
            val note = match.groupValues[6].trim().ifEmpty { null }
            if (period == "下午" && hour < 12) hour += 12
            if (period == "上午" && hour == 12) hour = 0
            if (!isValidDateTime(month, day, hour, minute)) return ContextParseResult.Error("日期或时间无效: $line")
            return ContextParseResult.RecordWithDate(
                month, day,
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear(), month, day, hour, minute),
                    isElectric = false, isWater = false, note = note
                )
            )
        }

        // 模式5 兼容阿拉伯数字和旧输入格式。
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
            if (!isValidDateTime(month, day, hour, minute)) return ContextParseResult.Error("日期或时间无效: $line")
            return ContextParseResult.RecordWithDate(
                month, day,
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear(), month, day, hour, minute),
                    isElectric = false, electricTotal = null,
                    isWater = false, waterTotal = null,
                    note = note
                )
            )
        }

        // 模式5b: 日期 + 时间 无数值
        Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d{1,2})\.(\d{2})$""").matchEntire(line)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val hour = match.groupValues[3].toInt()
            val minute = match.groupValues[4].toInt()
            return if (isValidDateTime(month, day, hour, minute)) ContextParseResult.DateHeader(month, day)
            else ContextParseResult.Error("日期或时间无效: $line")
        }

        // 模式6: 日期 + 时间 + 备注
        Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d{1,2})\.(\d{2})\s+(\D.+)$""").matchEntire(line)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val hour = match.groupValues[3].toInt()
            val minute = match.groupValues[4].toInt()
            if (!isValidDateTime(month, day, hour, minute)) return ContextParseResult.Error("日期或时间无效: $line")
            val note = match.groupValues[5].trim()
            return ContextParseResult.RecordWithDate(
                month, day,
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear(), month, day, hour, minute),
                    isElectric = false, electricTotal = null,
                    isWater = false, waterTotal = null,
                    note = note
                )
            )
        }

        // 模式6b: 时间 + 标记值（电/水/气/峰/谷）[备注]
        // 导出的格式化行：14.30 电12345 水67.89 气12.34 峰678 谷901 备注
        Regex("""^(\d{1,2})\.(\d{2})\s+((?:[电水气峰谷]\d+\.?\d*\s*)+)(.*)$""").matchEntire(line)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val valuesPart = match.groupValues[3]
            val note = match.groupValues[4].trim().ifEmpty { null }
            if (currentMonth == null || currentDay == null) return ContextParseResult.Error("缺少日期上下文")
            if (!isValidTime(hour, minute)) return ContextParseResult.Error("时间无效: $line")

            val electric = Regex("""电(\d+\.?\d*)""").find(valuesPart)?.groupValues?.get(1)?.toDoubleOrNull()
            val water = Regex("""水(\d+\.?\d*)""").find(valuesPart)?.groupValues?.get(1)?.toDoubleOrNull()
            val gas = Regex("""气(\d+\.?\d*)""").find(valuesPart)?.groupValues?.get(1)?.toDoubleOrNull()
            val peak = Regex("""峰(\d+\.?\d*)""").find(valuesPart)?.groupValues?.get(1)?.toDoubleOrNull()
            val valley = Regex("""谷(\d+\.?\d*)""").find(valuesPart)?.groupValues?.get(1)?.toDoubleOrNull()

            return ContextParseResult.Record(
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear(), currentMonth, currentDay, hour, minute),
                    isElectric = electric != null || peak != null || valley != null,
                    electricTotal = electric,
                    electricPeak = peak,
                    electricValley = valley,
                    isWater = water != null,
                    waterTotal = water,
                    isGas = gas != null,
                    gasTotal = gas,
                    note = note?.let { extractPeakValleyFromNote(it).third }
                )
            )
        }

        // 模式7a: 时间 + 电表 + 水表 [备注]
        // 必须在模式7之前，否则"12.00 16639 880 两家"会被模式7吞成"时间+单值+备注"
        Regex("""^(\d{1,2})\.(\d{2})\s+(\d+\.?\d*)\s+(\d+\.?\d*)\s*(.*)$""").matchEntire(line)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val value1 = match.groupValues[3].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            val value2 = match.groupValues[4].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            if (currentMonth == null || currentDay == null) return ContextParseResult.Error("缺少日期上下文")
            if (!isValidTime(hour, minute)) return ContextParseResult.Error("时间无效: $line")
            val note = match.groupValues[5].trim().ifEmpty { null }
            // 较大的是电表，较小的是水表（与模式4一致）
            val (electric, water) = if (value1 > value2) value1 to value2 else value2 to value1
            return ContextParseResult.Record(
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear(), currentMonth, currentDay, hour, minute),
                    isElectric = true, electricTotal = electric,
                    isWater = true, waterTotal = water,
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
            if (!isValidTime(hour, minute)) return ContextParseResult.Error("时间无效: $line")
            return ContextParseResult.Record(classifyValue(currentMonth, currentDay, hour, minute, value, note, thresholds))
        }

        // 模式8: 紧凑时间 + 数值
        Regex("""^(\d{2})(\d{2})\s+(\d+\.?\d*)\s*(.*)$""").matchEntire(line)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val value = match.groupValues[3].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            val note = match.groupValues[4].trim().ifEmpty { null }
            if (currentMonth == null || currentDay == null) return ContextParseResult.Error("缺少日期上下文")
            if (!isValidTime(hour, minute)) return ContextParseResult.Error("时间无效: $line")
            return ContextParseResult.Record(classifyValue(currentMonth, currentDay, hour, minute, value, note, thresholds))
        }

        // 模式9: 时间 + 备注
        Regex("""^(\d{1,2})\.(\d{2})\s*(\D.+)$""").matchEntire(line)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val note = match.groupValues[3].trim()
            if (currentMonth == null || currentDay == null) return ContextParseResult.Error("缺少日期上下文")
            if (!isValidTime(hour, minute)) return ContextParseResult.Error("时间无效: $line")
            return ContextParseResult.Record(
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear(), currentMonth, currentDay, hour, minute),
                    isElectric = false, electricTotal = null,
                    isWater = false, waterTotal = null,
                    note = note
                )
            )
        }

        // 模式9a: 时间 + 水表标记
        Regex("""^(\d{1,2})\.(\d{2})\s+水(\d+\.?\d*)\s*(.*)$""").matchEntire(line)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val value = match.groupValues[3].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            if (currentMonth == null || currentDay == null) return ContextParseResult.Error("缺少日期上下文")
            if (!isValidTime(hour, minute)) return ContextParseResult.Error("时间无效: $line")
            val note = match.groupValues[4].trim().ifEmpty { null }
            return ContextParseResult.Record(
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear(), currentMonth, currentDay, hour, minute),
                    isElectric = false, electricTotal = null,
                    isWater = true, waterTotal = value,
                    note = note
                )
            )
        }

        // 模式9b: 时间 + 燃气标记
        Regex("""^(\d{1,2})\.(\d{2})\s+气(\d+\.?\d*)\s*(.*)$""").matchEntire(line)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val value = match.groupValues[3].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            if (currentMonth == null || currentDay == null) return ContextParseResult.Error("缺少日期上下文")
            if (!isValidTime(hour, minute)) return ContextParseResult.Error("时间无效: $line")
            val note = match.groupValues[4].trim().ifEmpty { null }
            return ContextParseResult.Record(
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear(), currentMonth, currentDay, hour, minute),
                    isElectric = false, electricTotal = null,
                    isWater = false,
                    isGas = true, gasTotal = value,
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
                    timestamp = LocalDateTime.of(currentYear(), currentMonth, currentDay, 12, 0),
                    isElectric = false, electricTotal = null,
                    isWater = true, waterTotal = value,
                    note = null
                )
            )
        }
        // 模式10b: 燃气前缀
        Regex("""^气\s*(\d+\.?\d*)$""").matchEntire(line)?.let { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            if (currentMonth == null || currentDay == null) return ContextParseResult.Error("缺少日期上下文")
            return ContextParseResult.Record(
                ParseResult.Success(
                    timestamp = LocalDateTime.of(currentYear(), currentMonth, currentDay, 12, 0),
                    isElectric = false, electricTotal = null,
                    isWater = false,
                    isGas = true, gasTotal = value,
                    note = null
                )
            )
        }

        // 模式11: 纯数值（智能识别）
        Regex("""^(\d+\.?\d*)$""").matchEntire(line)?.let { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return ContextParseResult.Error("数值格式错误")
            if (currentMonth == null || currentDay == null) return ContextParseResult.Error("缺少日期上下文: $line")

            val t = thresholds ?: ClassificationThresholds.DEFAULTS
            val isPeak = value in t.peakMin..t.peakMax
            val isValley = value in t.valleyMin..t.valleyMax
            if (isPeak || isValley || value in PENDING_PEAK_VALLEY_MIN..PENDING_PEAK_VALLEY_MAX) {
                val next = when {
                    isPeak -> PendingElectric(peak = value, valley = pendingElectric?.valley, total = null)
                    isValley -> PendingElectric(peak = pendingElectric?.peak, valley = value, total = null)
                    pendingElectric == null -> PendingElectric(peak = value, valley = null, total = null)
                    else -> pendingElectric.copy(valley = value, total = null)
                }
                if (next.peak != null && next.valley != null) {
                    return ContextParseResult.PendingPeakValley(next.copy(total = next.peak + next.valley))
                }
                return ContextParseResult.PendingPeakValley(next)
            }
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
    /** 从备注中提取 峰X 谷X 并返回清洗后文本（移除已提取的峰谷标记） */
    private fun extractPeakValleyFromNote(note: String?): Triple<Double?, Double?, String?> {
        if (note == null) return Triple(null, null, null)
        val peak = Regex("""峰(\d+\.?\d*)""").find(note)?.groupValues?.get(1)?.toDoubleOrNull()
        val valley = Regex("""谷(\d+\.?\d*)""").find(note)?.groupValues?.get(1)?.toDoubleOrNull()
        val cleaned = if (peak != null || valley != null)
            note.replace(Regex("[峰谷]\\d+\\.?\\d*"), "").trim().ifEmpty { null }
        else note
        return Triple(peak, valley, cleaned)
    }

    private fun classifyValue(
        month: Int, day: Int, hour: Int, minute: Int,
        value: Double, note: String?,
        thresholds: ClassificationThresholds? = null
    ): ParseResult.Success {
        val t = thresholds ?: ClassificationThresholds.DEFAULTS
        val timestamp = LocalDateTime.of(currentYear(), month, day, hour, minute)

        // 先从备注提取峰谷值
        val (notePeak, noteValley, cleanedNote) = extractPeakValleyFromNote(note)

        val result = when {
            // 水表
            value < t.waterMax -> ParseResult.Success(
                timestamp = timestamp,
                isElectric = false, electricTotal = null,
                isWater = true, waterTotal = value,
                note = cleanedNote
            )
            // 峰电（同时提取备注中的谷值）
            value in t.peakMin..t.peakMax -> ParseResult.Success(
                timestamp = timestamp,
                isElectric = true, electricTotal = value, electricPeak = value,
                electricValley = noteValley,
                isWater = false, waterTotal = null,
                note = cleanedNote
            )
            // 谷电（同时提取备注中的峰值）
            value in t.valleyMin..t.valleyMax -> ParseResult.Success(
                timestamp = timestamp,
                isElectric = true, electricTotal = value, electricValley = value,
                electricPeak = notePeak,
                isWater = false, waterTotal = null,
                note = cleanedNote
            )
            // 总电（提取备注中的峰谷值）
            value >= t.totalElectricMin -> ParseResult.Success(
                timestamp = timestamp,
                isElectric = true, electricTotal = value,
                electricPeak = notePeak, electricValley = noteValley,
                isWater = false, waterTotal = null,
                note = cleanedNote
            )
            // 中间值：默认为总电
            else -> ParseResult.Success(
                timestamp = timestamp,
                isElectric = true, electricTotal = value,
                electricPeak = notePeak, electricValley = noteValley,
                isWater = false, waterTotal = null,
                note = cleanedNote
            )
        }
        return result
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
        // 处理 "X十Y" 模式：如 "三十五" → "3十5" → "35"
        result = result.replace(Regex("(\\d)十(\\d)"), "$1$2")
        // 处理 "X十" 模式（无个位）：如 "三十" → "3十" → "30"
        result = result.replace(Regex("(\\d)十"), "$10")
        // 处理 "十Y" 模式（无十位）：如 "十五" → "105" → "15"
        result = result.replace(Regex("10(\\d)"), "1$1")
        return result
    }

    private fun chineseNumber(text: String): Int? {
        if (text.isBlank()) return null
        val digits = mapOf(
            '零' to 0, '〇' to 0, '一' to 1, '壹' to 1, '二' to 2, '贰' to 2, '两' to 2,
            '三' to 3, '叁' to 3, '四' to 4, '肆' to 4, '五' to 5, '伍' to 5, '六' to 6,
            '陆' to 6, '七' to 7, '柒' to 7, '八' to 8, '捌' to 8, '九' to 9, '玖' to 9
        )
        if (text.length == 1 && text[0] != '十' && text[0] != '拾') return digits[text[0]]
        val tenIndex = text.indexOfFirst { it == '十' || it == '拾' }
        if (tenIndex < 0) return null
        val tens = if (tenIndex == 0) 1 else digits[text[tenIndex - 1]] ?: return null
        val ones = if (tenIndex == text.lastIndex) 0 else digits[text[tenIndex + 1]] ?: return null
        return tens * 10 + ones
    }

    private fun isValidDate(month: Int, day: Int): Boolean = runCatching {
        LocalDateTime.of(currentYear(), month, day, 0, 0)
    }.isSuccess

    private fun isValidTime(hour: Int, minute: Int): Boolean = hour in 0..23 && minute in 0..59

    private fun isValidDateTime(month: Int, day: Int, hour: Int, minute: Int): Boolean =
        isValidDate(month, day) && isValidTime(hour, minute)
}

data class PendingElectric(
    val peak: Double?,
    val valley: Double?,
    val total: Double?
) {
    fun toSuccess(month: Int, day: Int): ParseResult.Success {
        val computedTotal = total ?: if (peak != null && valley != null) peak + valley else null
        return ParseResult.Success(
            timestamp = LocalDateTime.of(Year.now().value, month, day, 12, 0),
            isElectric = true,
            electricTotal = computedTotal,
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
        val isGas: Boolean = false,
        val gasTotal: Double? = null,
        val note: String? = null
    ) : ParseResult()

    data class Error(val message: String) : ParseResult()
}
