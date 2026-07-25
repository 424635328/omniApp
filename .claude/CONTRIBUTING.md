# Contributing to .claude/ — Agent 系统维护指南

本文件指导如何维护和扩展 `.claude/` agent 知识库系统。

## 架构分层

```
CLAUDE.md          → 每次对话自动加载（决策树 + 铁律 + 构建命令）
    ↓ 路由
Skills (*.md)      → 按需读取（触发条件 + 文档索引 + 检查清单 + 验证 + 相关Skills）
    ↓ 指向
Docs (*.md)        → 按需读取（深度参考资料：架构/算法/数据模型/测试/UI）
    ↓ 内容供给
Agents (*.md)      → Workflow 调用（审查维度 + 检查清单 + 验证命令 + 忽略列表）
    ↓ 编排
Workflows (*.js)   → 显式调用（多Agent pipeline/parallel 编排）

优化：Agent 启动读 quick-ref.md (~40行) 替代 agent-protocol.md + gotchas.md (~200行)
```

## 数据流

```
用户消息 → CLAUDE.md(决策树匹配) → Skill(触发条件+必读doc) → Doc(深度知识)
                                                              ↓
                                                        Agent(审查规则)
                                                              ↓
                                                        Workflow(多Agent编排)
```

## 如何新增一个 Skill

1. 在 `.claude/skills/` 创建 `energyflow-<name>.md`
2. 文件格式：
```markdown
---
name: energyflow-<name>
description: 一句话描述 skill 用途
---

# EnergyFlow — <中文标题>

**用途**: <触发这个skill的用户意图>

## 第一步：<行动>
...
## 检查清单
...
## 验证
...
## 禁止事项
...
## 相关 Skills
- \`energyflow-xxx\` — 关联的其他 skill
```

3. 在 `CLAUDE.md` 的决策树中添加一条路由
4. 在 `.claude/README.md` 中更新目录结构
5. 如果新 skill 引用了现有 docs → 无需新建 doc；如果需要新 doc → 在 docs/ 下创建
6. 验证：确保触发条件不与其他 skill 重叠

## 如何新增一个 Doc

1. 在对应的 `.claude/docs/<category>/` 下创建
2. 格式：清晰的标题 + 表格化速查信息 + 文件路径索引
3. 在相关的 skill 文件中添加指向
4. 在 `agent-protocol.md` 或对应的 agent 中添加引用（如果需要）

## 如何新增一个 Agent

1. 在 `.claude/agents/` 创建 `<name>.md`
2. 文件格式：
```yaml
---
name: <name>
description: <用途>
model: sonnet
tools: [Read, Grep, Glob]
---
# <Title>
## Startup Protocol
1. Read .claude/docs/agents/agent-protocol.md
2. Read .claude/docs/architecture/gotchas.md
...
```

3. 必须遵守 agent-protocol.md 中的通用协议
4. 更新 `CLAUDE.md` 和 `README.md`

## 如何新增一个 Workflow

1. 在 `.claude/workflows/` 创建 `<name>.js`
2. 文件必须以 `export const meta = { ... }` 开头
3. 使用 `agent()` / `pipeline()` / `parallel()` / `phase()` API
4. 测试：在对话中调用 `workflow:<name>` 验证行为

## Gotchas — .claude/ 系统自身的陷阱

### Skill 文件不自动生效
Skill 文件创建后，需要在 CLAUDE.md 的决策树中添加路由条目。没有路由 = agent 不会读。

### Agent frontmatter 字段
- `model`: 可选 `sonnet` | `opus` | `haiku` | `fable`
- `tools`: agent 可用的工具集合，保守列出（不给不需要的工具）
- 缺少 tools 字段 = agent 无工具可用

### Workflow 脚本限制
- 不能用 `Date.now()` / `Math.random()` / `new Date()`（会破坏回放）
- 不能用 Node.js API / 文件系统 API
- schema 必须是纯 JSON Schema literal（不能引用变量）
- pipeline() vs parallel() 的选择规则见 Workflow 工具文档

### 并行优化原则 🆕
- **pipeline() 优先于 parallel()** — pipeline 无 barrier，Item A 修 bug 时 Item B 还在诊断
- **独立任务并行** — 改不同文件的 bug/功能 → 并行 Agent 同时执行
- **分层诊断并行** — 单 bug 的 data/shared/ui/di 四层同时排查
- **Agent 启动用 quick-ref** — 不再分别读 agent-protocol.md + gotchas.md
- **barrier 只用于真需要全体结果的场景** — 如去重、全量统计

### 文档路径引用
- 所有路径都相对于项目根
- 用 `.claude/docs/...` 而非 `docs/...`（明确）
- 不要跨项目引用（如 `../../other-project/`）

### 铁律一致性
CLAUDE.md 的铁律、skills 的禁止事项、agents 的检查清单 —— 三处信息必须一致。
如果改了铁律 → 更新所有相关 skill 和 agent。

## 审查提交前检查

每次修改 .claude/ 系统后：
- [ ] `git diff --stat` — 确认只改了意图中的文件
- [ ] 更新了 CLAUDE.md 的决策树（如果新增了 skill）
- [ ] 更新了 README.md（如果新增了结构）
- [ ] Agent/skill/doc 之间的引用链接正确
- [ ] 新增的 skill/agent/workflow 有对应的 doc 支撑
- [ ] 没有重复的 name（skill name / agent name / workflow name 各自唯一）
