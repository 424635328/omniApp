---
name: energyflow-parallel
description: 并行多任务调度——同时执行多种类型任务（bug修复+功能开发+重构等）
---

# EnergyFlow — 并行多任务调度

**用途**: 同时执行多种不同类型的独立任务时使用。

**触发词**: "同时修bug和加功能" / "一边重构一边加测试" / "这几个事情一起做" / "并行处理"

> **核心原则**: 如果任务互不依赖 → 用独立的 Agent/Workflow 并行执行 → 耗时 ≈ 最慢的那个

## 调度速查表

| 任务类型 | 单个任务 | 批量（≥2） |
|---------|---------|-----------|
| Bug 修复 | `workflow:bug-fix`（4层并行诊断） | `workflow:multi-fix` |
| 新功能 | `workflow:feature-development`（3-Agent面板） | `workflow:multi-feature` |
| 重构 | Agent(type='general-purpose') | 多个 Agent 并行 |
| 测试 | Agent(type='general-purpose') | 同上 |
| 扫描/审查 | `workflow:full-review`（4维度） | 同左 |
| 提交 | `workflow:test-then-commit` | 同左 |

## 依赖检查

```
任务A 和 任务B 可并行吗？
├── 改不同文件？            → ✅ 直接用 Agent
├── 改同一文件不同区域？     → ✅ 用 worktree 隔离
├── 改同一文件同一函数？     → ❌ 需串行
├── A 的输出是 B 的输入？   → ❌ 需串行
└── 共享编译产物？          → ⚠️ 错开编译时机
```

## 执行模式

| 模式 | 适用场景 | 示例 |
|------|---------|------|
| **纯并行** | 任务互不依赖 | 修不同模块的 bug + 加独立功能 |
| **并行+汇合** | 部分依赖 | 多个改动 → 统一测试 |
| **Pipeline** | A 输出 → B 输入 | 分析 → 实现 → 测试 |

## 注意事项

- **编译冲突**: 多 Agent 同时改代码时，最后统一编译
- **Worktree 隔离**: 同一文件的改动用 `isolation: 'worktree'`
- **失败处理**: 某任务失败不影响其他任务继续
