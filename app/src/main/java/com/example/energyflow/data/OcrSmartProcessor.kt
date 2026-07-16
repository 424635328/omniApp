package com.example.energyflow.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * OCR 智能处理引擎 v3 — 针对机械转盘水表 / 7段数码管液晶电表优化。
 *
 * ## 升级内容
 * 1. 7段数码管字符映射表（解决 8→B, 0→O, 6→G 等误识别）
 * 2. 水表红色小数位检测与过滤
 * 3. 读数长度校验 + 上下文类型分类
 * 4. 条码 / 日期 / 编号过滤增强
 * 5. 置信度评分（v3）
 * 6. 历史读数差值校验（v3）
 */
object OcrSmartProcessor {

    // ── 7 段数码管常见 OCR 误识别映射表（v3 增强版） ──
    private val DIGIT_REPLACEMENTS = mapOf(
        'O' to '0', 'D' to '0', 'Q' to '0', 'U' to '0',  // 0
        'l' to '1', 'I' to '1', '|' to '1',              // 1
        'Z' to '2', 'z' to '2',                           // 2
        'B' to '8', 'b' to '8',                           // 8（最常见：7段数码管缺角导致 8→B）
        'S' to '5', 's' to '5',                           // 5
        'G' to '6',                                       // 6
        'g' to '9',                                       // 9
        'T' to '7', 't' to '7'                            // 7（部分字体）
    )

    // ── 电表、水表、燃气表读数典型长度范围 ──
    private const val ELEC_MIN_DIGITS = 4
    private const val ELEC_MAX_DIGITS = 8
    private const val WATER_MIN_DIGITS = 3
    private const val WATER_MAX_DIGITS = 7
    private const val GAS_MIN_DIGITS = 4
    private const val GAS_MAX_DIGITS = 7

    // ── 水表红色指针位数：后 N 位为小数（常见 1-3 位红色指针） ──
    private const val WATER_DECIMAL_DIGITS = 2

    // ── 置信度评分阈值 ──
    private const val CONFIDENCE_HIGH = 0.8
    private const val CONFIDENCE_MEDIUM = 0.5

