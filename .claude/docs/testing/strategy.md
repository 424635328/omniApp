# 测试策略 (Test Strategy)

## 测试金字塔

```
        ┌─────────┐
        │  E2E/UI  │  ← 未来: Compose UI 测试
        │  (少量)   │
        ├─────────┤
        │ 集成测试  │  ← Repository + DAO + Real DB
        │  (适量)   │
        ├─────────┤
        │  单元测试  │  ← 当前主力: 纯逻辑 + Mock
        │  (大量)   │
        └─────────┘
```

## 当前覆盖率

| 模块 | 测试文件 | 测试数 | 覆盖维度 |
|------|---------|--------|---------|
| SmartInputParser | SmartInputParserTest.kt | 35+ | 全部11种模式 + 极端输入 + 自适应阈值 |
| CostEngine | CostEngineTest.kt | 4 | 峰谷分时 + 阶梯加价 + 水价阶梯 |
| PredictiveAnalyzer | PredictiveAnalyzerTest.kt | 15+ | 边界 + 递减过滤 + 本月推算 + 历史回退 + 天气 |
| AnomalyDetector | AnomalyDetectorTest.kt | 3 | 单调递增 + 突增检测 + 正常接受 |
| AdaptiveClassifier | AdaptiveClassifierTest.kt | 7 | 空记录 + 纯电 + 纯水 + 混合 + 缓存 + 重学 |
| EventImpactAnalyzer | EventImpactAnalyzerTest.kt | 8 | 事件窗口 + 标签检测 + 多事件 + 边界 |
| WeatherInterpolator | WeatherInterpolatorTest.kt | 12+ | 插值 + 外推 + 无效过滤 + 降水 + 大跨度 |
| WeatherRepository | WeatherRepositoryTest.kt | — | JSON 解析 + WMO 码映射 |
| MeterRepository | MeterRepositoryTest.kt | — | CRUD + 智能插入 |
| UserPreferences | UserPreferencesTest.kt | — | DataStore 持久化 |
| ThemeDistRepository | ThemeDistRepositoryTest.kt | — | CSS 变量解析 |
| DeepSeekRepository | DeepSeekRepositoryTest.kt | — | API 调用 + 降级 |
| ChartViewModel | ChartViewModelTest.kt | — | 数据聚合 |

## 质量门禁 (Quality Gates)

### 必须通过（阻塞发布）
- [ ] `./gradlew :app:compileDebugKotlin` — 编译成功
- [ ] `./gradlew :app:testDebugUnitTest` — 全部单元测试通过
- [ ] 无 `critical` 级别的 code review findings

### 应该通过（警告但不阻塞）
- [ ] 新增代码有对应测试
- [ ] 测试覆盖率不下降
- [ ] 无新增 lint warnings

### 建议通过（最佳实践）
- [ ] 边界条件有测试覆盖
- [ ] 错误路径有测试覆盖
- [ ] 测试命名清晰描述行为

## 测试分类

### 1. 纯逻辑测试（无依赖）
**适用**: CostEngine, PredictiveAnalyzer, CarbonCalculator, SmartInputParser, WeatherInterpolator
**模式**: 直接调用，断言返回值
**示例**:
```kotlin
@Test
fun `electric tiers apply additive surcharge`() {
    val bill = CostEngineShared.calculate(rules = ..., totalKwh = 300.0, ...)
    assertEquals(140.0, bill.electricTotalCost, 0.01)
}
```

### 2. 依赖注入测试（Mock DAO/Prefs）
**适用**: AdaptiveClassifier, AnomalyDetector, MeterRepository
**模式**: MockK 模拟 DAO，验证交互
**示例**:
```kotlin
@Test
fun `empty records defaults when no cache`() = runTest {
    coEvery { prefs.getCachedThresholds() } returns null
    coEvery { dao.getElectricRecords() } returns flowOf(emptyList())
    val thresholds = classifier.getThresholds()
    assertEquals(ClassificationThresholds.DEFAULTS, thresholds)
}
```

### 3. Fake DAO 测试（轻量级）
**适用**: AnomalyDetector（需要 Flow 返回）
**模式**: 实现 FakeDao 接口，返回固定数据
**示例**:
```kotlin
private class FakeDao(
    private val electric: List<MeterRecord> = emptyList()
) : MeterRecordDao {
    override fun getElectricRecords(): Flow<List<MeterRecord>> = flowOf(electric)
    // ... 其他方法返回默认值
}
```

### 4. 时间依赖测试（固定时间）
**适用**: PredictiveAnalyzer, EventImpactAnalyzer
**模式**: 传入固定的 `now` 参数，消除时间依赖
**示例**:
```kotlin
private val now = LocalDateTime.of(2026, 7, 15, 12, 0)

@Test
fun `normal month prediction`() {
    val result = analyzer.predictMonth(records, now = now)
    assertEquals(15, result!!.daysElapsed)
}
```

## 测试数据工厂模式

### MeterRecord 工厂
```kotlin
private fun reading(time: LocalDateTime, total: Double) = MeterRecord(
    timestamp = time,
    isElectricRecorded = true,
    electricTotal = total
)

private fun note(time: LocalDateTime, text: String) = MeterRecord(
    timestamp = time, note = text
)
```

### DailyWeather 工厂
```kotlin
private fun w(date: String, tempMax: Double, tempMin: Double) = DailyWeather(
    date = LocalDate.parse(date), tempMax = tempMax, tempMin = tempMin
)
```

### ClassificationThresholds 工厂
```kotlin
private val defaults = ClassificationThresholds.DEFAULTS
private val custom = ClassificationThresholds(
    peakMin = 10000.0, peakMax = 11000.0,
    valleyMin = 8000.0, valleyMax = 9000.0,
    totalElectricMin = 16000.0, waterMax = 500.0
)
```
