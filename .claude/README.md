# .claude/ — Agent 知识库 & 自动化系统

## 设计哲学

```
用户消息
    ↓
CLAUDE.md (自动加载) → "你的任务是 X → 读 .claude/skills/Y.md"
    ↓
Skill 文件 → "读这些 doc，按这个清单做，这样验证"
    ↓
Docs (按需加载) → 深度参考：架构、算法、陷阱
    ↓
代码改动
    ↓
(可选) Workflow → 多 Agent 并行审查 / 全流程自动化
```

**三层加载，按需取用：**

| 层 | 何时加载 | 内容 |
|----|---------|------|
| **CLAUDE.md** | 每条对话自动加载 | 启动流程、铁律、构建命令、Skills 路由表 |
| **Skills** | CLAUDE.md 路由后读取 | 可执行指令：触发条件 + 必读文档 + 检查清单 + 验证 |
| **Docs** | Skill 指令指向时读取 | 深度参考资料：架构细节、算法说明、已知陷阱 |

## 目录结构

```
.claude/
├── settings.json                   # 项目级共享配置（hooks、权限）
├── settings.local.json             # 本地覆盖（不提交）
├── README.md                       # 本文件
│
├── skills/                         # 行动手册（11个）— Agent 启动后按需读取
│   ├── energyflow-acknowledge.md   # 代码库认知引导
│   ├── energyflow-new-feature.md   # 功能实现引导
│   ├── energyflow-diagnose.md      # Bug 诊断引导
│   ├── energyflow-refactor.md      # 安全重构引导
│   ├── energyflow-test.md          # 测试运行与调试
│   ├── energyflow-commit.md        # Conventional Commits 规范
│   ├── energyflow-quick-scan.md    # 提交前快速扫描
│   ├── energyflow-onboard.md       # 新贡献者引导
│   ├── energyflow-data-migration.md # 数据迁移引导
│   ├── energyflow-build-debug.md   # 构建与调试
│   └── energyflow-security.md      # 安全检查清单
│
├── agents/                         # 专业子 Agent（4个）— Workflow 调用
│   ├── code-reviewer.md            # 代码正确性、风格、KMP 边界
│   ├── architecture-reviewer.md    # 模块边界、DI、数据流
│   ├── analytics-reviewer.md       # 算法/数学正确性
│   └── ui-reviewer.md              # Compose 性能、无障碍、设计一致性
│
├── docs/                           # 深度参考（18个）— 按需读取
│   ├── architecture/               # 架构、构建、陷阱、ADR
│   ├── data-layer/                 # 数据模型、解析、检测、计费、外部 API
│   ├── analytics/                  # 预测、碳足迹、洞察
│   ├── shared-kmp/                 # KMP 模块设计
│   ├── ui-layer/                   # 主题、导航、图表、设置
│   └── testing/                    # 测试策略、用例、流程、开发工作流
│
└── workflows/                      # 多 Agent 编排（5个）— 显式调用
    ├── full-review.js              # 4 维度并行审查
    ├── feature-development.js      # 理解→方案→实现→测试
    ├── bug-fix.js                  # 复现→诊断→修复→验证
    ├── test-then-commit.js         # 测试→自动提交
    └── onboarding.js               # 代码库引导漫游
```

## 自动触发机制

`settings.json` 中配置了 `PreToolUse` hook：
- 每次 Write / Edit 操作前，Agent 收到系统提醒确认已读对应 skill 文件

## 如何使用

### 自动（通过 CLAUDE.md 路由）
Agent 自动读 CLAUDE.md → 识别任务类型 → 读对应 skill → 按指令执行。

### 手动调用 Skill
```
/energyflow-acknowledge     # 全面理解项目
/energyflow-new-feature     # 引导式功能实现
/energyflow-diagnose        # 系统化 Bug 诊断
/energyflow-refactor        # 安全重构
/energyflow-test            # 运行与调试测试
/energyflow-commit          # 规范化提交
/energyflow-quick-scan      # 提交前快速扫描
/energyflow-onboard         # 新人引导
/energyflow-data-migration  # 数据迁移
/energyflow-build-debug     # 构建调试
/energyflow-security        # 安全检查
```

### 多 Agent 编排
```
workflow:full-review           # 4 并联审查员
workflow:feature-development   # 功能全流程
workflow:bug-fix               # Bug 修复全流程
workflow:test-then-commit      # 测试 + 提交
workflow:onboarding            # 项目引导
```

- `workflow:full-review`        # 4 并联审查员
- `workflow:feature-development # 功能全流程
- `workflow:bug-fix`            # Bug 修复全流程
- `workflow:test-then-commit`   # 测试 + 提交
- `workflow:onboarding`         # 项目引导

## 维护规则

当添加功能或修改架构时：
1. 更新对应 `.claude/docs/` 文档
2. 如果是反直觉的坑 → 加到 `gotchas.md`
3. 如果是重大设计决策 → 加 ADR
4. 如果改了常用工作流 → 更新对应 skill 文件
5. 如果有新测试场景 → 更新 `testing/test-cases.md`
6. 如果新增了外部 API / 安全相关 → 更新 `energyflow-security.md`
