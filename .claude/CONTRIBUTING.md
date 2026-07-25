# Contributing to .claude/ — Agent 系统维护指南

本文件指导如何维护和扩展 `.claude/` agent 知识库系统。

## 架构分层

```
CLAUDE.md              → 每次对话自动加载（决策树 + 铁律 + 构建命令）
    ↓ 路由
Skills (*.md)          → 按需读取（触发条件 + 文档索引 + 检查清单 + 验证）
    ↓ 指向
Docs (*.md)            → 按需读取（深度参考资料：架构/算法/数据模型/测试/UI）
    ↓ 内容供给
Agents (*.md)          → Workflow 调用（审查维度 + 检查清单 + 验证命令）
    ↓ 编排
Workflows (*.js)       → 显式调用（多Agent pipeline/parallel 编排）

shared/rules.md        → 单一事实源：铁律、已知忽略、验证命令、Prompt模板
                          ↑ 所有层引用此文件而非各自复制
```

## 数据流

```
用户消息 → CLAUDE.md(决策树匹配) → Skill(触发条件+必读doc) → Doc(深度知识)
                                                              ↓
                                                        Agent(审查规则)
                                                              ↓
                                                        Workflow(多Agent编排)

规则变更 → shared/rules.md(单一事实源) → 自动传播到所有引用者
```

## 核心原则：单一事实源

**`.claude/shared/rules.md` 是所有铁律、已知忽略、验证命令的唯一权威来源。**

- Agent 定义中引用它，不再内联复制
- Workflow 脚本中引用它（通过注释 `// canonical source: .claude/shared/rules.md`）
- Skill 文件中引用它
- 修改规则 → 只改这一个文件

## 如何新增一个 Skill

1. 在 `.claude/skills/` 创建目录 `energyflow-<name>/`，在其中创建 `SKILL.md`
2. 文件格式：
```markdown
---
name: energyflow-<name>
description: 一句话描述 skill 用途
---

# EnergyFlow — <中文标题>

**用途**: <触发这个skill的用户意图>

> **什么时候用这个 Skill vs Workflow?** (如果有对应 workflow)

## 第一步：<行动>
...
## 检查清单
...
## 验证
...
## 禁止事项
...
## 相关 Skills
- `energyflow-xxx` — 关联的其他 skill
```

3. 在 `CLAUDE.md` 的决策树中添加一条路由
4. 在 `.claude/README.md` 中更新目录结构
5. 如果新 skill 引用了现有 docs → 无需新建 doc；如果需要新 doc → 在 docs/ 下创建
6. 验证：Skill 应该可通过 `/energyflow-<name>` 直接调用，或通过描述自动触发

> **注意**: Skill 文件必须位于 `.claude/skills/<name>/SKILL.md` 才会被 Claude Code 自动发现。平铺的 `.claude/skills/<name>.md` 不会被注册。

## 如何新增一个 Doc

1. 在对应的 `.claude/docs/<category>/` 下创建
2. 格式：清晰的标题 + 表格化速查信息 + 文件路径索引
3. 在相关的 skill 文件中添加指向

## 如何新增一个 Agent

1. 在 `.claude/agents/` 创建 `<name>.md`
2. 文件格式：
```yaml
---
name: <name>
description: <用途>
model: sonnet
tools: [Read, Grep, Glob, Bash]  # 所有 reviewer 应有 Bash 用于验证
---
# <Title>

**Rules**: Iron rules + known ignores → `.claude/shared/rules.md`.
Protocol → `.claude/docs/agents/protocol.md`.

## Startup
Run `git diff main...HEAD --name-only` to scope review.

## Review Checklist
...
```
3. **不要内联铁律** — 引用 `shared/rules.md` 即可
4. 更新 `CLAUDE.md` 和 `README.md`

## 如何新增一个 Workflow

1. 在 `.claude/workflows/` 创建 `<name>.js`
2. 文件必须以 `export const meta = { ... }` 开头
3. **在文件顶部定义共享 prompt 块**：
```javascript
// Shared prompt blocks — canonical source: .claude/shared/rules.md
const RULES = `CRITICAL RULES:
1. KMP: shared/src/commonMain/ MUST NOT import java.time or android.*
...`
```
4. 在所有 agent prompt 中引用 `${RULES}` 变量而非内联复制
5. 使用 `agent()` / `pipeline()` / `parallel()` / `phase()` API
6. 测试：在对话中调用 `workflow:<name>` 验证行为

## Gotchas — .claude/ 系统自身的陷阱

### 单一事实源规则
**铁律和已知忽略的唯一来源是 `.claude/shared/rules.md`。** 不要在其他文件中复制完整的铁律文本——引用即可。特别检查：
- Agent 定义 `**Rules**:` 行是否指向正确路径
- Workflow 脚本 `// canonical source:` 注释是否指向正确路径

### Agent frontmatter 字段
- `model`: 可选 `sonnet` | `opus` | `haiku` | `fable`
- `tools`: 所有 reviewer agent 至少需要 `[Read, Grep, Glob, Bash]`（Bash 用于编译/grep 验证）
- `bug-fixer` 额外需要 `Edit`

### Workflow 脚本限制
- 不能用 `Date.now()` / `Math.random()` / `new Date()`（会破坏回放）
- 不能用 Node.js API / 文件系统 API
- schema 必须是纯 JSON Schema literal（不能引用变量）
- **pipeline() 优先于 parallel()** — pipeline 无 barrier，适合独立任务流水线

### 文档路径引用
- 所有路径都相对于项目根
- 用 `.claude/shared/rules.md` 引用共享规则
- 用 `.claude/docs/` 引用深度文档

### Skill vs Workflow 决策
- 简单任务（单层、单文件）→ Skill 手动引导
- 复杂任务（多层、多文件、≥3 文件）→ Workflow 自动编排
- 批量任务（N 个同类任务）→ multi-fix / multi-feature
