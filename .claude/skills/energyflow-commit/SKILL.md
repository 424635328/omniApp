---
name: energyflow-commit
description: Conventional Commits specification — correct format, scope selection, commit message generation
---

# EnergyFlow — Commit Specification

**Use when**: Writing commit messages / committing code / creating PRs.

## Commit Format

```
<type>(<scope>): <description>

[body — optional, explain why]

[footer — optional, link to issue]
```

## Type Quick Reference

| Type | When to Use | Example |
|------|-------------|---------|
| `feat` | New user-visible capability | `feat(data): add water prediction` |
| `fix` | Fix user-perceivable issue | `fix(ui): correct NeonYellow color alias` |
| `refactor` | Change structure without changing behavior | `refactor(shared): extract billing to CostEngineShared` |
| `test` | Add/modify tests | `test(data): add peak-valley edge cases` |
| `docs` | Documentation/comment changes | `docs: update gotchas with color system` |
| `chore` | Build/dependencies/tools | `chore: update AGP to 8.5` |
| `style` | Code formatting, no logic impact | `style: fix indentation in MeterRecord` |
| `perf` | Performance optimization | `perf(ui): lazy load chart heavy panels` |

## Scope Quick Reference

| Scope | Corresponding Directory | When to Use |
|-------|------------------------|-------------|
| `data` | `app/.../data/` | Data layer changes (Engine/Parser/Repository/DAO) |
| `ui` | `app/.../ui/` | UI layer changes (Screen/ViewModel/Theme/Components) |
| `shared` | `shared/.../` | KMP shared module changes |
| `di` | `app/.../di/` | DI configuration changes |
| `widget` | `app/.../widget/` | Desktop widget |
| `tile` | `app/.../ui/tile/` | Quick settings tile |
| `test` | `app/src/test/` | Pure test changes |
| `build` | `*.gradle.kts` | Build configuration |
| (empty) | Global | Cross-scope or unclassifiable changes |

## Scope Selection for Multi-File Changes

```
Changed app/data/CostEngine.kt → scope: data
Changed app/ui/chart/ChartScreen.kt + app/ui/chart/ChartViewModel.kt → scope: ui
Changed app/data/CostEngine.kt + shared/CostEngine.kt → scope: shared (shared takes precedence)
Changed app/data/ + app/ui/ + shared/ → scope: choose primary change, or omit
```

## Examples

```bash
# Simple feature
feat(data): add gas consumption chart to ChartViewModel

# Bug fix (with root cause)
fix(ui): ChartScreen uses collectAsState instead of collectAsStateWithLifecycle

# Refactoring
refactor(shared): extract anomaly detection to AnomalyDetectorShared

# With body (explain reason)
feat(data): add batch import deduplication

Uses timestamp + total value for deduplication within
tolerance of 0.1. Prevents duplicate entries from
repeated batch imports.

# Link to issue
fix(data): correct tier3 surcharge calculation

The tier3 surcharge was incorrectly applied to tier2 usage.
Now correctly applies only to consumption above tier2 limit.

Closes #42
```

## Pre-Commit Check

```bash
# 1. Confirm diff is clean (only see your own changes)
git diff --staged

# 2. Compile + test
./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest

# 3. Commit
git commit -m "feat(data): add water prediction"
```

## Prohibited Commits

```
❌ fix bug                 # No type/scope
❌ WIP                    # Do not commit half-done work
❌ fix stuff              # Unclear description
❌ feat + fix + refactor  # Do one thing at a time
```

## Related Skills
- Run tests: `energyflow-test` — all tests must pass before committing
- Pre-scan: `energyflow-quick-scan` — quality gate before committing
