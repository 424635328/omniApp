# .claude/ — Agent 知识库 & 自动化系统

> **Tested Claude Code configuration date**: 2026-07-25
> **Skill format**: `.claude/skills/<name>/SKILL.md`
> **Workflow format**: `.claude/workflows/<name>.js` (custom runtime)

---

## 架构全景

```
CLAUDE.md (自动加载，每次对话)
    │  常驻项目约束 + 铁律 + 构建命令 + 决策树路由
    ↓
 Skills                          ← 按需加载或 /name 直接调用
    │  .claude/skills/<name>/SKILL.md  (13个)
    │  触发条件 + 文档索引 + 检查清单 + 验证步骤
    ↓
 Docs                             ← Skill 指令指向时按需读取
    │  .claude/docs/  (26个)
    │  架构细节 · 算法说明 · 数据模型 · 已知陷阱 · ADR
    ↓
 Agents                           ← Workflow 调用的专业子 Agent
    │  .claude/agents/  (5个)
    │  独立上下文中的专业执行者（审查/修复/分析）
    ↓
 Workflows                        ← 显式调用: workflow:<name>
    │  .claude/workflows/  (7个)
    │  多 Agent 编排: pipeline / parallel / phase
    ↓
 Hooks                            ← 确定性规则自动执行
       .claude/settings.json + .claude/hooks/
       PreToolUse (Edit|Write): KMP 边界自动拦截
```

**五层协作：**

| 层 | 触发方式 | 角色 | 关键文件 |
|----|---------|------|---------|
| **CLAUDE.md** | 每次对话自动注入 | 常驻约束 + 路由总纲 | `CLAUDE.md` |
| **Skills** | 决策树路由 · `/name` 调用 · 描述自动匹配 | 领域工作流：分步指令 | `.claude/skills/<name>/SKILL.md` |
| **Docs** | Skill 指令指向时读取 | 深度知识：架构/算法/陷阱 | `.claude/docs/` |
| **Agents** | Workflow 编排调度 | 专业执行：审查/修复/分析 | `.claude/agents/*.md` |
| **Workflows** | `workflow:<name>` 显式调用 | 多 Agent 并行编排 | `.claude/workflows/*.js` |

**单一事实源**: `.claude/shared/rules.md` — 铁律 · 已知忽略 · 验证命令 · Prompt 模板。所有层引用它，不自行复制。

---

## 目录结构

```
.claude/
├── settings.json                   # 项目级共享配置（hooks、权限白名单）
├── settings.local.json             # 本地覆盖（不提交）
├── README.md                       # 本文件 — 架构总览
├── CONTRIBUTING.md                 # 维护指南 — 如何新增/修改组件
│
├── shared/                         # 共享资源（单一事实源）
│   └── rules.md                    # 铁律(5) + 已知忽略(4) + 验证命令 + Prompt模板
│
├── skills/                         # 行动手册（13个）— <name>/SKILL.md
│   ├── energyflow-acknowledge/     # 代码库认知引导
│   ├── energyflow-new-feature/     # 功能实现引导
│   ├── energyflow-diagnose/        # Bug 诊断引导
│   ├── energyflow-parallel/        # 并行多任务调度
│   ├── energyflow-multi-task/      # ⚠️ Deprecated → energyflow-parallel
│   ├── energyflow-refactor/        # 安全重构引导
│   ├── energyflow-test/            # 测试运行与调试
│   ├── energyflow-commit/          # Conventional Commits 规范
│   ├── energyflow-quick-scan/      # 提交前快速扫描
│   ├── energyflow-onboard/         # 新贡献者引导
│   ├── energyflow-data-migration/  # 数据迁移引导
│   ├── energyflow-build-debug/     # 构建与调试
│   └── energyflow-security/        # 安全检查清单
│
├── agents/                         # 专业子 Agent（5个）
│   ├── code-reviewer.md            # 代码审查（Read+Grep+Glob+Bash）— 只读
│   ├── architecture-reviewer.md    # 架构审查（Read+Grep+Glob+Bash）— 只读
│   ├── analytics-reviewer.md       # 算法/数学审查（Read+Grep+Glob+Bash）— 只读
│   ├── ui-reviewer.md              # Compose UI 审查（Read+Grep+Glob+Bash）— 只读
│   └── bug-fixer.md                # Bug 修复（+Edit）— 可修改代码
│
├── hooks/                          # PreToolUse 钩子脚本
│   └── kmp-boundary-guard.py       # 拦截 commonMain 平台依赖写入
│
├── docs/                           # 深度参考（26个）
│   ├── agents/
│   │   └── protocol.md             # 统一 Agent 协议 + 并发纪律
│   ├── architecture/               # 架构概览 · 构建 · 陷阱 · ADR (8个)
│   ├── data-layer/                 # 数据模型 · 解析 · 计费 · 外部 API (6个)
│   ├── analytics/                  # 预测 · 碳足迹 · 洞察 (2个)
│   ├── shared-kmp/                 # KMP 模块设计 (1个)
│   ├── ui-layer/                   # 主题 · 导航 · 图表 · 设置 · 入口/扫描/年报 (4个)
│   └── testing/                    # 测试策略 · 用例 · 流程 (4个)
│
├── archive/                        # 历史 agent 工作成果归档（gitignored）
│
└── workflows/                      # 多 Agent 编排（7个）
    ├── full-review.js              # 4 维度并行审查（code+arch+analytics+ui）
    ├── feature-development.js      # 理解→3面板设计→分阶段实现→并行验证
    ├── bug-fix.js                  # 复现→4层并行诊断→修复→全量验证
    ├── multi-fix.js                # 批量修 Bug（并行诊断→Pipeline修复→统一验证）
    ├── multi-feature.js            # 批量加功能（并行分析→Pipeline实现→统一验证）
    ├── test-then-commit.js         # 测试通过后自动提交
    └── onboarding.js               # 代码库引导漫游
```

