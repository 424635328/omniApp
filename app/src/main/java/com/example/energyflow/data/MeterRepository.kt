package com.example.energyflow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeterRepository @Inject constructor(
    private val meterRecordDao: MeterRecordDao,
    private val classifier: AdaptiveClassifier,
    private val anomalyDetector: AnomalyDetector
) {
    private val parser = SmartInputParser()

    fun getAllRecords(): Flow<List<MeterRecord>> = meterRecordDao.getAllRecords()

    fun getRecordsByTimeRange(startTime: LocalDateTime, endTime: LocalDateTime): Flow<List<MeterRecord>> =
        meterRecordDao.getRecordsByTimeRange(startTime, endTime)

    suspend fun getLatestRecord(): MeterRecord? = meterRecordDao.getLatestRecord()

    suspend fun getPreviousRecord(currentTime: LocalDateTime): MeterRecord? =
        meterRecordDao.getPreviousRecord(currentTime)

    fun getElectricRecords(): Flow<List<MeterRecord>> = meterRecordDao.getElectricRecords()

    fun getWaterRecords(): Flow<List<MeterRecord>> = meterRecordDao.getWaterRecords()

    fun getRecordsWithNotes(): Flow<List<MeterRecord>> = meterRecordDao.getRecordsWithNotes()

    fun getRecordCount(): Flow<Int> = meterRecordDao.getRecordCount()

    /**
     * 智能插入，带自适应阈值 + 单调递增 + 突增校验。
     */
    suspend fun smartInsert(input: String, force: Boolean = false): InsertResult {
        val thresholds = classifier.getThresholds()
        val results = parser.parseWithContext(input, thresholds)
        val successes = results.filterIsInstance<ParseResult.Success>()

        if (successes.isEmpty()) {
            val errors = results.filterIsInstance<ParseResult.Error>()
            return InsertResult.Error(errors.firstOrNull()?.message ?: "解析失败")
        }

        val result = successes.first()

        // 非强制插入时做异常校验
        if (!force) {
            val warning = anomalyDetector.checkParseResult(result)
            if (warning != null) {
                return InsertResult.Warning(warning)
            }
            val spikeWarning = anomalyDetector.checkParseResultForSpike(result)
            if (spikeWarning != null) {
                return InsertResult.Warning(spikeWarning)
            }
        }

        val record = result.toMeterRecord()
        val id = meterRecordDao.insert(record)
        classifier.reLearn() // 异步重学阈值
        return InsertResult.Success(id, record)
    }

    /**
     * 批量导入，带自适应阈值 + 校验。
     */
    suspend fun batchInsert(input: String, force: Boolean = false): BatchInsertResult {
        val thresholds = classifier.getThresholds()
        val results = parser.parseWithContext(input, thresholds)
        val successes = results.filterIsInstance<ParseResult.Success>()
        val parseErrors = results.filterIsInstance<ParseResult.Error>().map { it.message }

        if (!force) {
            val warnings = validateBatchCandidates(successes)
            if (warnings.isNotEmpty()) {
                return BatchInsertResult.Warning(warnings)
            }
        }

        val successRecords = mutableListOf<MeterRecord>()

        results.forEach { result ->
            when (result) {
                is ParseResult.Success -> {
                    successRecords.add(result.toMeterRecord())
                }
                is ParseResult.Error -> Unit
            }
        }

        // ═══ 插值填补日期缺口 ═══
        val interpolatedRecords = interpolateGaps(successRecords)
        successRecords.addAll(interpolatedRecords)

        // 按时间排序后插入
        val allRecords = (successRecords).sortedBy { it.timestamp }
        allRecords.forEach { meterRecordDao.insert(it) }

        if (successRecords.isNotEmpty()) {
            classifier.reLearn()
        }

        return when {
            parseErrors.isEmpty() -> BatchInsertResult.Success(successRecords.size)
            else -> BatchInsertResult.PartialSuccess(successRecords.size, parseErrors)
        }
    }

    /**
     * 检测导入记录中的日期缺口并生成插值记录。
     *
     * 当两条电表记录间隔 N 天（N≥2），在中间每一天生成一条插值记录，
     * 电表读数均匀分布。水表同理（独立插值）。
     */
    private fun interpolateGaps(records: List<MeterRecord>): List<MeterRecord> {
        val electricRecords = records
            .filter { it.isElectricRecorded && it.electricTotal != null }
            .sortedBy { it.timestamp }

        val waterRecords = records
            .filter { it.isWaterRecorded && it.waterTotal != null }
            .sortedBy { it.timestamp }

        val result = mutableListOf<MeterRecord>()

        // 电表插值
        for (i in 0 until electricRecords.size - 1) {
            val prev = electricRecords[i]
            val curr = electricRecords[i + 1]
            val span = java.time.temporal.ChronoUnit.DAYS.between(
                prev.timestamp.toLocalDate(), curr.timestamp.toLocalDate()
            )
            if (span <= 1) continue

            val prevTotal = prev.electricTotal!!
            val currTotal = curr.electricTotal!!
            val totalDelta = currTotal - prevTotal
            val dailyStep = totalDelta / span

            for (d in 1 until span) {
                val interpDate = prev.timestamp.toLocalDate().plusDays(d)
                result.add(
                    MeterRecord(
                        timestamp = interpDate.atTime(12, 0),
                        isElectricRecorded = true,
                        electricTotal = prevTotal + dailyStep * d,
                        isWaterRecorded = false,
                        note = null  // 插值记录不加备注
                    )
                )
            }
        }

        // 水表插值
        for (i in 0 until waterRecords.size - 1) {
            val prev = waterRecords[i]
            val curr = waterRecords[i + 1]
            val span = java.time.temporal.ChronoUnit.DAYS.between(
                prev.timestamp.toLocalDate(), curr.timestamp.toLocalDate()
            )
            if (span <= 1) continue

            val prevTotal = prev.waterTotal!!
            val currTotal = curr.waterTotal!!
            val totalDelta = currTotal - prevTotal
            val dailyStep = totalDelta / span

            for (d in 1 until span) {
                val interpDate = prev.timestamp.toLocalDate().plusDays(d)
                result.add(
                    MeterRecord(
                        timestamp = interpDate.atTime(12, 0),
                        isElectricRecorded = false,
                        isWaterRecorded = true,
                        waterTotal = prevTotal + dailyStep * d,
                        note = null
                    )
                )
            }
        }

        return result
    }

    private suspend fun validateBatchCandidates(successes: List<ParseResult.Success>): List<String> {
        val warnings = mutableListOf<String>()
        successes.forEach { result ->
            anomalyDetector.checkParseResult(result)?.let(warnings::add)
            anomalyDetector.checkParseResultForSpike(result)?.let(warnings::add)
        }

        val candidates = successes.map { it.toMeterRecord() }
        val existingElectric = meterRecordDao.getElectricRecords().first()
        if (existingElectric.isNotEmpty()) {
            warnings += findBatchDropWarning(
                records = existingElectric + candidates.filter { it.isElectricRecorded },
                valueOf = { it.electricTotal },
                label = "当前读数"
            )
        }
        val existingWater = meterRecordDao.getWaterRecords().first()
        if (existingWater.isNotEmpty()) {
            warnings += findBatchDropWarning(
                records = existingWater + candidates.filter { it.isWaterRecorded },
                valueOf = { it.waterTotal },
                label = "当前水表读数"
            )
        }

        return warnings.filter { it.isNotBlank() }.distinct()
    }

    /**
     * 检查相邻记录之间的读数递减。
     * 只检查包含已有记录的对（previous 来自历史），导入候选内部的自有递减忽略，
     * 因为用户可能按任意顺序录入数据。
     */
    private fun findBatchDropWarning(
        records: List<MeterRecord>,
        valueOf: (MeterRecord) -> Double?,
        label: String
    ): List<String> {
        val indexed = records
            .filter { valueOf(it) != null }
            .sortedBy { it.timestamp }

        // 找到第一个来自历史记录的递减对（id > 0 表示已持久化的记录）
        val pair = indexed
            .zipWithNext()
            .firstOrNull { (previous, current) ->
                current.timestamp > previous.timestamp &&
                    previous.id > 0L &&
                    (valueOf(current) ?: 0.0) < (valueOf(previous) ?: 0.0)
            }
            ?: return emptyList()

        val previous = valueOf(pair.first) ?: return emptyList()
        val current = valueOf(pair.second) ?: return emptyList()
        return listOf("$label ${format(current)} 低于历史记录 ${format(previous)}，请检查是否输入错误")
    }

    suspend fun insert(record: MeterRecord): Long {
        val id = meterRecordDao.insert(record)
        classifier.reLearn()
        return id
    }

    suspend fun update(record: MeterRecord) {
        meterRecordDao.update(record)
        classifier.reLearn()
    }

    suspend fun delete(record: MeterRecord) = meterRecordDao.delete(record)

    suspend fun deleteAll() = meterRecordDao.deleteAll()

    /**
     * 根据当前和历史记录计算消耗量。
     */
    suspend fun calculateConsumption(currentRecord: MeterRecord): ConsumptionResult {
        val previousRecord = meterRecordDao.getPreviousRecord(currentRecord.timestamp)
            ?: return ConsumptionResult.NoPreviousRecord

        val electricConsumption = if (currentRecord.isElectricRecorded && previousRecord.isElectricRecorded) {
            val current = currentRecord.electricTotal ?: 0.0
            val previous = previousRecord.electricTotal ?: 0.0
            current - previous
        } else null

        val waterConsumption = if (currentRecord.isWaterRecorded && previousRecord.isWaterRecorded) {
            val current = currentRecord.waterTotal ?: 0.0
            val previous = previousRecord.waterTotal ?: 0.0
            current - previous
        } else null

        val daysBetween = Duration.between(previousRecord.timestamp, currentRecord.timestamp).toDays()

        return ConsumptionResult.Success(
            electricConsumption = electricConsumption,
            waterConsumption = waterConsumption,
            daysBetween = daysBetween,
            dailyElectricConsumption = electricConsumption?.let { if (daysBetween > 0) it / daysBetween else it },
            dailyWaterConsumption = waterConsumption?.let { if (daysBetween > 0) it / daysBetween else it }
        )
    }

    suspend fun refreshClassifier() {
        classifier.reLearn()
    }
}

