---
name: energyflow-new-feature
description: Feature implementation guide — complete flow from requirement analysis to code verification
---

# EnergyFlow — New Feature Implementation Guide

**Use when**: Adding features / modifying code / implementing requirements.

> **When to use this Skill vs Workflow?**
> - Simple feature (single file, known approach) → continue using this skill for manual implementation
> - Complex feature (≥3 files + cross-module) → use `workflow:feature-development` (3-Agent panel design + parallel implementation)
> - Batch add N features → use `workflow:multi-feature` (N Agents parallel analysis → implementation)

## Step 1: Domain Analysis

### Required Reading (Select by Feature Domain)
| Feature Involves | Must-Read Doc |
|-----------------|---------------|
| Billing/pricing | `.claude/docs/data-layer/cost-engine.md` |
| Data parsing | `.claude/docs/data-layer/smart-input-parser.md` |
| Anomaly detection | `.claude/docs/data-layer/anomaly-detector.md` |
| Predictive analytics | `.claude/docs/analytics/predictive-analyzer.md` |
| Carbon footprint/insights | `.claude/docs/analytics/carbon-and-insight.md` |
| KMP shared logic | `.claude/docs/shared-kmp/module-design.md` |
| UI/charts | `.claude/docs/ui-layer/theme-and-navigation.md` + `chart-screen.md` |
| Settings/reports | `.claude/docs/ui-layer/settings-and-reports.md` |
| External API | `.claude/docs/data-layer/external-services.md` |
| Data model | `.claude/docs/data-layer/meter-record.md` |
| **All features (required)** | `.claude/docs/architecture/gotchas.md` |

### Understand Existing Design
- Read related ADRs (`adr-001`/`002`/`003`) — understand why it's designed this way
- Read corresponding test files — understand existing behavior contracts

## Step 2: Plan Design

Before writing code, answer these questions:
1. **Which layer does the change fall in?** shared (pure logic) / app:data (data layer) / app:ui (UI)
2. **Need new DI dependency?** @Singleton / @HiltViewModel / @Inject
3. **Need new Room entity/DAO?** Destructive migration will clear data
4. **Involves KMP boundary?** shared forbids java.time / android.*
5. **Need new DataStore key?** Watch for version migration
6. **Impact on existing tests?** Which tests need updating?

## Step 3: Checklist

Check each item while writing code:

### KMP Boundary Check
- [ ] shared module has no `import java.*` or `import android.*`
- [ ] shared module only uses `kotlinx.datetime`, not `java.time`
- [ ] Android wrapper correctly handles type conversion (java.time ↔ kotlinx.datetime)

### Hilt Check
- [ ] Engine class: `@Singleton class X @Inject constructor(deps)`
- [ ] ViewModel: `@HiltViewModel class X @Inject constructor(deps)`
- [ ] New Module correctly uses `@InstallIn(SingletonComponent::class)`

### Compose Check
- [ ] Font: Use `MonoFontFamily` (not default font)
- [ ] Colors: Only use theme colors (`ElectricColor` etc.), no hardcoded hex
- [ ] State: Flow uses `collectAsStateWithLifecycle()`
- [ ] Performance: Large list items use `remember{}`, lists use `animateItem()`

### Data Layer Check
- [ ] Check null before using nullable MeterRecord fields
- [ ] Readings are cumulative, not incremental — subtraction direction must be correct
- [ ] SmartInputParser year assumption (current year)
- [ ] Anomaly detection gates before saving

### Simplicity Check
- [ ] No abstractions created for single use
- [ ] No "in case we need it later" flexibility
- [ ] No error handling for impossible scenarios
- [ ] Reasonable code volume (if 200 lines can do it, don't write 500)
- [ ] No "while you're at it" optimization of adjacent code

## Step 4: Verification

```bash
# 1. Compile check
./gradlew :app:compileDebugKotlin

# 2. KMP compile check (if shared was changed)
./gradlew :shared:compileDebugKotlinAndroid

# 3. All unit tests
./gradlew :app:testDebugUnitTest

# 4. Related tests (faster)
./gradlew :app:testDebugUnitTest --tests "com.example.energyflow.data.YourTest"
```

### Verification Criteria
- ✅ Compilation passes (no warnings)
- ✅ All existing tests pass (no existing behavior broken)
- ✅ New logic has test coverage (at least edge cases)
- ✅ Code style matches existing code

## Step 5: Commit

Follow Conventional Commits specification:
```
<type>(<scope>): <description>
```
- type: feat / fix / refactor / test / docs / chore
- scope: data / ui / shared / di / test
- Example: `feat(data): add water prediction to ChartViewModel`

## Prohibited Actions
- ❌ Do not use java.time in shared module
- ❌ Do not hardcode hex colors — use theme colors
- ❌ Do not skip gotchas.md
- ❌ Do not "while you're at it" optimize adjacent code
- ❌ Do not create abstraction layers for single use
- ❌ Do not blindly use NavHost — maintain AnimatedContent pattern

## Related Skills
- Write tests: `energyflow-test` — TDD cycle and test templates
- Commit: `energyflow-commit` — Conventional Commits specification
- Pre-scan: `energyflow-quick-scan` — final check before committing
- Diagnose issues: `energyflow-diagnose` — if you encounter a bug during implementation
