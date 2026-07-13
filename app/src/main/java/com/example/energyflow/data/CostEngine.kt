package com.example.energyflow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 电费计算引擎。
 *
 * 支持：
 * - 阶梯电价（三档）
 * - 峰谷分时电价
 * - 水费计算
 *
 * 阶梯电价：
 *   第一档 (0-200 kWh/月): 基准价
 *   第二档 (201-400 kWh/月): 基准价 × 1.5
 *   第三档 (>400 kWh/月): 基准价 × 2.0
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
    ): BillResult {
        val peakPrice = readPrice(userPreferences.peakPrice)
        val valleyPrice = readPrice(userPreferences.valleyPrice)
        val flatPrice = readPrice(userPreferences.flatPrice)
        val waterPrice = readPrice(userPreferences.waterPrice)

        val flatKwh = (totalKwh - peakKwh - valleyKwh).coerceAtLeast(0.0)

        val peakTier = computeTiered(peakKwh, peakPrice)
        val valleyTier = computeTiered(valleyKwh, valleyPrice)
        val flatTier = computeTiered(flatKwh, flatPrice)

        val electricTotalCost = peakTier.total + valleyTier.total + flatTier.total
        val waterTotalCost = waterTons * waterPrice

        return BillResult(
            totalCost = electricTotalCost + waterTotalCost,
            peakKwh = peakKwh,
            peakCost = peakTier.total,
            peakTier1Kwh = peakTier.tier1Kwh,
            peakTier2Kwh = peakTier.tier2Kwh,
            peakTier3Kwh = peakTier.tier3Kwh,
            valleyKwh = valleyKwh,
            valleyCost = valleyTier.total,
            flatKwh = flatKwh,
            flatCost = flatTier.total,
            electricTotalCost = electricTotalCost,
            waterTotalCost = waterTotalCost,
            waterTons = waterTons,
            peakPrice = peakPrice,
            valleyPrice = valleyPrice,
            flatPrice = flatPrice,
            waterPrice = waterPrice
        )
    }

    suspend fun calculateSimple(totalKwh: Double): Double {
        val price = readPrice(userPreferences.flatPrice)
        return computeTiered(totalKwh, price).total
    }

    suspend fun calculatePeakValleyBill(
        peakKwh: Double,
        valleyKwh: Double
    ): PeakValleyBillResult {
        val peakPrice = readPrice(userPreferences.peakPrice)
        val valleyPrice = readPrice(userPreferences.valleyPrice)

        val peakTier = computeTiered(peakKwh, peakPrice)
        val valleyCost = valleyKwh * valleyPrice
        val total = peakTier.total + valleyCost

        return PeakValleyBillResult(
            peakKwh = peakKwh,
            peakCost = peakTier.total,
            peakPrice = peakPrice,
            valleyKwh = valleyKwh,
            valleyCost = valleyCost,
            valleyPrice = valleyPrice,
            totalCost = total,
            savingsFromValley = peakKwh * (peakPrice - valleyPrice)
        )
    }

    /**
     * 阶梯电价计算。@return 各档明细+总计。
     */
    private fun computeTiered(kwh: Double, basePrice: Double): TieredCalc {
        val tier1Kwh = kwh.coerceAtMost(200.0)
        val tier1Cost = tier1Kwh * basePrice

        val tier2Kwh = (kwh - 200.0).coerceIn(0.0, 200.0)
        val tier2Cost = tier2Kwh * basePrice * 1.5

        val tier3Kwh = (kwh - 400.0).coerceAtLeast(0.0)
        val tier3Cost = tier3Kwh * basePrice * 2.0

        return TieredCalc(
            tier1Kwh = tier1Kwh,
            tier1Cost = tier1Cost,
            tier2Kwh = tier2Kwh,
            tier2Cost = tier2Cost,
            tier3Kwh = tier3Kwh,
            tier3Cost = tier3Cost,
            total = tier1Cost + tier2Cost + tier3Cost
        )
    }

    private suspend fun readPrice(flow: Flow<Double>): Double {
        return flow.first()
    }
}

data class TieredCalc(
    val tier1Kwh: Double,
    val tier1Cost: Double,
    val tier2Kwh: Double,
    val tier2Cost: Double,
    val tier3Kwh: Double,
    val tier3Cost: Double,
    val total: Double
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
