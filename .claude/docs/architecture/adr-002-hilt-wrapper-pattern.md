# ADR-002: Hilt Wrapper Pattern for KMP Shared Module

## Status
Accepted

## Context
Business logic (cost calculation, prediction, anomaly detection, carbon footprint) needs to work across platforms (Android + potential iOS/Desktop). But the Android app uses Hilt for DI and `java.time` for dates, while the shared KMP module uses `kotlinx.datetime`.

## Decision
Create thin Hilt `@Singleton` wrappers in the Android `data/` package that:
1. Accept `UserPreferences` (DataStore) via `@Inject constructor`
2. Read configuration from DataStore flows
3. Convert `java.time` ↔ `kotlinx.datetime`
4. Delegate to the shared module's pure logic objects

## Consequences
- **Positive**: Shared logic is testable without Android framework
- **Positive**: Platform-specific concerns (DI, date types) are isolated in wrappers
- **Positive**: Shared module can be reused on other platforms
- **Negative**: Two layers of abstraction (wrapper + shared)
- **Negative**: Type conversion boilerplate in each wrapper

## Examples
```
Android: CostEngine (Hilt) → reads BillingRules from DataStore → calls CostEngineShared.calculate()
Android: PredictiveAnalyzer (Hilt) → converts java.time → calls PredictiveAnalyzerShared.predictMonth()
```

## Files
- `data/CostEngine.kt` → `shared/CostEngine.kt`
- `data/PredictiveAnalyzer.kt` → `shared/PredictiveAnalyzer.kt`
- `data/CarbonFootprint.kt` → `shared/CarbonFootprint.kt`
