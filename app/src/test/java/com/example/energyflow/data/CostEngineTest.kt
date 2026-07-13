package com.example.energyflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CostEngineTest {
    @Test
    fun `electric tiers apply once to total consumption`() {
        val bill = CostEngine.calculate(
            rules = BillingRules(peakPrice = 0.6, valleyPrice = 0.3, flatPrice = 0.5),
            totalKwh = 300.0,
            peakKwh = 150.0,
            valleyKwh = 150.0
        )

        // 300 kWh means a 1.1667 effective tier multiplier, shared across peak and valley.
        assertEquals(157.5, bill.electricTotalCost, 0.001)
        assertEquals(105.0, bill.peakCost, 0.001)
        assertEquals(52.5, bill.valleyCost, 0.001)
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

        assertEquals(70.0, bill.waterTotalCost, 0.001)
        assertEquals(2.8, bill.waterPrice, 0.001)
    }
}