---

## Skill 速查

### 触发方式

Skills 支持三种触发方式，无需手动 `Read()`：

| 方式 | 示例 | 说明 |
|------|------|------|
| **决策树路由** | Agent 读取 CLAUDE.md → 匹配任务类型 → 自动加载 | 对话中自动发生 |
| **斜杠命令** | `/energyflow-diagnose` | 用户主动调用 |
| **描述匹配** | 用户说"排查一下这个崩溃" → 自动匹配 diagnose | 自然语言触发 |

### 完整列表

| Skill | 用途 | 触发场景 |
|-------|------|---------|
| `energyflow-acknowledge` | 代码库认知引导 | "理解代码" / "这是什么" / 不确定时 |
| `energyflow-new-feature` | 功能实现引导 | "加功能" / "实现" / 单文件新功能 |
| `energyflow-diagnose` | Bug 诊断引导 | "修bug" / "报错" / "不工作" |
| `energyflow-parallel` | 并行多任务调度 | 混合任务（修bug+加功能+重构同时做） |
| `energyflow-refactor` | 安全重构引导 | "重构" / "优化结构" |
| `energyflow-test` | 测试运行与调试 | "跑测试" / "写测试" / 测试失败排查 |
| `energyflow-commit` | 提交规范 | "提交" / "commit" / 写 PR |
| `energyflow-quick-scan` | 提交前快速扫描 | "预检" / "扫描" / 提交前检查 |
| `energyflow-onboard` | 新贡献者引导 | "新人" / "第一次" / "上手" |
| `energyflow-data-migration` | 数据迁移引导 | "改数据库" / "迁移" / Schema 变更 |
| `energyflow-build-debug` | 构建与调试 | "编译失败" / "Gradle报错" / Hilt 问题 |
| `energyflow-security` | 安全检查 | "安全" / "API Key" / 隐私 |
| ~~`energyflow-multi-task`~~ | ⚠️ 已废弃 → 用 `energyflow-parallel` | 禁用自动触发 |

---

## Workflow 速查

| Workflow | 用途 | 适用场景 | Agent 数 |
|----------|------|---------|:------:|
| `full-review` | 4 维度并行审查 | 提交前全面审查 | 5 |
| `feature-development` | 理解→设计→实现→验证 | ≥3 文件 + 跨模块新功能 | 8+ |
| `bug-fix` | 复现→诊断→修复→验证 | ≥3 层复杂 Bug | 7 |
| `multi-fix` | 批量并行修 Bug | N 个独立 Bug | N×3+ |
| `multi-feature` | 批量并行加功能 | N 个独立功能 | N×3+ |
| `test-then-commit` | 测试→自动提交 | 常规提交流程 | 2 |
| `onboarding` | 代码库引导漫游 | 新人上手 | 4 |

