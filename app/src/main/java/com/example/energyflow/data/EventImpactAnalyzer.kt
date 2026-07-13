package com.example.energyflow.data

import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 事件归因分析器。
 *
 * 通过标签匹配，量化特定事件对能耗的影响。
 *
 * 场景：
 * - 用户在某记录备注了 "❄️开冰箱"，后续记录备注 "🔇关冰箱"
 * - App 自动计算：带有冰箱标签的时间段，日均耗电 8.5 度
 *   vs 无标签时段日均耗电 5.2 度 → 冰箱每天多消耗 3.3 度
 *
 * 匹配逻辑：
 * - 同一标签的"开"/"关"配对
 * - 跨多天或多周的事件窗口
 */
@Singleton
class EventImpactAnalyzer @Inject constructor() {

    /**
     * 分析所有带备注的记录，找出标签事件对能耗的影响。
     */
    fun analyzeAll(records: List<MeterRecord>): List<EventImpact> {
        val electricRecords = records
            .filter { it.isElectricRecorded && it.electricTotal != null }
            .sortedBy { it.timestamp }

        if (electricRecords.size < 2) return emptyList()

        // 提取所有唯一标签（emoji + 名称）
        val allNotes = records.mapNotNull { it.note }.filter { it.isNotBlank() }
        val tags = allNotes
            .flatMap { extractTags(it) }
            .distinct()

        return tags.mapNotNull { tag ->
            analyzeTag(tag, electricRecords)
        }
    }

    /**
     * 分析单个标签的能耗影响。
     */
    private fun analyzeTag(tag: String, records: List<MeterRecord>): EventImpact? {
        // 找到所有包含该标签的记录
        val taggedRecords = records.filter { it.note?.contains(tag) == true }
        if (taggedRecords.isEmpty()) return null

        // 构建事件窗口：相邻的带标签记录之间的区间
        val eventWindows = buildEventWindows(tag, taggedRecords)

        // 找到所有 NOT 被该标签覆盖的区间（对比组）
        val nonEventWindows = buildNonEventWindows(records, eventWindows)

        // 计算日均耗电
        val eventDailies = eventWindows.mapNotNull { dailyRate(it) }
        val nonEventDailies = nonEventWindows.mapNotNull { dailyRate(it) }

        if (eventDailies.isEmpty() || nonEventDailies.isEmpty()) return null

        val eventAvg = eventDailies.average()
        val nonEventAvg = nonEventDailies.average()
        val delta = eventAvg - nonEventAvg

        return EventImpact(
            tag = tag,
            eventDailyKwh = eventAvg,
            nonEventDailyKwh = nonEventAvg,
            deltaKwh = delta,
            eventDays = eventWindows.sumOf { it.durationDays },
            nonEventDays = nonEventWindows.sumOf { it.durationDays }
        )
    }

    /**
     * 提取标签：例如 "❄️开冰箱" → ["冰箱"]
     */
    private fun extractTags(note: String): List<String> {
        // 去掉常见前缀词，提取核心词
        val cleaned = note
            .replace(Regex("""[❄️🔇❄️👥🧺✅☀️]"""), "")
            .replace(Regex("""^(开了?|关(了?|闭)|启用|停用|开始|结束|停止|不再)"""), "")
            .trim()
        return if (cleaned.isNotBlank()) listOf(cleaned) else emptyList()
    }

    /**
     * 构建事件窗口：相邻带标签记录之间的区间
     */
    private fun buildEventWindows(tag: String, taggedRecords: List<MeterRecord>): List<TimeWindow> {
        val windows = mutableListOf<TimeWindow>()
        var i = 0
        while (i < taggedRecords.size) {
            val start = taggedRecords[i]
            // 是否配对？如果备注包含"关"或"停"，那就是结束点
            val isEnd = start.note?.let { n ->
                n.contains(tag) && (n.contains("关") || n.contains("停") || n.contains("结束") || n.contains("不再"))
            } ?: false

            if (isEnd && windows.isNotEmpty()) {
                // 关闭前面的配对事件
                val prev = windows.removeLast()
                windows.add(prev.copy(end = start.timestamp))
            } else if (i + 1 < taggedRecords.size) {
                // 事件窗口：这条到下一带同标签记录
                windows.add(TimeWindow(start.timestamp, taggedRecords[i + 1].timestamp))
            } else {
                // 最后一条记录：到整个数据集末尾
                val lastRecord = taggedRecords.last()
                windows.add(TimeWindow(start.timestamp, lastRecord.timestamp))
            }
            i++
        }
        return windows
    }

