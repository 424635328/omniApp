---
name: energyflow-refactor
description: Safe refactoring guide — dependency analysis, plan design, precise changes, verification rollback
---

# EnergyFlow — Safe Refactoring Guide

**Use when**: Refactoring / restructuring / changing architecture.

## Core Principle

> Refactoring = changing structure without changing behavior. If behavior changes, that is a "rewrite", not a "refactor".

## Step 1: Understand the Current State

### Required Reading
1. `.claude/docs/architecture/overview.md` — Overall architecture
2. `.claude/docs/architecture/adr-001-tab-navigation.md` — Navigation design decisions
3. `.claude/docs/architecture/adr-002-hilt-wrapper-pattern.md` — Layering pattern
4. `.claude/docs/architecture/adr-003-adaptive-classifier.md` — Adaptive thresholds
5. `.claude/docs/architecture/gotchas.md` — **Required** — avoid pitfalls
6. `.claude/docs/shared-kmp/module-design.md` — KMP module design

### Draw the Dependency Graph
```
Class to refactor → Who depends on it? → What does it depend on?
                  → Which tests cover it?
                  → Which UI components use it?
```

Use search tools to find all references:
```bash
grep -r "ClassName" --include="*.kt" app/src/
grep -r "ClassName" --include="*.kt" shared/src/
```

## Step 2: Make a Plan

### Answer These Questions
1. **Scope** — Changing one file? One package? Cross-module?
2. **Behavior guarantee** — How to ensure behavior is unchanged after refactoring? (All tests pass)
3. **Rollback plan** — If something goes wrong, how to revert?
4. **Impact scope** — Which files will be affected? How many lines changed?
5. **KMP boundary** — Does it involve the shared module? Are type conversions needed?

### Choose Strategy

| Refactoring Type | Strategy | When |
|-----------------|----------|------|
| Extract method/class | Pure IDE refactor → test verify | Code duplication |
| Move file | git mv → update imports → test | Package structure unreasonable |
| Change interface | Add new interface first → migrate callers → delete old interface | Poor interface design |
| Change DI | Verify new Module first → switch → delete old Module | DI too coupled |
| KMP extraction | Write logic in app layer → test → extract to shared | Found cross-platform reusable logic |

## Step 3: Precise Changes

### Change Rules
- [ ] Do one refactoring at a time (extract + move separately)
- [ ] Run all tests between each step
- [ ] Do not change behavior — if tests fail, you changed behavior
- [ ] Do not change adjacent code — keep diff to only refactoring
- [ ] Run `grep` after changes to confirm no old references remain

### KMP Extraction Flow (Special Case)
```
1. Create pure logic class in shared module
2. Copy logic from app layer → replace java.time with kotlinx.datetime
3. Create Hilt wrapper in app layer (@Singleton + @Inject)
4. Update app layer callers → compile → test
5. Delete old implementation in app layer
```

## Step 4: Verification

```bash
# 1. Full compilation
./gradlew :app:compileDebugKotlin
./gradlew :shared:compileDebugKotlinAndroid

# 2. Full tests (the most important refactoring verification)
./gradlew :app:testDebugUnitTest

# 3. Confirm no dead code
grep -r "OldClassName" --include="*.kt" app/src/ shared/src/
```

### Verification Checklist
- ✅ All tests pass (consistent with pre-refactoring)
- ✅ No new compilation warnings
- ✅ No lingering old imports / old class name references
- ✅ KMP boundary correct (shared has no android.* / java.*)
- ✅ git diff contains only structural changes, no behavior changes

## Prohibited Actions

- ❌ Do not change behavior "while you're at it" — behavior and structure in separate commits
- ❌ Do not skip all tests — this is the only safety net
- ❌ Do not refactor + add features at the same time — separate PRs
- ❌ Do not delete public APIs that may be externally referenced (unless confirmed no callers)
- ❌ Do not override architecture decisions in ADRs without discussion first
- ❌ Do not replace AnimatedContent with NavHost — this is an intentional design decision

## Related Skills
- Run tests: `energyflow-test` — must run all tests after refactoring
- Commit: `energyflow-commit` — use refactor type for refactoring
- Data migration: `energyflow-data-migration` — if schema changes are involved
