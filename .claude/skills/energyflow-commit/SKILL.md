---
name: energyflow-commit
description: Conventional Commits 提交规范——正确格式、scope选择、commit message 生成
---

# EnergyFlow — 提交规范

**用途**: 写 commit message / 提交代码 / 创建 PR 时使用。

## 提交格式

```
<type>(<scope>): <description>

[body — 可选，解释为什么]

[footer — 可选，关联 issue]
```

## Type 速查

| Type | 中文 | 何时用 | 示例 |
|------|------|-------|------|
| `feat` | 新功能 | 用户可见的新能力 | `feat(data): add water prediction` |
| `fix` | Bug修复 | 修复用户可感知的问题 | `fix(ui): correct NeonYellow color alias` |
| `refactor` | 重构 | 改结构不改行为 | `refactor(shared): extract billing to CostEngineShared` |
| `test` | 测试 | 新增/修改测试 | `test(data): add peak-valley edge cases` |
| `docs` | 文档 | 文档/注释变更 | `docs: update gotchas with color system` |
| `chore` | 杂项 | 构建/依赖/工具 | `chore: update AGP to 8.5` |
| `style` | 格式 | 代码格式，不影响逻辑 | `style: fix indentation in MeterRecord` |
| `perf` | 性能 | 性能优化 | `perf(ui): lazy load chart heavy panels` |

## Scope 速查

| Scope | 对应目录 | 何时用 |
|-------|---------|-------|
| `data` | `app/.../data/` | 数据层变更（Engine/Parser/Repository/DAO） |
| `ui` | `app/.../ui/` | UI 层变更（Screen/ViewModel/Theme/Components） |
| `shared` | `shared/.../` | KMP 共享模块变更 |
| `di` | `app/.../di/` | DI 配置变更 |
| `widget` | `app/.../widget/` | 桌面小组件 |
| `tile` | `app/.../ui/tile/` | 快速设置磁贴 |
| `test` | `app/src/test/` | 纯测试变更 |
| `build` | `*.gradle.kts` | 构建配置 |
| (空) | 全局 | 跨多个 scope 或无法归类的变更 |

## 多文件变更时的 Scope 选择

```
改了 app/data/CostEngine.kt → scope: data
改了 app/ui/chart/ChartScreen.kt + app/ui/chart/ChartViewModel.kt → scope: ui
改了 app/data/CostEngine.kt + shared/CostEngine.kt → scope: shared (以shared为准)
改了 app/data/ + app/ui/ + shared/ → scope 选主要变更，或省略
```

## 示例

```bash
# 简单功能
feat(data): add gas consumption chart to ChartViewModel

# Bug 修复（含根因）
fix(ui): ChartScreen uses collectAsState instead of collectAsStateWithLifecycle

# 重构
refactor(shared): extract anomaly detection to AnomalyDetectorShared

# 带 body（解释原因）
feat(data): add batch import deduplication

Uses timestamp + total value for deduplication within
tolerance of 0.1. Prevents duplicate entries from
repeated batch imports.

# 关联 issue
fix(data): correct tier3 surcharge calculation

The tier3 surcharge was incorrectly applied to tier2 usage.
Now correctly applies only to consumption above tier2 limit.

Closes #42
```

## 提交前检查

```bash
# 1. 确认 diff 干净（只看自己的改动）
git diff --staged

# 2. 编译 + 测试
./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest

# 3. 提交
git commit -m "feat(data): add water prediction"
```

## 禁止的提交

```
❌ fix bug                 # 没有 type/scope
❌ WIP                    # 不要提交半成品
❌ fix stuff              # 描述不清晰
❌ feat + fix + refactor  # 一次只做一件事
```

## 相关 Skills
- 跑测试: `energyflow-test` — 提交前必须全量测试通过
- 预检: `energyflow-quick-scan` — 提交前的质量闸门
