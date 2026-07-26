---
name: energyflow-test
description: Test running and debugging — quick execution, result interpretation, writing new tests
---

# EnergyFlow — Test Running and Debugging

**Use when**: Running tests / debugging test failures / writing new tests.

## Quick Execution

```bash
# All tests
./gradlew :app:testDebugUnitTest

# Single test class
./gradlew :app:testDebugUnitTest --tests "com.example.energyflow.data.CostEngineTest"

# Single test method (using backtick description)
./gradlew :app:testDebugUnitTest --tests "com.example.energyflow.data.CostEngineTest.electric tiers apply additive surcharge"

# KMP shared module
./gradlew :shared:jvmTest

# Compile check (faster than running tests)
./gradlew :app:compileDebugKotlin
./gradlew :shared:compileDebugKotlinAndroid
```

## Test File Quick Reference

| Test File | Class Under Test | Test Count | Quick Command |
|-----------|-----------------|------------|---------------|
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

## Test Failure Troubleshooting

### Common Failure Causes
| Symptom | Possible Cause | Fix |
|---------|---------------|-----|
| assertEquals with small difference | Floating point precision | Increase delta or use `roundTo` |
| assertEquals with large difference | Logic error or wrong test data | Check calculation logic |
| NullPointerException | Incomplete mock | Add coEvery or use Fake |
| ClassCastException | Type mismatch | Check cast |
| Unresolved reference | Missing import | Auto-add import |
| coEvery not matched | MockK stub order | Check stub chain |

### Debugging Tips
```kotlin
// 1. Print intermediate values (temporary debug)
println("DEBUG: tier1=$tier1, tier2=$tier2, avgSurcharge=$avgSurcharge")

// 2. Progressively comment out to narrow scope
// 3. Check if Mock is correctly mounted (coEvery vs every)
// 4. Check if time dependency is fixed (do not use LocalDateTime.now())
```

## New Test Template

### Pure Logic Tests (CostEngine/PredictiveAnalyzer/CarbonCalculator)
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

### Repository/DAO Tests (Require Mock)
```kotlin
@Test
fun `repository deduplicates identical insert`() = runTest {
    coEvery { dao.getElectricRecords() } returns flowOf(emptyList())
    val result = repository.smartInsert("7.15 14.30 16639")
    assertTrue(result is InsertResult.Success)
}
```

### Test Conventions
- ✅ Method names use backtick descriptions: `` `electric tiers apply additive surcharge` ``
- ✅ Use AAA pattern (Arrange-Act-Assert)
- ✅ Use delta for floating point: `assertEquals(expected, actual, 0.01)`
- ✅ Use fixed time for time dependencies, not `LocalDateTime.now()`
- ❌ Do not use names like `testCase1` / `testCalculate`
- ❌ Do not share mutable state between tests

## Related Skills
- Bug diagnosis: `energyflow-diagnose` — writing a reproduction test is the first step in diagnosis
- Pre-scan: `energyflow-quick-scan` — scan after tests pass before committing
