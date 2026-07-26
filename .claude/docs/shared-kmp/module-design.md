# KMP Shared Module 设计

## 目标
将纯计算逻辑抽取到 `shared` 模块，实现跨平台复用（Android + Desktop/JVM）。

## 平台目标
> Gradle 目标声明为 `jvm("desktop")`（`shared/build.gradle.kts`），因此任务名均为 `desktop*`。
- `commonMain` — 纯 Kotlin 逻辑，无 Android 依赖
- `androidMain` — Android 平台实现 (actual)
- `desktopMain` — Desktop/JVM 平台实现 (actual)
- `commonTest` — 跨平台测试（`SharedEnginesTest.kt`，JVM 侧由 `:shared:desktopTest` 运行）

## 依赖
- `kotlinx.datetime` — 跨平台日期时间
- `kotlinx.serialization` — JSON 序列化

## 模块清单

### CostEngineShared (`shared/CostEngine.kt`)
- `BillingRules` — @Serializable 计费规则
- `BillResult` / `PeakValleyBillResult` — 计算结果
- `CostEngineShared.calculate()` — 纯函数，输入规则+用量，输出费用

### PredictiveAnalyzerShared (`shared/PredictiveAnalyzer.kt`)
- `Reading` — 跨平台读数数据类
- `WeatherForecast` — 天气预报数据类
- `MonthPrediction` — 预测结果
- `predictMonth()` — DES + 天气权重 + 周末因子

### AnomalyDetectorShared (`shared/AnomalyDetector.kt`)
- `Reading` — 跨平台读数数据类
- `Warning` — sealed class 警告
- `detect()` — 递减检测 + 尖峰检测

### CarbonCalculator (`shared/CarbonFootprint.kt`)
- `CarbonConfig` / `CarbonResult` — 配置与结果
- `GreenBadge` — 绿色徽章枚举
- `YearMonthStat` — 月统计数据
- `CarbonCalculator.calculate()` — 碳排放计算
- `CarbonCalculator.badgesFromRecords()` — 徽章计算

### WrappedReportBuilder (`shared/WrappedReport.kt`)
- `WrappedReportData` — 年度报告数据
- `WrappedReportBuilder.build()` — 构建报告

### Platform (`shared/Platform.kt`)
- `expect fun platformName(): String` — 本模块唯一的 expect/actual
- actual 实现：androidMain 返回 `"Android"`，desktopMain 返回 `"Desktop"`

## Android 包装模式
```kotlin
// shared module (纯逻辑)
object CostEngineShared {
    fun calculate(rules: BillingRules, ...): BillResult
}

// Android module (Hilt 包装)
@Singleton
class CostEngine @Inject constructor(private val userPreferences: UserPreferences) {
    suspend fun calculateBill(...): BillResult =
        CostEngineShared.calculate(rules = userPreferences.billingRules.first(), ...)
}
```
