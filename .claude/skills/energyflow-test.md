---
name: energyflow-test
description: 测试运行与调试——快速运行、解读结果、写新测试
---

# EnergyFlow — 测试运行与调试

**用途**: 运行测试 / 调试测试失败 / 写新测试时使用。

## 快速运行

```bash
# 全量
./gradlew :app:testDebugUnitTest

# 单个测试类
./gradlew :app:testDebugUnitTest --tests "com.example.energyflow.data.CostEngineTest"

# 单个测试方法（用反引号中的描述）
./gradlew :app:testDebugUnitTest --tests "com.example.energyflow.data.CostEngineTest.electric tiers apply additive surcharge"

# KMP shared module
./gradlew :shared:jvmTest

# 编译检查（比跑测试快）
./gradlew :app:compileDebugKotlin
./gradlew :shared:compileDebugKotlinAndroid
```

## 测试文件速查

| 测试文件 | 被测类 | 测试数 | 快速命令 |
|---------|--------|--------|---------|
| `CostEngineTest` | CostEngine/CostEngineShared | 4 | `--tests "*.data.CostEngineTest"` |
| `PredictiveAnalyzerTest` | PredictiveAnalyzer | 15+ | `--tests "*.data.PredictiveAnalyzerTest"` |
| `SmartInputParserTest` | SmartInputParser | 35+ | `--tests "*.data.SmartInputParserTest"` |
| `AnomalyDetectorTest` | AnomalyDetector | 3 | `--tests "*.data.AnomalyDetectorTest"` |
| `AdaptiveClassifierTest` | AdaptiveClassifier | 7 | `--tests "*.data.AdaptiveClassifierTest"` |
| `EventImpactAnalyzerTest` | EventImpactAnalyzer | 8 | `--tests "*.data.EventImpactAnalyzerTest"` |
| `WeatherInterpolatorTest` | WeatherInterpolator | 12+ | `--tests "*.data.WeatherInterpolatorTest"` |
| `ChartViewModelTest` | ChartViewModel | — | `--tests "*.ui.chart.ChartViewModelTest"` |
| `MeterRepositoryTest` | MeterRepository | — | `--tests "*.data.MeterRepositoryTest"` |
| `UserPreferencesTest` | UserPreferences | — | `--tests "*.data.UserPreferencesTest"` |

## 测试失败排查

### 常见失败原因
| 症状 | 可能原因 | 修复 |
|------|---------|------|
| assertEquals 差值很小 | 浮点精度 | 调大 delta 或使用 `roundTo` |
| assertEquals 差值很大 | 逻辑错或测试数据错 | 检查计算逻辑 |
| NullPointerException | Mock 不完整 | 补 coEvery 或使用 Fake |
| ClassCastException | 类型不匹配 | 检查 cast |
| Unresolved reference | import 缺失 | 自动补 import |
| coEvery not matched | MockK stub 顺序 | 检查 stub 链 |

### 调试技巧
```kotlin
// 1. 打印中间值（临时调试）
println("DEBUG: tier1=$tier1, tier2=$tier2, avgSurcharge=$avgSurcharge")

// 2. 逐步注释缩小范围
// 3. 检查 Mock 是否正确挂载（coEvery vs every）
// 4. 检查时间依赖是否固定（不用 LocalDateTime.now()）
```

## 写新测试模板

### 纯逻辑测试（CostEngine/PredictiveAnalyzer/CarbonCalculator）
```kotlin
@Test
fun `descriptive behavior name in backticks`() {
    // Arrange
    val rules = BillingRules(peakPrice = 0.5583, ...)
    // Act
    val bill = CostEngineShared.calculate(rules = rules, totalKwh = 300.0)
    // Assert
    assertEquals(140.0, bill.electricTotalCost, 0.01)
}
```

### Repository/DAO 测试（需要 Mock）
```kotlin
@Test
fun `repository deduplicates identical insert`() = runTest {
    coEvery { dao.getElectricRecords() } returns flowOf(emptyList())
    val result = repository.smartInsert("7.15 14.30 16639")
    assertTrue(result is InsertResult.Success)
}
```

### 测试规范
- ✅ 方法名用反引号描述行为: `` `electric tiers apply additive surcharge` ``
- ✅ 使用 AAA 模式 (Arrange-Act-Assert)
- ✅ 浮点数用 delta: `assertEquals(expected, actual, 0.01)`
- ✅ 时间依赖用固定时间，不用 `LocalDateTime.now()`
- ❌ 不要用 `testCase1` / `testCalculate` 这种命名
- ❌ 不要在测试间共享可变状态

## 相关 Skills
- Bug 诊断: `energyflow-diagnose` — 写复现测试是诊断的第一步
- 预检: `energyflow-quick-scan` — 测试通过后做扫描再提交
