package com.example.energyflow.data

import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 南京建邺区居民水电计费规则（2026年现行）。
 *
 * 用电：峰谷分时 + 阶梯加价
 *   - 峰 0.5583 / 谷 0.3583 / 平 0.5283 元/度（第一档）
 *   - 第二档（年 >2760 度）加价 0.05 元/度
 *   - 第三档（年 >4800 度）加价 0.30 元/度
 *   - 按月估算时，月阶梯上限 = 年上限 / 12
 *
 * 用水：阶梯水价（年计量，按月估算）
 *   - 第一档 ≤200 吨/年 → 3.42 元/吨（月均 16.67 吨）
 *   - 第二档 200-270 吨/年 → 4.32 元/吨（月均 22.5 吨）
 *   - 第三档 >270 吨/年 → 7.02 元/吨
 */
@Serializable
data class BillingRules(
    // ── 电价（分时） ──
    val peakPrice: Double = 0.5583,
    val valleyPrice: Double = 0.3583,
    val flatPrice: Double = 0.5283,

    // ── 用电阶梯（月估算 = 年上限 / 12） ──
    val electricTier1Limit: Double = 230.0,   // 2760 / 12
    val electricTier2Limit: Double = 400.0,   // 4800 / 12
    val electricTier2Surcharge: Double = 0.05,  // 第二档加价（元/度）
    val electricTier3Surcharge: Double = 0.30,  // 第三档加价（元/度）

    // ── 水价（阶梯，月估算 = 年上限 / 12） ──
    val waterTier1Limit: Double = 16.67,  // 200 / 12
    val waterTier2Limit: Double = 22.5,   // 270 / 12
    val waterTier1Price: Double = 3.42,
    val waterTier2Price: Double = 4.32,
    val waterTier3Price: Double = 7.02
)

/**
 * 统一计费引擎。
 *
 * 用电 = 分时电价 × 阶梯加价（"先峰谷、后阶梯"）
 * 用水 = 阶梯水价（分段累进）
 */
@Singleton
class CostEngine @Inject constructor(
    private val userPreferences: UserPreferences
) {
    suspend fun calculateBill(
        totalKwh: Double,
        peakKwh: Double = 0.0,
        valleyKwh: Double = 0.0,
        waterTons: Double = 0.0
    ): BillResult = calculate(
        rules = userPreferences.billingRules.first(),
        totalKwh = totalKwh,
        peakKwh = peakKwh,
        valleyKwh = valleyKwh,
        waterTons = waterTons
    )

    suspend fun calculateSimple(totalKwh: Double): Double =
        calculateBill(totalKwh = totalKwh).electricTotalCost

    suspend fun calculatePeakValleyBill(peakKwh: Double, valleyKwh: Double): PeakValleyBillResult {
        val bill = calculateBill(totalKwh = peakKwh + valleyKwh, peakKwh = peakKwh, valleyKwh = valleyKwh)
        val rules = userPreferences.billingRules.first()
        return PeakValleyBillResult(
            peakKwh = peakKwh,
            peakCost = bill.peakCost,
            peakPrice = rules.peakPrice,
            valleyKwh = valleyKwh,
            valleyCost = bill.valleyCost,
            valleyPrice = rules.valleyPrice,
            totalCost = bill.electricTotalCost,
            savingsFromValley = valleyKwh.coerceAtLeast(0.0) * (rules.peakPrice - rules.valleyPrice)
        )
    }

    companion object {
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

            // ── 用电阶梯加价（"先峰谷、后阶梯"） ──
            val tieredUsage = tieredUsage(safeTotal, rules.electricTier1Limit, rules.electricTier2Limit)

            // 计算加权平均加价：每度电的阶梯附加费
            val avgSurcharge = if (safeTotal > 0.0) {
                (tieredUsage.tier2 * rules.electricTier2Surcharge +
                 tieredUsage.tier3 * rules.electricTier3Surcharge) / safeTotal
            } else 0.0

            val peakCost = safePeak * (rules.peakPrice + avgSurcharge)
            val valleyCost = safeValley * (rules.valleyPrice + avgSurcharge)
            val flatCost = flatKwh * (rules.flatPrice + avgSurcharge)

            // ── 阶梯水价 ──
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
    }
}

private data class TieredUsage(val tier1: Double, val tier2: Double, val tier3: Double)
private data class WaterCost(val total: Double, val effectivePrice: Double)

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