    /**
     * 构建非事件窗口
     */
    private fun buildNonEventWindows(
        allRecords: List<MeterRecord>,
        eventWindows: List<TimeWindow>
    ): List<TimeWindow> {
        if (allRecords.isEmpty()) return emptyList()
        if (eventWindows.isEmpty()) {
            return listOf(
                TimeWindow(
                    allRecords.first().timestamp,
                    allRecords.last().timestamp
                )
            )
        }

        // 简化：取事件窗口之外的完整记录段
        val result = mutableListOf<TimeWindow>()
        val sortedEvents = eventWindows.sortedBy { it.start }
        var cursor = allRecords.first().timestamp

        for (event in sortedEvents) {
            if (event.start.isAfter(cursor)) {
                result.add(TimeWindow(cursor, event.start))
            }
            cursor = event.end
        }
        if (cursor.isBefore(allRecords.last().timestamp)) {
            result.add(TimeWindow(cursor, allRecords.last().timestamp))
        }
        return result.filter { it.durationDays > 0 }
    }

    private fun dailyRate(window: TimeWindow): Double? {
        if (window.durationDays <= 0) return null
        // 无法获取精确消耗，返回 null 表示窗口不完整
        return null // 实际需要 Record 数据才能算精确值
    }

    /**
     * 基于记录的精确版日均耗电。
     */
    fun analyzeWithRecords(records: List<MeterRecord>): List<EventImpact> {
        val electricRecords = records
            .filter { it.isElectricRecorded && it.electricTotal != null }
            .sortedBy { it.timestamp }

        if (electricRecords.size < 2) return emptyList()

        val allNotes = records.mapNotNull { it.note }.filter { it.isNotBlank() }
        val tags = allNotes
            .flatMap { extractTags(it) }
            .distinct()

        return tags.mapNotNull { tag ->
            analyzeTagWithRealData(tag, electricRecords)
        }
    }

    private fun analyzeTagWithRealData(
        tag: String,
        records: List<MeterRecord>
    ): EventImpact? {
        val taggedRecords = records.filter { it.note?.contains(tag) == true }
        if (taggedRecords.isEmpty()) return null

        val taggedIds = taggedRecords.map { it.id }.toSet()
        val nonTaggedRecords = records.filter { it.id !in taggedIds }

        // 在有标签的区间内计算日均消耗
        val eventConsumptions = computeWindowConsumptions(taggedRecords)
        val nonEventConsumptions = computeWindowConsumptions(nonTaggedRecords)

        if (eventConsumptions.isEmpty() || nonEventConsumptions.isEmpty()) return null

        val eventAvg = eventConsumptions.average()
        val nonEventAvg = nonEventConsumptions.average()
        val delta = eventAvg - nonEventAvg

        return EventImpact(
            tag = tag,
            eventDailyKwh = eventAvg,
            nonEventDailyKwh = nonEventAvg,
            deltaKwh = delta,
            eventDays = taggedRecords.windowed(2).sumOf { (a, b) ->
                ChronoUnit.DAYS.between(a.timestamp, b.timestamp)
            },
            nonEventDays = nonTaggedRecords.windowed(2).sumOf { (a, b) ->
                ChronoUnit.DAYS.between(a.timestamp, b.timestamp)
            }
        )
    }

    /**
     * 计算记录序列中各相邻窗口的日均消耗。
     */
    private fun computeWindowConsumptions(records: List<MeterRecord>): List<Double> {
        if (records.size < 2) return emptyList()
        return records.sortedBy { it.timestamp }.windowed(2).mapNotNull { (prev, curr) ->
            val days = ChronoUnit.DAYS.between(prev.timestamp, curr.timestamp).coerceAtLeast(1)
            val consumption = (curr.electricTotal ?: 0.0) - (prev.electricTotal ?: 0.0)
            if (consumption > 0) consumption / days else null
        }
    }
}

data class EventImpact(
    val tag: String,
    val eventDailyKwh: Double,    // 事件时段日均耗电
    val nonEventDailyKwh: Double, // 非事件时段日均耗电
    val deltaKwh: Double,          // 差异
    val eventDays: Long,
    val nonEventDays: Long
)

private data class TimeWindow(
    val start: java.time.LocalDateTime,
    val end: java.time.LocalDateTime
) {
    val durationDays: Long
        get() = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0)
}
