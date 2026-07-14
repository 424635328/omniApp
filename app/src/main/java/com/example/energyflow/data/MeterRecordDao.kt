package com.example.energyflow.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface MeterRecordDao {
    // 插入记录
    @Insert
    suspend fun insert(record: MeterRecord): Long

    // 更新记录
    @Update
    suspend fun update(record: MeterRecord)

    // 删除记录
    @Delete
    suspend fun delete(record: MeterRecord)

    // 查询所有记录，按时间倒序
    @Query("SELECT * FROM meter_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<MeterRecord>>

    // 按时间范围查询
    @Query("SELECT * FROM meter_records WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getRecordsByTimeRange(startTime: LocalDateTime, endTime: LocalDateTime): Flow<List<MeterRecord>>

    // 查询最新的记录
    @Query("SELECT * FROM meter_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecord(): MeterRecord?

    // 查询上一条记录（用于计算差值）
    @Query("SELECT * FROM meter_records WHERE timestamp < :currentTime ORDER BY timestamp DESC LIMIT 1")
    suspend fun getPreviousRecord(currentTime: LocalDateTime): MeterRecord?

    // 按类型查询记录
    // 分页加载（首屏只取最近200条）
    @Query("SELECT * FROM meter_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecordsLimited(limit: Int = 200): Flow<List<MeterRecord>>

    // 加载更多（滚动底部时调用）
    @Query("SELECT * FROM meter_records ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun loadMoreRecords(limit: Int = 50, offset: Int): List<MeterRecord>

    // 按类型计数
    @Query("SELECT COUNT(*) FROM meter_records WHERE isElectricRecorded = 1")
    fun getElectricCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM meter_records WHERE isWaterRecorded = 1")
    fun getWaterCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM meter_records WHERE isGasRecorded = 1")
    fun getGasCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM meter_records WHERE note IS NOT NULL AND note != ''")
    fun getNoteCount(): Flow<Int>

    @Query("SELECT * FROM meter_records WHERE isElectricRecorded = 1 ORDER BY timestamp DESC")
    fun getElectricRecords(): Flow<List<MeterRecord>>

    @Query("SELECT * FROM meter_records WHERE isWaterRecorded = 1 ORDER BY timestamp DESC")
    fun getWaterRecords(): Flow<List<MeterRecord>>

    // 查询有备注的记录（用于事件标注）
    @Query("SELECT * FROM meter_records WHERE note IS NOT NULL AND note != '' ORDER BY timestamp DESC")
    fun getRecordsWithNotes(): Flow<List<MeterRecord>>

    // 获取记录总数
    @Query("SELECT COUNT(*) FROM meter_records")
    fun getRecordCount(): Flow<Int>

    // 删除所有记录
    @Query("DELETE FROM meter_records")
    suspend fun deleteAll()

    // 查询最新的电表记录
    @Query("SELECT * FROM meter_records WHERE isElectricRecorded = 1 AND electricTotal IS NOT NULL ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestElectricRecord(): MeterRecord?

    // 查询最新的水表记录
    @Query("SELECT * FROM meter_records WHERE isWaterRecorded = 1 AND waterTotal IS NOT NULL ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestWaterRecord(): MeterRecord?
}