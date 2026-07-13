package com.example.energyflow.data

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Local, user-configurable billing rules. Prices are yuan per unit. */
data class BillingRules(
    val peakPrice: Double = 0.6,
    val valleyPrice: Double = 0.3,
    val flatPrice: Double = 0.5,
    val electricTier1Limit: Double = 200.0,
    val electricTier2Limit: Double = 400.0,
    val waterTier1Limit: Double = 15.0,
    val waterTier2Limit: Double = 25.0,
    val waterTier1Price: Double = 3.5,
    val waterTier2Price: Double = 4.5,
    val waterTier3Price: Double = 6.0
)

/**
 * Keeps pricing policy in one place so UI estimates and monthly predictions use
 * exactly the same calculation. Electricity tiers apply to total monthly usage,
 * never separately to peak, valley and flat usage.
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

            val tieredUsage = tieredUsage(
                usage = safeTotal,
                firstLimit = rules.electricTier1Limit,
                secondLimit = rules.electricTier2Limit
            )
            val weightedMultiplier = if (safeTotal == 0.0) 1.0 else {
                (tieredUsage.tier1 + tieredUsage.tier2 * 1.5 + tieredUsage.tier3 * 2.0) / safeTotal
            }
            val peakCost = safePeak * rules.peakPrice * weightedMultiplier
            val valleyCost = safeValley * rules.valleyPrice * weightedMultiplier
            val flatCost = flatKwh * rules.flatPrice * weightedMultiplier
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
                peakPrice = rules.peakPrice,
                valleyPrice = rules.valleyPrice,
                flatPrice = rules.flatPrice,
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
            return WaterCost(total = total, effectivePrice = if (tons == 0.0) rules.waterTier1Price else total / tons)
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
