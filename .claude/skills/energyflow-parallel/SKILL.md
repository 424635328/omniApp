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
├── Change different files?            → ✅ Use Agents directly
├── Change same file, different areas?  → ✅ Use worktree isolation
├── Change same file, same function?    → ❌ Must be serial
├── A's output is B's input?           → ❌ Must be serial
└── Share compilation artifacts?        → ⚠️ Stagger compilation timing
```

## Execution Modes

| Mode | Use Case | Example |
|------|----------|---------|
| **Pure parallel** | Tasks are independent | Fix bugs in different modules + add independent features |
| **Parallel + merge** | Partial dependencies | Multiple changes → unified testing |
| **Pipeline** | A output → B input | Analysis → implementation → testing |

## Notes

- **Compilation conflicts**: When multiple Agents modify code simultaneously, compile together at the end
- **Worktree isolation**: Use `isolation: 'worktree'` for changes to the same file
- **Failure handling**: One task failing does not affect other tasks continuing
