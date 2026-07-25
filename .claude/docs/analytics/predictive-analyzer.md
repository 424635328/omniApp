# PredictiveAnalyzer — 月度用电预测

## Files
- Android wrapper: `app/src/main/java/com/example/energyflow/data/PredictiveAnalyzer.kt`
- Shared KMP core: `shared/src/commonMain/kotlin/com/example/energyflow/shared/PredictiveAnalyzer.kt`

## 算法: 双重指数平滑 (DES)
```
level = α * current + (1-α) * (level + trend)
trend = β * (level - prevLevel) + (1-β) * trend
forecast = level + trend * 0.5
```
- α = 0.3, β = 0.1
- 最少 5 个数据点才启用 DES，否则 fallback 到简单平均

## 预测流程
1. **数据清洗**: removeDecreasingReadings — 过滤递减读数（换表等）
2. **日均率计算**: buildDailyRateSeries — 相邻记录差值 / 天数
3. **DES 平滑**: 双重指数平滑，输出趋势调整后的日均率
4. **天气权重**: calculateWeatherMultiplier
   - ≥40°C → ×1.5, ≥38°C → ×1.35, ≥35°C → ×1.15
   - 7天预报窗口的加权平均
5. **周末因子**: calculateWeekendFactor
   - 基于历史数据的周末/工作日用电比
   - 加权计算剩余天数中的周末影响
6. **预测输出**: predictedTotal = consumedSoFar + dailyRate × daysRemaining

## MonthPrediction
```kotlin
data class MonthPrediction(
    val dailyRateKwh: Double,        // 调整后日均用电
    val daysElapsed: Int,            // 本月已过天数
    val daysRemaining: Int,          // 本月剩余天数
    val consumedSoFarKwh: Double,    // 本月已用电量
    val predictedRemainingKwh: Double, // 预测剩余用电
    val predictedTotalKwh: Double    // 预测总用电
)
```

## Fallback 策略
当数据点 < 5 时：
- 优先使用本月已有 2+ 条记录的差值
- 否则取最近 5 条记录的简单平均
- 日均率 <= 0 时返回 null（无法预测）
