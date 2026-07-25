# CostEngine — 计费计算引擎

## Files
- Android wrapper: `app/src/main/java/com/example/energyflow/data/CostEngine.kt`
- Shared KMP core: `shared/src/commonMain/kotlin/com/example/energyflow/shared/CostEngine.kt`

## 计费规则 (BillingRules)
```kotlin
data class BillingRules(
    val peakPrice: Double = 0.5583,      // 峰电单价 ¥/kWh
    val valleyPrice: Double = 0.3583,    // 谷电单价 ¥/kWh
    val flatPrice: Double = 0.5283,      // 平电单价 (无峰谷时)
    val electricTier1Limit: Double = 230.0,   // 一档上限 kWh
    val electricTier2Limit: Double = 400.0,   // 二档上限 kWh
    val electricTier2Surcharge: Double = 0.05, // 二档加价 ¥/kWh
    val electricTier3Surcharge: Double = 0.30, // 三档加价 ¥/kWh
    val waterTier1Limit: Double = 16.67,      // 水一档上限 吨
    val waterTier2Limit: Double = 22.5,       // 水二档上限 吨
    val waterTier1Price: Double = 3.42,       // 水一档单价 ¥/吨
    val waterTier2Price: Double = 4.32,       // 水二档单价 ¥/吨
    val waterTier3Price: Double = 7.02        // 水三档单价 ¥/吨
)
```
默认值为南京建邺区 2026 年标准。

## 计算逻辑 (CostEngineShared.calculate)
1. **用电**: 峰谷分时 + 阶梯加价
   - tieredUsage(totalKwh, tier1Limit, tier2Limit) → 分段用量
   - avgSurcharge = (tier2 * surcharge2 + tier3 * surcharge3) / total
   - peakCost = peak * (peakPrice + avgSurcharge)
   - valleyCost = valley * (valleyPrice + avgSurcharge)
   - flatCost = flat * (flatPrice + avgSurcharge)
2. **用水**: 阶梯水价（分段累进）
   - tier1 * price1 + tier2 * price2 + tier3 * price3

## UserPreferences 计费迁移
- `CURRENT_BILLING_VERSION = 2`
- 首次启动或版本低于当前 → 自动重置为南京默认值并写入 DataStore
- 迁移只触发一次，避免 Compose 重组循环

## API
```kotlin
// Android wrapper
suspend fun calculateBill(totalKwh, peakKwh, valleyKwh, waterTons): BillResult
suspend fun calculateSimple(totalKwh): Double
suspend fun calculatePeakValleyBill(peakKwh, valleyKwh): PeakValleyBillResult
```

## BillResult 字段
```kotlin
data class BillResult(
    val totalCost: Double,           // 总费用
    val peakCost: Double,            // 峰电费用
    val valleyCost: Double,          // 谷电费用
    val flatCost: Double,            // 平电费用
    val electricTotalCost: Double,   // 电费小计
    val waterTotalCost: Double,      // 水费小计
    val peakPrice: Double,           // 峰电实际单价 (含阶梯加价)
    val valleyPrice: Double,         // 谷电实际单价
    val flatPrice: Double,           // 平电实际单价
    val waterPrice: Double,          // 水实际单价 (加权平均)
    // ... 更多分段明细
)
```

## 相关文档
- 数据模型: `.claude/docs/data-layer/meter-record.md`
- 预测分析: `.claude/docs/analytics/predictive-analyzer.md` (预测账单)
- 账单报告: `.claude/docs/ui-layer/settings-and-reports.md` (BillReportGenerator)
