package com.example.energyflow.data

/**
 * 动态数值分类阈值。
 * 默认值为当前硬编码的经验值，AdaptiveClassifier 会根据用户历史数据自动调整。
 */
data class ClassificationThresholds(
    val totalElectricMin: Double = 15000.0,
    val peakMin: Double = 9000.0,
    val peakMax: Double = 10000.0,
    val valleyMin: Double = 7000.0,
    val valleyMax: Double = 8000.0,
    val waterMax: Double = 1000.0
) {
    companion object {
        val DEFAULTS = ClassificationThresholds()
    }
}
