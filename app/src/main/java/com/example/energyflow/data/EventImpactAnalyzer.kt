package com.example.energyflow.data

import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Quantifies usage during explicit appliance start/stop windows against a control period. */
@Singleton
class EventImpactAnalyzer @Inject constructor() {
    fun analyzeWithRecords(records: List<MeterRecord>): List<EventImpact> {
        val electricRecords = records
            .filter { it.isElectricRecorded && it.electricTotal != null }
            .sortedBy { it.timestamp }
        if (electricRecords.size < 3) return emptyList()

        val tags = records
            .flatMap { extractTags(it.note.orEmpty()) }
            .distinct()

        return tags.mapNotNull { tag -> analyzeTag(tag, records, electricRecords) }
    }

    /** Compatibility entry point for callers of the original analyzer. */
    fun analyzeAll(records: List<MeterRecord>): List<EventImpact> = analyzeWithRecords(records)

    private fun analyzeTag(
        tag: String,
        allRecords: List<MeterRecord>,
        electricRecords: List<MeterRecord>
    ): EventImpact? {
        val windows = eventWindows(tag, allRecords, electricRecords.last().timestamp)
        if (windows.isEmpty()) return null

        val active = UsageTotals()
        val control = UsageTotals()
        electricRecords.windowed(2).forEach { (from, to) ->
            val fromValue = from.electricTotal ?: return@forEach
            val toValue = to.electricTotal ?: return@forEach
            val usage = toValue - fromValue
            val days = Duration.between(from.timestamp, to.timestamp).toMinutes() / MINUTES_PER_DAY
            if (usage < 0.0 || days <= 0.0) return@forEach

            val midpoint = from.timestamp.plusMinutes((Duration.between(from.timestamp, to.timestamp).toMinutes() / 2.0).toLong())
            if (windows.any { midpoint >= it.start && midpoint < it.end }) {
                active.add(usage, days)
            } else {
                control.add(usage, days)
            }
        }
        if (active.days <= 0.0 || control.days <= 0.0) return null

        val activeDaily = active.kwh / active.days
        val controlDaily = control.kwh / control.days
        return EventImpact(
            tag = tag,
            eventDailyKwh = activeDaily,
            nonEventDailyKwh = controlDaily,
            deltaKwh = activeDaily - controlDaily,
            eventDays = active.days,
            nonEventDays = control.days
        )
    }

    private fun eventWindows(tag: String, records: List<MeterRecord>, end: LocalDateTime): List<TimeWindow> {
        val markers = records
            .filter { it.note?.contains(tag) == true }
            .sortedBy { it.timestamp }
        val windows = mutableListOf<TimeWindow>()
        var startedAt: LocalDateTime? = null
        markers.forEach { marker ->
            val note = marker.note.orEmpty()
            when {
                isStop(note) && startedAt != null -> {
                    if (marker.timestamp > startedAt) windows += TimeWindow(startedAt!!, marker.timestamp)
                    startedAt = null
                }
                isStart(note) && startedAt == null -> startedAt = marker.timestamp
            }
        }
        startedAt?.takeIf { it < end }?.let { windows += TimeWindow(it, end) }
        return windows
    }

    private fun extractTags(note: String): List<String> {
        val hashtagTags = HASH_TAG.findAll(note).map { it.groupValues[1] }.toList()
        val applianceTags = KNOWN_APPLIANCES.filter { note.contains(it) }
        return (hashtagTags + applianceTags).distinct()
    }

    private fun isStart(note: String): Boolean = START_WORDS.any(note::contains) && !isStop(note)
    private fun isStop(note: String): Boolean = STOP_WORDS.any(note::contains)

    private data class UsageTotals(var kwh: Double = 0.0, var days: Double = 0.0) {
        fun add(kwh: Double, days: Double) {
            this.kwh += kwh
            this.days += days
        }
    }

    private companion object {
        const val MINUTES_PER_DAY = 24.0 * 60.0
        val HASH_TAG = Regex("""#([^#\s，。；,;]+)""")
        val KNOWN_APPLIANCES = listOf("冰箱", "空调", "洗衣机", "烘干机", "热水器", "电暖器")
        val START_WORDS = listOf("打开", "开启", "开了", "启用", "开始", "使用")
        val STOP_WORDS = listOf("关闭", "关了", "停止", "停用", "结束", "不再", "关")
    }
}

data class EventImpact(
    val tag: String,
    val eventDailyKwh: Double,
    val nonEventDailyKwh: Double,
    val deltaKwh: Double,
    val eventDays: Double,
    val nonEventDays: Double
)

private data class TimeWindow(val start: LocalDateTime, val end: LocalDateTime)
