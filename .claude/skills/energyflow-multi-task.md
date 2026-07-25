---
name: energyflow-multi-task
description: 并行多任务编排——同时修复多个bug、同时实现多个独立功能
---

# EnergyFlow — 并行多任务编排

**用途**: 批量修 bug / 批量加功能 / 多个独立任务同时执行时使用。

## 核心原则

> 如果 N 个任务互不依赖 → N 个 Agent 并行执行 → 耗时 ≈ 最慢的那个，而非 N×单个耗时

## 场景一：批量修 Bug

### 触发词
"修这些bug" / "批量修复" / "同时修" / "这几个问题" / "fix these bugs"

### 执行方式
```
workflow:multi-fix
```

传入参数：`args.bugs = ["bug1描述", "bug2描述", ...]` 或 `args.bugs = "bug1; bug2; bug3"`

### 执行流程（自动并行）
```
Phase 1 — Diagnose: 所有 bug 并行诊断（每个 bug 一个 Agent）
    Bug #1 诊断  ─┐
    Bug #2 诊断  ─┤ 并行（同时进行）
    Bug #3 诊断  ─┘
         ↓
Phase 2 — Fix (Pipeline): 诊断完一个就开始修一个，不等待其他
    Bug #1 修复  ─┐
    Bug #2 修复  ─┤ Pipeline（Bug #1 修好后 Bug #2 可能还在诊断）
    Bug #3 修复  ─┘
         ↓
Phase 3 — Verify: 全部修完后跑一次全量测试
```

### 单 Bug 优化路径
如果只有 1 个 bug → 自动走并行分层诊断（data/shared/ui/di 四层同时排查）

## 场景二：批量加功能

### 触发词
"加这些功能" / "同时实现" / "这几个需求"

### 执行方式
```
workflow:multi-feature
```

传入参数：`args.features = ["功能1描述", "功能2描述", ...]`

### 执行流程（自动并行）
```
Phase 1 — Analyze: 所有功能并行分析领域知识
Phase 2 — Implement (Pipeline): 分析完一个就开始实现一个
Phase 3 — Verify: 全部实现后跑全量测试
```

## 判断任务是否可并行

| 条件 | 可并行 | 不可并行 |
|------|--------|---------|
| 改不同文件 | ✅ | — |
| 改同一文件不同区域 | ✅ (用 worktree 隔离) | — |
| 改同一文件同一函数 | — | ❌ 需串行 |
| Bug A 的修复依赖 Bug B | — | ❌ 需串行 |
| 共享同一依赖变更 | — | ❌ 先改依赖，再并行 |

## 调用示例

```
用户: "修这3个bug: 1) 峰谷电费计算错误 2) ChartScreen状态丢失 3) KMP模块用了java.time"

Agent 自动执行:
→ workflow:multi-fix { bugs: ["峰谷电费计算错误", "ChartScreen状态丢失", "KMP模块用了java.time"] }
→ 3个Agent并行诊断 → Pipeline修复 → 全量验证
```

## 相关 Skills
- 单 Bug 修复: `energyflow-diagnose` — 单个 bug 的系统化排查
- 单功能实现: `energyflow-new-feature` — 单个功能的完整流程
- 预检: `energyflow-quick-scan` — 全部修完后的质量闸门
