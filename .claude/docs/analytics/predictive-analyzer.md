# PredictiveAnalyzer — 月度用电预测

## Files
- Android wrapper: `app/src/main/java/com/example/energyflow/data/PredictiveAnalyzer.kt`
- Shared KMP core: `shared/src/commonMain/kotlin/com/example/energyflow/shared/PredictiveAnalyzer.kt`

## 算法: 5 模型置信度加权集成
每个满足数据量门槛且输出 > 0 的模型贡献一个日均率，按置信度加权平均：
```
weightedDailyRate = Σ(rate × confidence) / Σ(confidence)
```

| 模型 | 置信度 | 启用门槛 | 说明 |
|------|--------|----------|------|
| DES 双重指数平滑 | 0.8 | ≥5 点 (MIN_DES_POINTS) | α=0.3, β=0.1, forecast = level + trend × 0.5 |
| Holt-Winters 三重指数平滑 | 0.85 | ≥14 点 (MIN_SEASONAL_POINTS) | γ=0.15 (GAMMA), SEASON_LENGTH=7 周季节性，输出附带 seasonalFactor |
| 线性回归 | 0.7 | ≥6 点 (MIN_LR_POINTS) | 最小二乘外推下一点 |
| AR(3) 自回归 | 0.65 | ≥7 点 (MIN_AR_POINTS) | AR_LAG=3，越近的点权重越高 |
| 简单平均 (fallback) | — | predictions 为空时 | 见下文 Fallback 策略 |

所有模型输出的日均率均以 0.1 为下限 (`coerceAtLeast(0.1)`)。

## 预测流程
1. **数据清洗**: removeDecreasingReadings — 过滤递减读数（换表等）
2. **零消耗守卫** (lines 96-98): 清洗后首尾读数差 totalConsumption <= 0 → 直接返回 null，不运行任何模型
3. **日均率计算**: buildDailyRateSeries — 相邻记录差值 / 天数
4. **多模型集成**: 上表各模型按置信度加权平均 → weightedDailyRate
5. **天气权重**: calculateWeatherMultiplier
   - 高温修正（空调）: tempMax ≥40°C → ×1.5, ≥38°C → ×1.35, ≥35°C → ×1.15
   - 低温修正（取暖）: tempMin ≤0°C → ×1.3, ≤5°C → ×1.15
   - 每天取 maxOf(高温, 低温) 的增量，累加后固定除以 FORECAST_DAYS=7（不是 inWindow.size，line 408）
6. **周末因子**: calculateWeekendFactor
   - 基于历史数据的周末/工作日用电比
   - 加权计算剩余天数中的周末影响，clamp 0.9~1.3
7. **预测输出**:
   - predictedRemaining = finalDailyRate × daysRemaining × seasonalFactor (line 188)
   - predictedTotal = consumedSoFar + predictedRemaining
   - 置信区间: margin = predictedTotal × 模型间方差(clamp 5%~30%) × 1.96

## MonthPrediction（11 字段）
```kotlin
data class MonthPrediction(
    val dailyRateKwh: Double,        // 调整后日均用电
    val daysElapsed: Int,            // 本月已过天数
    val daysRemaining: Int,          // 本月剩余天数
    val consumedSoFarKwh: Double,    // 本月已用电量
    val predictedRemainingKwh: Double, // 预测剩余用电
    val predictedTotalKwh: Double,   // 预测总用电
    val confidenceLow: Double = predictedTotalKwh * 0.85,   // 置信下界
    val confidenceHigh: Double = predictedTotalKwh * 1.15,  // 置信上界
    val modelUsed: String = "DES",   // 置信度最高的模型名
    val seasonalFactor: Double = 1.0,
    val trendDirection: TrendDirection = TrendDirection.STABLE
)

enum class TrendDirection { RISING, FALLING, STABLE }
```

## Fallback 策略
当 predictions 为空时触发（数据点 < 5，或所有模型输出 ≤ 0）→ fallbackSimplePrediction：
- 优先使用本月已有 2+ 条记录的差值
- 否则取最近 5 条记录的简单平均
- 日均率 <= 0 时返回 null（无法预测）
- modelUsed = "Simple Average"，置信区间固定 ±20%

## 已知缺口: Android wrapper 丢弃集成元数据
`app/src/main/java/com/example/energyflow/data/PredictiveAnalyzer.kt:28-35` 重建 MonthPrediction 时只透传 6 个旧字段，confidenceLow/High、modelUsed、seasonalFactor、trendDirection 全部回落为默认值（±15%、"DES"、1.0、STABLE）——共享层计算的置信区间与模型信息在 Android 侧不可见。
