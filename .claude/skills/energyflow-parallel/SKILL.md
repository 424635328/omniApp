---
name: energyflow-parallel
description: Parallel multi-task scheduling — execute multiple types of tasks simultaneously (bug fixes + feature development + refactoring etc.)
---

# EnergyFlow — Parallel Multi-Task Scheduling

**Use when**: Executing multiple independent tasks of different types simultaneously.

**Trigger words**: "fix bugs and add features at the same time" / "refactor while adding tests" / "do these things together" / "parallel processing"

> **Core principle**: If tasks are independent → use independent Agents/Workflows in parallel → time ≈ the slowest one

## Scheduling Quick Reference

| Task Type | Single Task | Batch (≥2) |
|-----------|-------------|-----------|
| Bug fix | `workflow:bug-fix` (4-layer parallel diagnosis) | `workflow:multi-fix` |
| New feature | `workflow:feature-development` (3-Agent panel) | `workflow:multi-feature` |
| Refactoring | Agent(type='general-purpose') | Multiple Agents in parallel |
| Testing | Agent(type='general-purpose') | Same as above |
| Scan/review | `workflow:full-review` (4 dimensions) | Same as left |
| Commit | `workflow:test-then-commit` | Same as left |

## Dependency Check

```
Can Task A and Task B run in parallel?
├── Read-only (diagnose/review/scan)?   → ✅ Always parallel — but grep/read only, NO Gradle
├── Change different files?             → ✅ Parallel EDITS ok — but compile serially afterwards
├── Change same file, different areas?  → ⚠️ Worktree isolation (see hard rules below)
├── Change same file, same function?    → ❌ Must be serial
├── A's output is B's input?            → ❌ Must be serial
└── Needs to compile/test?              → ❌ Gradle is a serial resource — one process per tree
```

## Hard Rules (deadlock prevention)

1. **One Gradle process per working tree at a time.** Concurrent `./gradlew` invocations
   block on Gradle's project lock (looks like a deadlock) and each may spawn a separate
   ~2GB daemon (system-wide lag). Parallel phases are read-only or edit-only; ONE
   designated agent runs the build after the phase completes.
2. **Worktree isolation is a last resort.** Never run Gradle builds inside a worktree
   (separate daemon + hundreds of MB of build output). Always remove the worktree and
   its branch when the task ends — stale worktrees under `.claude/worktrees/` have
   previously accumulated 683MB and 29 orphan branches.

## Execution Modes

| Mode | Use Case | Example |
|------|----------|---------|
| **Pure parallel** | Tasks are independent | Fix bugs in different modules + add independent features |
| **Parallel + merge** | Partial dependencies | Multiple changes → unified testing |
| **Pipeline** | A output → B input | Analysis → implementation → testing |

## Effort Tiers (token/latency efficiency)

When authoring workflow `agent()` calls, set `effort` by task type:

| Tier | Task type | Examples |
|------|-----------|----------|
| `'low'` | Mechanical: run command → report PASS/FAIL, grep scans, doc summarization, JSON extraction | compile-check, boundary-scan, full-suite, read:docs |
| default (omit) | Judgment: diagnosis, implementation, design, review, synthesis | diag:*, impl-*, design:*, synthesize-* |

Low-effort agents return faster and cheaper — this shortens the tail latency of parallel phases where a mechanical agent would otherwise be the straggler.

## Notes

- **Compilation conflicts**: When multiple Agents modify code simultaneously, compile once at the end via a single agent — never let each Agent compile on its own
- **Failure handling**: One task failing does not affect other tasks continuing
