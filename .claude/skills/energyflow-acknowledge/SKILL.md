---
name: energyflow-acknowledge
description: Thoroughly understand the EnergyFlow codebase — project structure, architecture decisions, data flow, known pitfalls
---

# EnergyFlow — Codebase Understanding Guide

**Use when**: Understanding the project / exploring code / not sure what to do.

## Step 1: Load Knowledge Context

Read the following documents in order (do not skip):

### Required Reading (Core Understanding)
1. `.claude/docs/architecture/overview.md` — Project identity, tech stack, module structure, data flow, file reference table
2. `.claude/docs/architecture/gotchas.md` — **Most important** — counter-intuitive pitfalls, naming traps, edge cases
3. `.claude/docs/architecture/adr-001-tab-navigation.md` — Why NavHost is not used
4. `.claude/docs/architecture/adr-002-hilt-wrapper-pattern.md` — KMP and Android layering pattern
5. `.claude/docs/architecture/adr-003-adaptive-classifier.md` — Adaptive classification thresholds

### On-Demand Reading (Deep Understanding)
- Data model: `meter-record.md` → Billing: `cost-engine.md` → Parsing: `smart-input-parser.md`
- Anomaly detection: `anomaly-detector.md` → Prediction: `predictive-analyzer.md`
- Carbon footprint: `carbon-and-insight.md` → KMP: `module-design.md`
- UI: `theme-and-navigation.md` / `chart-screen.md` / `settings-and-reports.md`
- External: `external-services.md` → DI: `app-entry-and-di.md`
- Build: `build-and-test.md` → Testing: `strategy.md` / `test-cases.md` / `process.md`

## Step 2: Build Mental Model

After reading, make sure you can answer:
- **Data flow**: `User Input → SmartInputParser → AnomalyDetector → MeterRepository → Room → ChartViewModel/InsightGenerator/PredictiveAnalyzer`
- **Layering**: shared = pure logic + kotlinx.datetime | app = Hilt DI + java.time + Android API
- **Why AnimatedContent instead of NavHost?** ViewModel stays alive, instant switching, no destroy/recreate
- **Why are meter readings cumulative?** Meter reading reads the meter number directly; the difference is consumption
- **Why is NeonYellow blue?** Legacy naming, actually points to ElectricStart (#00A8FF)

## Step 3: Output Structured Understanding

Output format (do not dump raw document content):
```markdown
## Project Overview
- Identity: [one sentence]
- Core domains: [billing/prediction/carbon footprint/...]
- Key design patterns: [Hilt Wrapper / Adaptive Learning / Smart Parse + AI Fallback]

## Modules I Care About
- File: [path]  Responsibility: [one sentence]  Pitfall: [related gotcha]

## Key Boundaries
- KMP: shared forbids java.time / android.*
- Hilt: Engine uses @Singleton class X @Inject constructor
- Compose: MonoFontFamily / theme colors only / collectAsStateWithLifecycle()
```

## Iron Rules
- ✅ Read gotchas.md — every time
- ✅ Understand ADRs — design decisions have reasons
- ❌ Do not write code at this stage — this is the understanding phase
- ❌ Do not skip gotchas.md — it is a collection of hard-won lessons

## Related Skills
- Onboarding: `energyflow-onboard` — a more concise entry path
- Data migration: `energyflow-data-migration` — understand Room/DataStore structure
- Running tests: `energyflow-test` — familiarize with the test system
