# Gotchas & Tribal Knowledge

Non-obvious things that aren't clear from reading the code alone.

## Build & Runtime

### Hilt Build Cache Poisoning
**Symptom**: `ClassNotFoundException` on launch for a class that definitely exists.
**Cause**: Stale Gradle build cache after Hilt annotation processing changes.
**Fix**: `./gradlew :app:assembleDebug --rerun-tasks`
**See**: Memory file `hilt-build-cache-poisoning.md`

### Room Destructive Migration
`AppDatabase` uses `fallbackToDestructiveMigration()`. Schema changes **wipe all data**. This is intentional for development but means production needs proper migrations.

### DataStore File Name
DataStore file is `energy_flow_preferences` (defined in `DataStoreModule.kt`). Don't confuse with the database file `energy_flow_database`.

## Data Model

### MeterRecord Readings Are Cumulative
`electricTotal`, `waterTotal`, `gasTotal` are **cumulative readings**, NOT deltas. Consumption = current - previous. This is the #1 confusion for new developers.

### Timestamp Precision
`MeterRecord.timestamp` is `LocalDateTime` (minute precision). Room stores it as `Long` via `Converters.kt`. The index on `timestamp` accelerates range queries.

### Peak/Valley Independence
`electricPeak` and `electricValley` can be:
- Both null (only total recorded)
- Both present (full peak/valley breakdown)
- One present, one null (partial data)
- Their sum should equal `electricTotal`, but this is NOT enforced

### Null vs Zero
`null` means "not recorded", `0.0` means "recorded as zero". The code treats them differently:
- `areValuesSame()`: null == null (same), null != 0.0 (different)
- Display: null → "-", 0.0 → "0"

## SmartInputParser

### Year Assumption
`SmartInputParser` uses `Year.now().value` as the year for all parsed dates. Historical data from previous years cannot be parsed this way — use batch import with full dates.

### Peak/Valley Pairing State Machine
`PendingElectric` is a state machine within `parseWithContext()`:
- First numeric in peak range → pending peak
- Second numeric in valley range → auto-merge with pending peak
- If both arrive, they're combined into one record with `total = peak + valley`
- If only one arrives by end of input, it's saved independently

### Classification Order Matters
`classifyValue()` checks in this order: water → peak → valley → total → default. A value of 8500 matches "valley" (7000-8000) before it could match "total" (≥15000). The thresholds are adaptive — `AdaptiveClassifier` learns from history.

## AnomalyDetector

### Spike Detection Uses Normalized Daily Rate
`checkElectricSpike()` doesn't compare raw deltas. It normalizes to daily rate (`delta / days`), then compares against the average of the last 4 historical daily rates. A 5x spike is flagged.

### Batch Drop Only Checks History→Candidate Pairs
`findBatchDropWarning()` only flags when the **previous** record is from history (id > 0) and the **current** is a new candidate. Decreases within the batch are ignored (users may enter data out of order).

## CostEngine

### Surcharge Distribution
Tier surcharges are distributed proportionally across peak/valley/flat:
```
avgSurcharge = (tier2 * surcharge2 + tier3 * surcharge3) / totalKwh
```
This means each kWh gets the same average surcharge, regardless of peak/valley.

### Billing Version Migration
`UserPreferences.billingRules` has a version counter (`CURRENT_BILLING_VERSION = 3`). When the version increments, ALL billing settings reset to Nanjing defaults. This is intentional — it ensures users get correct prices after policy changes.

## ChartViewModel

### Water Consumption Uses Independent Records
`calculateWaterConsumptionInWindow()` uses `waterRecords` independently, NOT the water readings from electric records. This prevents anchoring to electric timestamps.

### Forecast Points Use -1.0 Sentinel
`DailyConsumption.estimatedCost = -1.0` marks a projected data point (not actual). The chart uses this to render dashed lines.

### Prediction Snapshot Throttling
Prediction snapshots are saved at most once per day (`now.dayOfMonth - cachedSnapshot.savedDayOfMonth >= 1`).

## UI

### Tab Instant Switch (No NavHost)
`AppNavGraph` does NOT use NavHost. It uses `when(tab)` with `AnimatedContent`. ViewModels are cached at the Activity level via `hiltViewModel()`. Switching tabs doesn't destroy/recreate ViewModels.

### NeonYellow Is NOT Yellow
`NeonYellow` is an alias for `ElectricStart` (#00A8FF, blue). `NeonBlue` is an alias for `WaterStart` (#00DDBB, cyan). These are legacy names from an earlier color scheme.

### collectAsState() Inconsistency (Fixed)
`ChartScreen` previously used `collectAsState()`; it was fully migrated to `collectAsStateWithLifecycle()` (2026-07-26, commit eba3f4b). There are now NO `collectAsState()` exemptions anywhere in `ui/`.

### renderHeavy Delay
ChartScreen delays heavy panel rendering by 50ms (`delay(50)`) to avoid first-frame JIT compilation jank.

## KMP Boundary

### java.time vs kotlinx.datetime
- Android code uses `java.time.LocalDateTime`
- Shared KMP code uses `kotlinx.datetime.LocalDateTime`
- Conversion happens in Android wrapper classes (e.g., `PredictiveAnalyzer.kt` has `toKtDateTime()` extension)

### Shared Module Has No Android Dependencies
`shared/src/commonMain/` must NEVER import `android.*` or `java.*`. Use `kotlinx.*` only. Platform-specific code goes in `androidMain/` or `desktopMain/`.

## External APIs

### Open-Meteo Is Free (No Key)
Weather API is completely free, no API key needed. Default coordinates: Nanjing (32.06, 118.80).

### DeepSeek API Key Is Optional
If no DeepSeek API key is configured, `analyze()` returns `null` silently. The app works fully without AI features.

### ThemeDist Is Optional
If `themeDistEnabled` is false or the API fails, the app falls back to the default color scheme.