private fun ParseResult.Success.toMeterRecord(): MeterRecord {
    return MeterRecord(
        timestamp = timestamp,
        isElectricRecorded = isElectric,
        electricTotal = electricTotal,
        electricPeak = electricPeak,
        electricValley = electricValley,
        isWaterRecorded = isWater,
        waterTotal = waterTotal,
        note = note
    )
}

sealed class InsertResult {
    data class Success(val id: Long, val record: MeterRecord) : InsertResult()
    data class Warning(val message: String) : InsertResult()
    data class Error(val message: String) : InsertResult()
}

sealed class BatchInsertResult {
    data class Success(val count: Int) : BatchInsertResult()
    data class Warning(val warnings: List<String>) : BatchInsertResult()
    data class PartialSuccess(val successCount: Int, val errors: List<String>) : BatchInsertResult()
}

private fun format(value: Double): String = if (value == value.toLong().toDouble()) {
    value.toLong().toString()
} else {
    "%.1f".format(value)
}

sealed class ConsumptionResult {
    data class Success(
        val electricConsumption: Double?,
        val waterConsumption: Double?,
        val daysBetween: Long,
        val dailyElectricConsumption: Double?,
        val dailyWaterConsumption: Double?
    ) : ConsumptionResult()

    object NoPreviousRecord : ConsumptionResult()
}
