---
name: energyflow-diagnose
description: Bug diagnosis guide — systematic investigation, localization, and fix
---

# EnergyFlow — Bug Diagnosis Guide

**Use when**: Fixing bugs / investigating / errors / something doesn't work.

> **When to use this Skill vs Workflow?**
> - Simple bug (single layer, known cause) → continue using this skill for manual investigation
> - Complex bug (spans ≥3 layers) → use `workflow:bug-fix` (4-layer parallel diagnosis + auto-fix)
> - Batch fix N bugs → use `workflow:multi-fix` (N Agents parallel diagnosis → fix)

## Step 0: Check Known Pitfalls First

**Before diving into investigation, check the most common pitfalls in gotchas.md:**

| Symptom | Most Common Cause | Quick Verification |
|---------|-------------------|-------------------|
| `ClassNotFoundException` | Hilt build cache poisoning | `./gradlew :app:assembleDebug --rerun-tasks` |
| Data all lost | Room destructive migration | Check if schema version incremented |
| Billing result incorrect | Billing version migration reset rules | Check `CURRENT_BILLING_VERSION` |
| Peak/valley electricity is 0 | Peak/valley values are null | Check if records have peak/valley |
| Parse result wrong | AdaptiveClassifier threshold drift | Check classification thresholds |
| BuildConfig not found | AGP build features not enabled | `buildFeatures { buildConfig = true }` |

## Step 1: Reproduce

1. **Exact reproduction steps** — what operation, what input triggered the bug?
2. **Minimal input** — can it be reproduced with the least data?
3. **Write a reproduction test** — write a failing test in the corresponding test class

```kotlin
@Test
fun `reproduce bug: decreasing record not flagged`() {
    // Arrange — simulate data that triggers the bug
    val records = listOf(...)
    // Act
    val result = detector.checkElectricMonotonic(...)
    // Assert — should currently fail (bug still exists)
    assertNotNull(result) // Expected warning but none → this is the bug
}
```

## Step 2: Classify

Determine which layer the bug belongs to:

| Layer | Manifestation | Investigation Focus |
|-------|--------------|---------------------|
| **shared (KMP)** | Abnormal calculation result | Pure logic issue, verify directly with tests |
| **data (Android)** | Data storage/retrieval abnormal | Hilt injection, Room queries, DataStore |
| **ui (Compose)** | UI display abnormal | StateFlow collection, theme colors, lifecycle |
| **DI/infra** | Startup crash / injection failure | Hilt configuration, module installation |
| **external** | API call failure | Network/deserialization/fallback logic |

## Step 3: Locate

### Investigate by Layer

**shared pure logic** → construct test data directly, step-by-step debug:
```kotlin
val result = CostEngineShared.calculate(rules = ..., totalKwh = 300.0)
println("totalCost=${result.electricTotalCost}") // Compare with expected
```

**data layer** → check data flow:
```kotlin
// Print intermediate state
val parsed = parser.parseWithContext(input)
println("parsed=$parsed")
val warning = detector.checkElectricMonotonic(value, timestamp)
println("warning=$warning")
```

**ui layer** → check state collection and recomposition:
- Is `collectAsState()` used instead of `collectAsStateWithLifecycle()`?
- Is State modified outside Composition?
- Are theme colors correct in dark/light mode?

### Common Error Pattern Quick Reference

| Code Pattern | Problem | Correct Pattern |
|-------------|---------|-----------------|
| `collectAsState()` | Does not respect lifecycle | `collectAsStateWithLifecycle()` |
| `#XXXXXX` hardcoded color | Does not support theme switching | `ElectricColor` / `DarkBackground` etc. |
| `java.time.*` in shared | Breaks KMP compatibility | `kotlinx.datetime.*` |
| `val x = dao.getAll().first()` | Called in non-coroutine context | `suspend fun` or Flow collect |
| `electricPeak!!` | Peak electricity may be null | `electricPeak ?: 0.0` |
| `newVal - prevVal` (subtraction reversed) | Readings are cumulative | `current - previous` (larger minus smaller) |

## Step 4: Fix

1. **Write regression test first** — confirm test fails → fix → test passes
2. **Minimal change** — only change what's needed to fix the bug
3. **Do not refactor while fixing** — keep diff clean
4. **Check edge cases** — consider null / 0 / extreme values

## Step 5: Verify

```bash
# 1. Regression test
./gradlew :app:testDebugUnitTest --tests "YourBugTest"

# 2. All tests (confirm no other functionality broken)
./gradlew :app:testDebugUnitTest

# 3. Compile check
./gradlew :app:compileDebugKotlin
```

### Verification Checklist
- ✅ New regression test passes
- ✅ All existing tests pass
- ✅ Compilation succeeds
- ✅ If possible, manually verify the fix on device

## Special Scenarios

### Hilt / DI Issues
```
1. Clean build: ./gradlew clean
2. Clear Gradle cache: rm -rf ~/.gradle/caches
3. Rebuild: ./gradlew :app:assembleDebug --rerun-tasks
```

### Room Data Issues
```
1. Check schema version (AppDatabase.kt)
2. Check TypeConverters are correctly registered
3. Check DAO query SQL is correct
4. Use App Inspection or uninstall to clear data
```

### KMP Compilation Issues
```bash
./gradlew :shared:compileDebugKotlinAndroid --info  # Verbose logs
```

## Related Skills
- Run tests: `energyflow-test` — run regression tests and debug failures
- Build issues: `energyflow-build-debug` — Gradle/Hilt/compilation specific troubleshooting
- Pre-scan: `energyflow-quick-scan` — do a quick scan after fix
