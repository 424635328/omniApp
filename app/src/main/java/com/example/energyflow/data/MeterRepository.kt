package com.example.energyflow.data

import kotlinx.coroutines.flow.Flow
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
        val successRecords = mutableListOf<MeterRecord>()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        results.forEach { result ->
            when (result) {
                is ParseResult.Success -> {
                    if (!force) {
                        val warning = anomalyDetector.checkParseResult(result)
                        if (warning != null) {
                            warnings.add(warning)
                        }
                        val spikeWarning = anomalyDetector.checkParseResultForSpike(result)
                        if (spikeWarning != null) {
                            warnings.add(spikeWarning)
                        }
                    }
                    val record = result.toMeterRecord()
                    meterRecordDao.insert(record)
                    successRecords.add(record)
                }
                is ParseResult.Error -> {
                    errors.add(result.message)
                }
            }
        }

        if (successRecords.isNotEmpty()) {
            classifier.reLearn()
        }

        return when {
            errors.isEmpty() && warnings.isEmpty() -> BatchInsertResult.Success(successRecords.size)
            errors.isEmpty() -> BatchInsertResult.SuccessWithWarnings(successRecords.size, warnings)
            else -> BatchInsertResult.PartialSuccess(successRecords.size, errors)
        }
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
    data class SuccessWithWarnings(val count: Int, val warnings: List<String>) : BatchInsertResult()
    data class PartialSuccess(val successCount: Int, val errors: List<String>) : BatchInsertResult()
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
