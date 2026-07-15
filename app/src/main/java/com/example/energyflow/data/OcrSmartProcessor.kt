package com.example.energyflow.data

/**
 * OCR 智能处理引擎 — 启发式规则清洗和推断杂乱 OCR 文本。
 * 根治：条形码误判、日期干扰、相似字符误读、无标签模糊读数。
 */
object OcrSmartProcessor {

    /**
     * 智能提取电表/水表读数，返回 SmartInputParser 可直接解析的结构化文本。
     */
    fun process(rawText: String): String {
        // 1. OCR 常见错误纠正：数字中的 O→0, l/I→1
        var cleanText = rawText
            .replace(Regex("""(?<=\d)[oO](?=\d|$)"""), "0")
            .replace(Regex("""(?<=\d)[lI](?=\d|$)"""), "1")

        val lines = cleanText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var electricReading: String? = null
        var waterReading: String? = null

        // 匹配合法读数：最多6位整数，可带1-3位小数 (如 12345.67)
        val readingRegex = """\b\d{1,6}(?:\.\d{1,3})?\b""".toRegex()
        // 匹配日期特征 (如 2026-07-15, 2026.07)
        val dateRegex = """20[1-3]\d[-/.年][0-1]?\d""".toRegex()

        for (line in lines) {
            val lowerLine = line.lowercase()

            // 2. 过滤绝对的噪音行 (编号、代码、出厂日期、时间)
            if (lowerLine.contains("编号") || lowerLine.contains("no.") ||
                lowerLine.contains("代码") || dateRegex.containsMatchIn(lowerLine) ||
                lowerLine.contains(":")  // 通常是时间如 14:30
            ) {
                continue
            }

            // 在当前行寻找所有可能是读数的数字
            val candidates = readingRegex.findAll(line).map { it.value }.toList()

            // 3. 过滤纯长整数（条形码），保留带小数或较短的整数
            val validNumbers = candidates.filter {
                it.contains(".") || (it.length in 1..6 && !it.startsWith("202"))
            }

            if (validNumbers.isEmpty()) continue

            // 同行最后一个数字通常是真正的读数
            val targetNum = validNumbers.last()

            // 4. 根据同行上下文精准分类
            if (lowerLine.contains("电") || lowerLine.contains("kwh") ||
                lowerLine.contains("度") || lowerLine.contains("千瓦时")) {
                if (electricReading == null) electricReading = targetNum
            } else if (lowerLine.contains("水") || lowerLine.contains("m3") ||
                lowerLine.contains("m³") || lowerLine.contains("吨") || lowerLine.contains("t")) {
                if (waterReading == null) waterReading = targetNum
            }
        }

        // 5. 组合结果
        val today = java.time.LocalDate.now()
        val datePrefix = "${today.monthValue}.${today.dayOfMonth}"

        if (electricReading != null || waterReading != null) {
            val sb = StringBuilder("$datePrefix\n")
            if (electricReading != null) sb.append("电表 ").append(electricReading).append(" ")
            if (waterReading != null) sb.append("水表 ").append(waterReading).append(" ")
            return sb.toString().trim()
        }

        // 6. 终极 Fallback：寻找最大的浮点数
        val allNumbers = readingRegex.findAll(cleanText).map { it.value }.toList()
        val floatNumbers = allNumbers.filter { it.contains(".") }
        if (floatNumbers.isNotEmpty()) {
            val maxFloat = floatNumbers.maxByOrNull { it.toDoubleOrNull() ?: 0.0 }
            return "$datePrefix\n读数: $maxFloat"
        }

        // 找不到数字，返回清理后的原文
        return cleanText.replace("\n", " ").take(30)
    }
}
