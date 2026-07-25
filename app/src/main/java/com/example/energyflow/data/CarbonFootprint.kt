package com.example.energyflow.data

import com.example.energyflow.shared.CarbonCalculator
import com.example.energyflow.shared.CarbonConfig
import com.example.energyflow.shared.CarbonResult
import com.example.energyflow.shared.GreenBadge
import com.example.energyflow.shared.YearMonthStat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt 包装的碳足迹计算器 — 委托给 KMP 共享模块 [CarbonCalculator]。
 *
 * 从 [UserPreferences] 读取用户自定义的排放因子。
 */
@Singleton
class CarbonFootprint @Inject constructor(
    private val userPreferences: UserPreferences
) {
    /**
     * 从 UserPreferences 读取碳足迹配置并计算。
     */
    suspend fun calculate(kwh: Double, gasM3: Double = 0.0): CarbonResult {
        val config = loadConfig()
        return CarbonCalculator.calculate(kwh, gasM3, config)
    }

    /**
     * 计算绿色徽章。
     */
    suspend fun badges(monthlyStats: List<YearMonthStat>): List<GreenBadge> {
        return CarbonCalculator.badgesFromRecords(monthlyStats)
    }

    private suspend fun loadConfig(): CarbonConfig {
        val electric = userPreferences.carbonElectricFactor.first()
        val gas = userPreferences.carbonGasFactor.first()
        val tree = userPreferences.carbonTreeKgPerYear.first()
        return CarbonConfig(electricFactor = electric, gasFactor = gas, treeKgPerYear = tree)
    }
}
