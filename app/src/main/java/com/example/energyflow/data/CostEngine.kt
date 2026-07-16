package com.example.energyflow.data

import com.example.energyflow.shared.BillingRules
import com.example.energyflow.shared.BillResult
import com.example.energyflow.shared.CostEngineShared
import com.example.energyflow.shared.PeakValleyBillResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt 包装的计费引擎 — 委托给 KMP 共享模块 [CostEngineShared]。
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
    ): BillResult = CostEngineShared.calculate(
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
}
