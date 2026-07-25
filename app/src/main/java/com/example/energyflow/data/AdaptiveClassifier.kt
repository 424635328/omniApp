package com.example.energyflow.data

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自适应数值分类器。
 *
 * 从用户的历史记录中自动学习各类仪表的阈值范围，无需硬编码固定区间。
 * 学习结果缓存到 DataStore，避免每次冷启动重算。
 *
 * 学习策略：
 * - 总电下限 = avg(历史总电) × 0.85
 * - 峰电区间 = avg(历史峰电) ± 15%
 * - 谷电区间 = avg(历史谷电) ± 15%
 * - 水表上限 = max(历史水表) × 1.2
 * - 无历史时回退 DEFAULTS
 */
@Singleton
class AdaptiveClassifier @Inject constructor(
    private val dao: MeterRecordDao,
    private val userPreferences: UserPreferences
) {
    /**
     * 获取当前分类阈值。
     * 优先返回 DataStore 缓存值；无缓存时从历史数据计算。
     */
    suspend fun getThresholds(): ClassificationThresholds {
        val cached = userPreferences.getCachedThresholds()
        if (cached != null) return cached
        return computeAndCache()
    }

    /**
     * 根据当前所有历史记录重新计算阈值并持久化。
     */
    suspend fun reLearn() {
        computeAndCache()
    }

    private suspend fun computeAndCache(): ClassificationThresholds {
        val thresholds = computeThresholds()
        userPreferences.cacheThresholds(thresholds)
        return thresholds
    }

    private suspend fun computeThresholds(): ClassificationThresholds {
        val electricRecords = dao.getElectricRecords().first()
        val waterRecords = dao.getWaterRecords().first()

        if (electricRecords.isEmpty() && waterRecords.isEmpty()) {
            return ClassificationThresholds.DEFAULTS
        }

        val totals = electricRecords.mapNotNull { it.electricTotal }
        val peaks = electricRecords.mapNotNull { it.electricPeak }
        val valleys = electricRecords.mapNotNull { it.electricValley }
        val waters = waterRecords.mapNotNull { it.waterTotal }

        return ClassificationThresholds(
            totalElectricMin = if (totals.isNotEmpty()) {
                (totals.average() * 0.85).coerceAtLeast(0.0)
            } else {
                ClassificationThresholds.DEFAULTS.totalElectricMin
            },
            peakMin = if (peaks.isNotEmpty()) {
                peaks.average() * 0.85
            } else {
                ClassificationThresholds.DEFAULTS.peakMin
            },
            peakMax = if (peaks.isNotEmpty()) {
                peaks.average() * 1.15
            } else {
                ClassificationThresholds.DEFAULTS.peakMax
            },
            valleyMin = if (valleys.isNotEmpty()) {
                valleys.average() * 0.85
            } else {
                ClassificationThresholds.DEFAULTS.valleyMin
            },
            valleyMax = if (valleys.isNotEmpty()) {
                valleys.average() * 1.15
            } else {
                ClassificationThresholds.DEFAULTS.valleyMax
            },
            waterMax = if (waters.isNotEmpty()) {
                waters.max() * 1.2
            } else {
                ClassificationThresholds.DEFAULTS.waterMax
            }
        )
    }
}