### Skill vs Workflow 决策

| 场景 | 用 Skill | 升级到 Workflow |
|------|---------|----------------|
| Bug 修复 | `/energyflow-diagnose`（单层） | `workflow:bug-fix`（≥3层） |
| 新功能 | `/energyflow-new-feature`（单文件） | `workflow:feature-development`（≥3文件+跨模块） |
| 批量任务 | — | `workflow:multi-fix` / `workflow:multi-feature` |
| 代码审查 | `/energyflow-quick-scan`（快速） | `workflow:full-review`（深度） |

---

## Agent 能力矩阵

| Agent | 角色 | Tools | 可修改代码 |
|-------|------|-------|:--------:|
| `code-reviewer` | 代码正确性 · 风格 · KMP 边界 | Read, Grep, Glob, Bash | ❌ |
| `architecture-reviewer` | 模块边界 · DI · 数据流 | Read, Grep, Glob, Bash | ❌ |
| `analytics-reviewer` | 算法/数学正确性 | Read, Grep, Glob, Bash | ❌ |
| `ui-reviewer` | Compose 性能 · 无障碍 · 设计一致性 | Read, Grep, Glob, Bash | ❌ |
| `bug-fixer` | 独立诊断并修复 Bug | Read, Grep, Glob, Edit, Bash | ✅ |

> 所有 Agent 引用 `.claude/shared/rules.md`（铁律）和 `.claude/docs/agents/protocol.md`（协议）。

---

## Hooks 系统

在 `.claude/settings.json` 中配置，脚本位于 `.claude/hooks/`：

| Hook | 触发时机 | 行为 |
|------|---------|------|
| **PreToolUse (Edit\|Write)** | 每次写入前 | 若目标在 `shared/src/commonMain/` 且新内容含 `import java.* / javax.* / android.* / androidx.*` → **拦截该次写入**（exit 2），脚本 `kmp-boundary-guard.py` |

> **设计原则**: PreToolUse 只做单文件快速静态检查（<1秒），hook 自身异常时放行（fail-open）；全量构建和测试留给 Workflow 验证阶段或 CI。

---

## 维护规则

### 修改规则时
**只需改 `.claude/shared/rules.md`**（铁律、已知忽略、验证命令、Prompt 模板的唯一来源）。
Workflow 中的 `RULES` 块为静态副本 — 修改 `rules.md` 后需同步更新到 5 个代码修改型 workflow 的 `RULES` 常量。

### 添加/修改组件时
1. 改规则 → `shared/rules.md`
2. 加 Skill → `.claude/skills/<name>/SKILL.md` + 更新 `CLAUDE.md` 决策树 + 更新本文件
3. 加 Agent → `.claude/agents/<name>.md` + 更新本文件能力矩阵
4. 加 Workflow → `.claude/workflows/<name>.js` + 更新本文件速查表
5. 加 Doc → `.claude/docs/<category>/<name>.md` + 在对应 Skill 中添加引用
6. 改架构 → 加 ADR 到 `.claude/docs/architecture/`
7. 发现新坑 → 加到 `.claude/docs/architecture/gotchas.md`

### 已知设计约束
- **Skill 必须为目录结构**: `.claude/skills/<name>/SKILL.md`（平铺 `.md` 不会被注册）
- **Workflow RULES 为静态副本**: JS 运行时不支持动态导入 `rules.md`，修改规则后需手动同步
- **Agent model 合法值**: `sonnet` | `opus` | `haiku` | `fable` | `inherit`（小写合法）
- **Reviewer Agent 不应有 Edit**: 只读审查，修复由 `bug-fixer` 或主 Agent 执行

---

## 快速开始

```bash
# 新人上手
/energyflow-onboard

# 理解代码
/energyflow-acknowledge

# 日常开发：加功能
/energyflow-new-feature

# 日常开发：修Bug
/energyflow-diagnose

# 提交前检查
/energyflow-quick-scan
./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest

# 大规模任务
workflow:full-review              # 全面审查
workflow:feature-development     # 端到端功能开发
workflow:bug-fix                 # 系统化 Bug 修复
```
