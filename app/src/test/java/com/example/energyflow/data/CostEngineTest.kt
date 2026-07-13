package com.example.energyflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CostEngineTest {
    @Test
    fun `electric tiers apply additive surcharge`() {
        val bill = CostEngine.calculate(
            rules = BillingRules(
                peakPrice = 0.6,
                valleyPrice = 0.3,
                flatPrice = 0.5,
                electricTier1Limit = 200.0,
                electricTier2Limit = 400.0,
                electricTier2Surcharge = 0.05,
                electricTier3Surcharge = 0.30
            ),
            totalKwh = 300.0,
            peakKwh = 150.0,
            valleyKwh = 150.0
        )

        // 300 kWh → tier1=200, tier2=100, tier3=0
        // avgSurcharge = (100 × 0.05) / 300 = 0.01667
        // peak effective = 0.6 + 0.01667 = 0.61667 → 150 × 0.61667 = 92.5
        // valley effective = 0.3 + 0.01667 = 0.31667 → 150 × 0.31667 = 47.5
        assertEquals(140.0, bill.electricTotalCost, 0.01)
        assertEquals(92.5, bill.peakCost, 0.01)
        assertEquals(47.5, bill.valleyCost, 0.01)
    }

    @Test
    fun `tier3 surcharge kicks in above tier2 limit`() {
        val bill = CostEngine.calculate(
            rules = BillingRules(
                peakPrice = 0.5583,
                valleyPrice = 0.3583,
                flatPrice = 0.5283,
                electricTier1Limit = 230.0,
                electricTier2Limit = 400.0,
                electricTier2Surcharge = 0.05,
                electricTier3Surcharge = 0.30
            ),
            totalKwh = 500.0,
            peakKwh = 300.0,
            valleyKwh = 200.0
        )

        // 500 kWh → tier1=230, tier2=170, tier3=100
        // avgSurcharge = (170×0.05 + 100×0.30) / 500 = (8.5 + 30) / 500 = 0.077
        // peak effective = 0.5583 + 0.077 = 0.6353 → 300 × 0.6353 = 190.59
        // valley effective = 0.3583 + 0.077 = 0.4353 → 200 × 0.4353 = 87.06
        assertEquals(277.65, bill.electricTotalCost, 0.02)
    }

    @Test
    fun `within tier1 no surcharge applied`() {
        val bill = CostEngine.calculate(
            rules = BillingRules(
                peakPrice = 0.5583,
                valleyPrice = 0.3583,
                flatPrice = 0.5283,
                electricTier1Limit = 230.0,
                electricTier2Limit = 400.0,
                electricTier2Surcharge = 0.05,
                electricTier3Surcharge = 0.30
            ),
            totalKwh = 100.0,
            peakKwh = 60.0,
            valleyKwh = 40.0
        )

        // All within tier1 → surcharge = 0
        assertEquals(60.0 * 0.5583 + 40.0 * 0.3583, bill.electricTotalCost, 0.01)
        assertEquals(0.5583, bill.peakPrice, 0.001)
        assertEquals(0.3583, bill.valleyPrice, 0.001)
    }

    @Test
    fun `water price follows configured tiers`() {
        val bill = CostEngine.calculate(
            rules = BillingRules(
                waterTier1Limit = 10.0,
                waterTier2Limit = 20.0,
                waterTier1Price = 2.0,
                waterTier2Price = 3.0,
                waterTier3Price = 4.0
            ),
            totalKwh = 0.0,
            waterTons = 25.0
        )

        // 25 tons → tier1=10, tier2=10, tier3=5
        // cost = 10×2 + 10×3 + 5×4 = 20 + 30 + 20 = 70
        assertEquals(70.0, bill.waterTotalCost, 0.001)
        assertEquals(2.8, bill.waterPrice, 0.001)  // 70 / 25
    }
}
