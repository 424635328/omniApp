package com.example.energyflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "meter_records")
data class MeterRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 时间戳
    val timestamp: LocalDateTime,

    // 电表相关
    val isElectricRecorded: Boolean = false,
    val electricTotal: Double? = null,
    val electricPeak: Double? = null,
    val electricValley: Double? = null,

    // 水表相关
    val isWaterRecorded: Boolean = false,
    val waterTotal: Double? = null,

    // 燃气表相关（预留扩展）
    val isGasRecorded: Boolean = false,
    val gasTotal: Double? = null,

    // 备注
    val note: String? = null
)