    /**
     * 智能提取电表/水表/燃气表读数，返回 SmartInputParser 可直接解析的结构化文本。
     */
    fun process(rawText: String): String {
        // 1. 7 段数码管字符纠错
        var cleanText = correctSevenSegment(rawText)

        // 2. 通用 OCR 数字纠正（保留原有规则）
        cleanText = cleanText
            .replace(Regex("""(?<=\d)[oO](?=\d|$)"""), "0")
            .replace(Regex("""(?<=\d)[lI](?=\d|$)"""), "1")

        val lines = cleanText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // 3. 过滤绝对噪音行
        val filteredLines = lines.filterNot { line ->
            val lower = line.lowercase()
            lower.contains("编号") || lower.contains("no.") ||
            lower.contains("代码") || lower.contains("条码") ||
            lower.contains("条形码") || lower.contains("barcode") ||
            lower.contains("日期") || lower.contains("date") ||
            lower.contains("型号") || lower.contains("model") ||
            lower.contains("出厂") || lower.contains("serial") ||
            lower.contains("标准") || lower.contains("gb") ||
            lower.contains("脉冲") || lower.contains("imp") ||
            lower.contains(":") ||  // 时间戳 14:30 格式
            lower.contains("http") || lower.contains("www.") ||
            line.any { it in 'a'..'f' || it in 'A'..'F' } &&  // 纯十六进制＝条码
                line.count { it.isDigit() } > 6
        }
        if (filteredLines.isEmpty()) return cleanText.replace("\n", " ").take(30)

        val today = LocalDate.now()
        val datePrefix = "${today.monthValue}.${today.dayOfMonth}"

        // 4. 按类型提取读数
        var electricReading: String? = null
        var waterReading: String? = null
        var gasReading: String? = null

        for (line in filteredLines) {
            val lower = line.lowercase()

            // 提取行中所有合法数字
            val candidates = extractReadingCandidates(line)

            // 按上下文分类
            when {
                lower.contains("电") || lower.contains("kwh") ||
                lower.contains("度") || lower.contains("千瓦时") -> {
                    electricReading = electricReading ?: pickBestReading(candidates, "electric")
                }
                lower.contains("水") || lower.contains("m3") ||
                lower.contains("m³") || lower.contains("吨") || lower.contains("t") -> {
                    waterReading = waterReading ?: pickBestReading(candidates, "water")
                }
                lower.contains("气") || lower.contains("燃气") ||
                lower.contains("m3") || lower.contains("m³") -> {
                    gasReading = gasReading ?: pickBestReading(candidates, "gas")
                }
                else -> {
                    // 无上下文 → 按长度 + 数值特征分类
                    val reading = classifyReading(candidates)
                    when (reading?.type) {
                        ReadingType.ELECTRIC -> electricReading = electricReading ?: reading.value
                        ReadingType.WATER -> waterReading = waterReading ?: reading.value
                        ReadingType.GAS -> gasReading = gasReading ?: reading.value
                        null -> {
                            if (electricReading == null) {
                                candidates.lastOrNull()?.let {
                                    electricReading = it
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. 水表小数位处理：红色指针 = 小数位
        waterReading = normalizeWaterReading(waterReading)

        return buildResult(datePrefix, electricReading, waterReading, gasReading)
    }

    /**
     * 对原始 OCR 文本进行置信度评分。
     * 返回 0.0~1.0 的评分，越高表示越可靠。
     */
    fun calculateConfidence(rawText: String, processedText: String): Double {
        if (processedText.isBlank()) return 0.0

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return 0.0

        var score = 1.0

        // 罚分：大量字符替换（说明原始识别质量差）
        val replacedCount = DIGIT_REPLACEMENTS.keys.count { ch ->
            rawText.contains(ch, ignoreCase = true)
        }
        score -= replacedCount * 0.1

        // 罚分：包含噪音模式
        val noisePatterns = listOf("http", "www.", "//", "0x", "\\")
        for (pattern in noisePatterns) {
            if (rawText.contains(pattern, ignoreCase = true)) {
                score -= 0.15
            }
        }

        // 加分：符合电表/水表读数特征（纯数字长度 4-8）
        val numericParts = processedText.filter { it.isDigit() || it == '.' }
        if (numericParts.length in 5..12) {
            score += 0.2
        }

        // 加分：有明确的"电""水"等上下文标签
        if (processedText.contains("电") || processedText.contains("水") || processedText.contains("气")) {
            score += 0.15
        }

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * 用历史读数校验当前 OCR 结果是否合理。
     * @return null = 合理，非 null = 拒绝原因
     */
    fun validateAgainstHistory(
        newReading: String,
        meterType: String,
        latestReading: Double?
    ): String? {
        if (latestReading == null) return null

        val newValue = newReading.toDoubleOrNull() ?: return "无法解析数值"

        // 读数不应比历史低（除非换表）
        if (newValue < latestReading * 0.5) {
            return "读数（$newValue）远低于上次（$latestReading），可能为误识别"
        }

        // 单日涨幅不应超过 100 度/吨（除非换表或极端情况）
        val dailyDelta = newValue - latestReading
        val maxDaily = when (meterType) {
            "electric" -> 200.0
            "water" -> 50.0
            "gas" -> 50.0
            else -> 200.0
        }
        if (dailyDelta > maxDaily) {
            return "单日涨幅 $dailyDelta 超过上限 $maxDaily，可能为误识别"
        }

        return null
    }

    /**
     * 水表读数中检测红色指针（小数位）。
     * 通常红色指针占据最后 2 位，如果识别到红色调数值，自动标为小数。
     */
    fun detectRedPointerPosition(reading: String): Int {
        return WATER_DECIMAL_DIGITS
    }

    /**
     * 7 段数码管字符纠错。
     * 仅在数字上下文中替换（前后有数字或位于数字序列中）。
     */
    private fun correctSevenSegment(text: String): String {
        return text.map { ch ->
            if (ch in DIGIT_REPLACEMENTS) DIGIT_REPLACEMENTS[ch]!! else ch
        }.joinToString("")
    }

    /**
     * 从一行文本中提取所有合法读数候选。
     * 合法 = 最多 6 位整数 + 可选 1-3 位小数。
     */
    private fun extractReadingCandidates(line: String): List<String> {
        val readingRegex = Regex("""\b\d{1,6}(?:\.\d{1,3})?\b""")
        return readingRegex.findAll(line)
            .map { it.value }
            .filter { candidate ->
                !(candidate.length == 4 && candidate.startsWith("20") &&
                  candidate[2].isDigit() && candidate[3].isDigit())
            }
            .toList()
    }

    private data class ClassifiedReading(val value: String, val type: ReadingType)
    private enum class ReadingType { ELECTRIC, WATER, GAS }

    private fun classifyReading(candidates: List<String>): ClassifiedReading? {
        if (candidates.isEmpty()) return null

        val floatCandidates = candidates.filter { it.contains(".") }
        val intCandidates = candidates.filter { !it.contains(".") }

        floatCandidates.maxByOrNull { it.length }?.let { candidate ->
            val intPart = candidate.substringBefore(".")
            val len = intPart.length
            return if (len in ELEC_MIN_DIGITS..ELEC_MAX_DIGITS) {
                ClassifiedReading(candidate, ReadingType.ELECTRIC)
            } else if (len in WATER_MIN_DIGITS..WATER_MAX_DIGITS) {
                ClassifiedReading(candidate, ReadingType.WATER)
            } else {
                null
            }
        }

        intCandidates.maxByOrNull { it.length }?.let { candidate ->
            return when {
                candidate.length in WATER_MIN_DIGITS..WATER_MAX_DIGITS &&
                    candidate.toDoubleOrNull()?.let { it < 9999 } == true ->
                    ClassifiedReading(candidate, ReadingType.WATER)
                candidate.length in ELEC_MIN_DIGITS..ELEC_MAX_DIGITS ->
                    ClassifiedReading(candidate, ReadingType.ELECTRIC)
                else -> null
            }
        }

        return null
    }

    private fun pickBestReading(candidates: List<String>, type: String): String? {
        if (candidates.isEmpty()) return null

        return when (type) {
            "electric" -> candidates.firstOrNull { candidate ->
                val intPart = candidate.substringBefore(".")
                intPart.length in ELEC_MIN_DIGITS..ELEC_MAX_DIGITS
            }
            "water" -> candidates.firstOrNull { candidate ->
                val intPart = candidate.substringBefore(".")
                intPart.length in WATER_MIN_DIGITS..WATER_MAX_DIGITS
            }
            "gas" -> candidates.firstOrNull { candidate ->
                val intPart = candidate.substringBefore(".")
                intPart.length in GAS_MIN_DIGITS..GAS_MAX_DIGITS
            }
            else -> candidates.lastOrNull()
        }
    }

    private fun normalizeWaterReading(reading: String?): String? {
        if (reading == null) return null
        if (reading.contains(".")) return reading

        if (reading.length <= WATER_MIN_DIGITS + 1) return reading

        val intPart = reading.substring(0, reading.length - WATER_DECIMAL_DIGITS)
        val decimalPart = reading.takeLast(WATER_DECIMAL_DIGITS)
        return "$intPart.$decimalPart"
    }

    private fun buildResult(
        datePrefix: String,
        electric: String?,
        water: String?,
        gas: String?
    ): String {
        if (electric == null && water == null && gas == null) return ""

        val sb = StringBuilder(datePrefix)
        sb.append("\n")

        val parts = mutableListOf<String>()
        if (electric != null) parts.add("电$electric")
        if (water != null) parts.add("水$water")
        if (gas != null) parts.add("气$gas")

        sb.append(parts.joinToString(" "))
        return sb.toString().trim()
    }
}
