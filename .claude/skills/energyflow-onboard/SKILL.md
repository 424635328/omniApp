---
name: energyflow-onboard
description: New contributor onboarding — complete path from zero to first PR
---

# EnergyFlow — New Contributor Onboarding

**Use when**: New member joins the project / first time looking at code / needs quick ramp-up.

## 5-Minute Quick Overview

EnergyFlow is a **household energy consumption tracking Android App** (Kotlin + Compose + Hilt + Room + KMP) for:
- 📝 Recording electric/water/gas meter readings (supports 11 natural language input formats)
- 💰 Calculating tiered electricity costs (peak/valley time-of-use + tiered surcharge)
- 📊 Visualizing energy consumption trends + monthly predictions (double exponential smoothing algorithm)
- 🌱 Carbon footprint tracking + green badges
- 🤖 AI smart analysis (DeepSeek) + weather integration (Open-Meteo)

## First Hour: Build Mental Model

### 1. Read Core Documents (30 minutes)
In order:
1. `.claude/docs/architecture/overview.md` — Project panorama (10 minutes)
2. `.claude/docs/architecture/gotchas.md` — Must-know pitfalls (10 minutes)
3. `.claude/docs/architecture/adr-001-tab-navigation.md` — Architecture decisions (5 minutes)
4. `.claude/docs/architecture/adr-002-hilt-wrapper-pattern.md` — Layering pattern (5 minutes)

### 2. Understand Data Flow (15 minutes)
```
User inputs "7.15 14.30 16639 880"
  → SmartInputParser (regex parsing + AI fallback)
    → AnomalyDetector (monotonic increase + spike check)
      → MeterRepository → Room database
        → ChartViewModel (chart aggregation + prediction + billing + carbon footprint)
          → ChartScreen / MainScreen (Compose UI)
```

### 3. Get It Running (15 minutes)
```bash
# Compile
./gradlew :app:compileDebugKotlin

# Test
./gradlew :app:testDebugUnitTest

# Build APK (requires Android SDK)
./gradlew :app:assembleDebug
```

## First PR: Change Something Simple

### Recommended Starter Tasks
1. **Fix an "info" level code review finding** — small change, low risk
2. **Add unit tests for an existing function** — learn testing patterns
3. **Fix a `collectAsState()` → `collectAsStateWithLifecycle()`** — learn Compose lifecycle
4. **Add comments to a data class** — understand data model

### Flow
```bash
# 1. Create branch
git checkout -b fix/simple-thing

# 2. Change code (read CLAUDE.md + corresponding skill)

# 3. Verify
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest

# 4. Commit
git commit -m "fix(ui): collectAsState → collectAsStateWithLifecycle in XxxScreen"

# 5. Push & create PR
```

## Key Resources

| What You Need | Where to Find |
|--------------|---------------|
| Understand architecture | `.claude/docs/architecture/` |
| Understand data model | `.claude/docs/data-layer/meter-record.md` |
| Understand UI components | `.claude/docs/ui-layer/` |
| Know how to test | `.claude/docs/testing/` / `energyflow-test` skill |
| Know how to commit | `energyflow-commit` skill |
| Pre-commit check | `energyflow-quick-scan` skill |
| Encountered a bug | `energyflow-diagnose` skill |
| Need to refactor | `energyflow-refactor` skill |

## Common Newcomer Pitfalls

| Pitfall | Why It's Easy to Make | Correct Approach |
|---------|----------------------|------------------|
| Using `java.time` in shared | IDE auto-import | Only use `kotlinx.datetime` |
| Hardcoding colors | Looks convenient | Check theme color table |
| `electricPeak!!` crash | Assumed value always exists | Use `?: 0.0` |
| Subtraction direction reversed | Intuition is consumption | Readings are cumulative, larger minus smaller |
| Changed shared without compile check | Only compiled app | `./gradlew :shared:compileDebugKotlinAndroid` |
> More pitfalls → `.claude/docs/architecture/gotchas.md`

## Related Skills
- Codebase understanding: `energyflow-acknowledge` — deep architecture understanding
- First PR: `energyflow-new-feature` — complete path from requirement to verification
