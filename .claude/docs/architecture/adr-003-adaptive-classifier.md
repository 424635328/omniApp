# ADR-003: Adaptive Classification Thresholds

## Status
Accepted

## Context
`SmartInputParser` needs to classify numeric input as electric/water/peak/valley. Hard-coded thresholds fail for users with different meter ranges (e.g., commercial vs residential).

## Decision
Implement `AdaptiveClassifier` that:
1. Reads historical records from Room
2. Computes average/min/max for each meter type
3. Sets thresholds: peak ±15%, valley ±15%, water max ×1.2, total min ×0.85
4. Caches results in DataStore (avoids recalc on every cold start)
5. Re-learns on every insert (debounced to 5 minutes)

## Consequences
- **Positive**: Works for any user without manual configuration
- **Positive**: Self-correcting as data accumulates
- **Negative**: First few records may be misclassified (no history yet → falls back to defaults)
- **Negative**: Thresholds can drift if user enters anomalous data

## Defaults (no history)
```kotlin
totalElectricMin = 15000.0
peakMin = 9000.0, peakMax = 10000.0
valleyMin = 7000.0, valleyMax = 8000.0
waterMax = 1000.0
```

## Implementation
- `data/AdaptiveClassifier.kt` — computation + cache
- `data/ClassificationThresholds.kt` — data class with defaults
- `data/UserPreferences.kt` — DataStore cache keys (TH_TOTAL_ELECTRIC_MIN, etc.)
