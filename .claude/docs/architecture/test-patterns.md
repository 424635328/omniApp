# Test Patterns & Coverage

## 运行测试
```bash
# 全部单元测试
./gradlew :app:testDebugUnitTest

# 单个测试类
./gradlew :app:testDebugUnitTest --tests "com.example.energyflow.data.CostEngineTest"

# 单个测试方法
./gradlew :app:testDebugUnitTest --tests "com.example.energyflow.data.CostEngineTest.test tiered pricing"

# Shared module 测试
./gradlew :shared:desktopTest
```

## 测试文件清单
| 测试文件 | 覆盖目标 | 关键测试点 |
|---------|---------|-----------|
| `SmartInputParserTest.kt` | SmartInputParser | 11+ 正则模式、中文数字、峰谷配对、日期上下文继承 |
| `CostEngineTest.kt` | CostEngine | 峰谷分时、阶梯加价、水价阶梯、零用量边界 |
| `PredictiveAnalyzerTest.kt` | PredictiveAnalyzer | DES 收敛、天气乘数、周末因子、fallback 策略 |
| `AnomalyDetectorTest.kt` | AnomalyDetector | 单调递增、突增检测 (5x)、批导入递减 |
| `MeterRepositoryTest.kt` | MeterRepository | smartInsert、batchInsert、去重、插值 |
| `AdaptiveClassifierTest.kt` | AdaptiveClassifier | 阈值学习、缓存、无历史回退 |
| `EventImpactAnalyzerTest.kt` | EventImpactAnalyzer | 标签提取、事件窗口、日均对比 |
| `DeepSeekRepositoryTest.kt` | DeepSeekRepository | API 调用、降级解析、无 Key 返回 null |
| `WeatherRepositoryTest.kt` | WeatherRepository | JSON 解析、WMO 码映射、错误处理 |
| `WeatherInterpolatorTest.kt` | WeatherInterpolator | 线性插值、最近邻外推、空数据 |
| `UserPreferencesTest.kt` | UserPreferences | DataStore 持久化、计费迁移 |
| `ThemeDistRepositoryTest.kt` | ThemeDistRepository | CSS 变量解析、颜色转换 |
| `ChartViewModelTest.kt` | ChartViewModel | 数据聚合、时间窗口、费用切换 |

## 测试模式

### 1. 纯函数测试 (CostEngine, PredictiveAnalyzer, CarbonCalculator)
```kotlin
@Test
fun `test tiered pricing`() {
    val rules = BillingRules(peakPrice = 0.5, valleyPrice = 0.3, ...)
    val result = CostEngineShared.calculate(rules, totalKwh = 300.0, peakKwh = 200.0, valleyKwh = 100.0)
    assertEquals(expected, result.electricTotalCost, 0.01)
}
```

### 2. 解析器测试 (SmartInputParser)
```kotlin
@Test
fun `parse date header then record`() {
    val results = parser.parseWithContext("7.15\n14.30 16639")
    assertEquals(1, results.size)
    val success = results[0] as ParseResult.Success
    assertEquals(16639.0, success.electricTotal)
}
```

### 3. 异常检测测试 (AnomalyDetector)
```kotlin
@Test
fun `detect monotonic decrease`() {
    // 先插入历史记录
    dao.insert(MeterRecord(timestamp = ..., electricTotal = 1000.0))
    val warning = detector.checkElectricMonotonic(999.0, timestamp)
    assertNotNull(warning)
    assertTrue(warning!!.contains("低于历史记录"))
}
```

### 4. Repository 测试 (MeterRepository)
```kotlin
@Test
fun `smart insert deduplicates`() {
    repository.smartInsert("7.15 14.30 16639")
    val result = repository.smartInsert("7.15 14.30 16639")
    assertTrue(result is InsertResult.Error) // 重复
}
```

## 编写新测试的规范
1. 测试类放在与被测类相同的包路径下 (`app/src/test/java/...`)
2. 使用 JUnit 4 (`@Test`, `assertEquals`, `assertNotNull`)
3. 测试方法名用反引号描述行为: `` `test tiered pricing with peak valley` ``
4. 每个测试独立，不依赖其他测试的执行顺序
5. 使用 `@Before` 设置公共 fixtures
6. 浮点比较使用 delta: `assertEquals(expected, actual, 0.01)`
