package com.example.energyflow.shared

import kotlinx.serialization.Serializable

/**
 * 计费计算引擎（纯逻辑，跨平台可用）。
 *
 * 用电：峰谷分时 + 阶梯加价
 * 用水：阶梯水价（分段累进）
 */
@Serializable
data class BillingRules(
    val peakPrice: Double = 0.5583,
    val valleyPrice: Double = 0.3583,
    val flatPrice: Double = 0.5283,
    val electricTier1Limit: Double = 230.0,
    val electricTier2Limit: Double = 400.0,
    val electricTier2Surcharge: Double = 0.05,
    val electricTier3Surcharge: Double = 0.30,
    val waterTier1Limit: Double = 16.67,
    val waterTier2Limit: Double = 22.5,
    val waterTier1Price: Double = 3.42,
    val waterTier2Price: Double = 4.32,
    val waterTier3Price: Double = 7.02
)

data class BillResult(
    val totalCost: Double,
    val peakKwh: Double,
    val peakCost: Double,
    val peakTier1Kwh: Double,
    val peakTier2Kwh: Double,
    val peakTier3Kwh: Double,
    val valleyKwh: Double,
    val valleyCost: Double,
    val flatKwh: Double,
    val flatCost: Double,
    val electricTotalCost: Double,
    val waterTotalCost: Double,
    val waterTons: Double,
    val peakPrice: Double,
    val valleyPrice: Double,
    val flatPrice: Double,
    val waterPrice: Double
)

data class PeakValleyBillResult(
    val peakKwh: Double,
    val peakCost: Double,
    val peakPrice: Double,
    val valleyKwh: Double,
    val valleyCost: Double,
    val valleyPrice: Double,
    val totalCost: Double,
    val savingsFromValley: Double
)

object CostEngineShared {
    fun calculate(
        rules: BillingRules,
        totalKwh: Double,
        peakKwh: Double = 0.0,
        valleyKwh: Double = 0.0,
        waterTons: Double = 0.0
    ): BillResult {
        val safeTotal = totalKwh.coerceAtLeast(0.0)
        val safePeak = peakKwh.coerceIn(0.0, safeTotal)
        val safeValley = valleyKwh.coerceIn(0.0, safeTotal - safePeak)
        val flatKwh = (safeTotal - safePeak - safeValley).coerceAtLeast(0.0)

        val tieredUsage = tieredUsage(safeTotal, rules.electricTier1Limit, rules.electricTier2Limit)

        val avgSurcharge = if (safeTotal > 0.0) {
            (tieredUsage.tier2 * rules.electricTier2Surcharge +
             tieredUsage.tier3 * rules.electricTier3Surcharge) / safeTotal
        } else 0.0

        val peakCost = safePeak * (rules.peakPrice + avgSurcharge)
        val valleyCost = safeValley * (rules.valleyPrice + avgSurcharge)
        val flatCost = flatKwh * (rules.flatPrice + avgSurcharge)

        val water = tieredWaterCost(waterTons.coerceAtLeast(0.0), rules)

        return BillResult(
            totalCost = peakCost + valleyCost + flatCost + water.total,
            peakKwh = safePeak,
            peakCost = peakCost,
            peakTier1Kwh = tieredUsage.tier1 * safePeak / safeTotal.orOne(),
            peakTier2Kwh = tieredUsage.tier2 * safePeak / safeTotal.orOne(),
            peakTier3Kwh = tieredUsage.tier3 * safePeak / safeTotal.orOne(),
            valleyKwh = safeValley,
            valleyCost = valleyCost,
            flatKwh = flatKwh,
            flatCost = flatCost,
            electricTotalCost = peakCost + valleyCost + flatCost,
            waterTotalCost = water.total,
            waterTons = waterTons.coerceAtLeast(0.0),
            peakPrice = rules.peakPrice + avgSurcharge,
            valleyPrice = rules.valleyPrice + avgSurcharge,
            flatPrice = rules.flatPrice + avgSurcharge,
            waterPrice = water.effectivePrice
        )
    }

    private fun tieredUsage(usage: Double, firstLimit: Double, secondLimit: Double): TieredUsage {
        val first = firstLimit.coerceAtLeast(0.0)
        val second = secondLimit.coerceAtLeast(first)
        return TieredUsage(
            tier1 = usage.coerceAtMost(first),
            tier2 = (usage - first).coerceIn(0.0, second - first),
            tier3 = (usage - second).coerceAtLeast(0.0)
        )
    }

    private fun tieredWaterCost(tons: Double, rules: BillingRules): WaterCost {
        val usage = tieredUsage(tons, rules.waterTier1Limit, rules.waterTier2Limit)
        val total = usage.tier1 * rules.waterTier1Price +
            usage.tier2 * rules.waterTier2Price +
            usage.tier3 * rules.waterTier3Price
        return WaterCost(
            total = total,
            effectivePrice = if (tons == 0.0) rules.waterTier1Price else total / tons
        )
    }

    private fun Double.orOne(): Double = if (this == 0.0) 1.0 else this

    private data class TieredUsage(val tier1: Double, val tier2: Double, val tier3: Double)
    private data class WaterCost(val total: Double, val effectivePrice: Double)
}
