package com.example.energyflow.data

import com.example.energyflow.shared.BillingRules as SharedBillingRules
import com.example.energyflow.shared.PredictiveAnalyzerShared
import kotlinx.serialization.Serializable

// Re-export BillingRules from the KMP shared module so existing imports continue to work
typealias BillingRules = SharedBillingRules

// Re-export MonthPrediction from KMP shared module
typealias MonthPrediction = PredictiveAnalyzerShared.MonthPrediction

/**
 * 预测快照 — 序列化到 DataStore，用于月度预测跟踪对比。
 */
@Serializable
data class PredictionSnapshot(
    val savedYearMonth: String,
    val savedDayOfMonth: Int,
    val predictedTotalKwh: Double,
    val dailyRateKwh: Double,
    val consumedSoFarAtSave: Double
)
