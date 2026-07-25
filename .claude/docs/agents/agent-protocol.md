# Agent Protocol — 所有 Agent 的通用规则

每个 EnergyFlow agent（code-reviewer, architecture-reviewer, analytics-reviewer, ui-reviewer）以及 workflow 中调用的 agent 都必须遵守此协议。

## 1. 启动协议

**每次被调用时**：
1. 读 `.claude/docs/architecture/gotchas.md` — 否则你会错过已知陷阱
2. 确认你的审查范围：只审查 diff 中的变更，还是全量扫描？
3. 先 `git diff --stat` 看改了什么，再决定读哪些文件

## 2. 输出协议

统一使用以下格式输出 finding：

```
FILE:LINE — SEVERITY — CATEGORY — Summary
```

| 字段 | 可选值 |
|------|--------|
| SEVERITY | `critical`（崩溃/数据丢失） / `warning`（错误但非致命） / `info`（风格/建议） |
| CATEGORY | `correctness` / `kmp-boundary` / `style` / `performance` / `accessibility` / `architecture` / `math` / `security` / `null-safety` / `design` |

**示例**：
```
app/.../CostEngine.kt:42 — critical — math — tier3 surcharge applied to tier2 usage
shared/.../PredictiveAnalyzer.kt:88 — warning — kmp-boundary — java.time.Instant used instead of kotlinx.datetime
app/.../ChartScreen.kt:156 — info — performance — missing remember{} on expensive computation
```

## 3. 审查协议

### 3.1 只审变更，不审遗留
- 用 `git diff main...HEAD --name-only` 确定变更范围
- 不对未变更的文件报告 finding（除非新代码引入了对旧代码的错误调用）
- 已知的遗留问题不要再报告（如 ChartScreen 的 collectAsState()）

### 3.2 优先级
1. **Correctness** — 逻辑错误、崩溃、数据丢失（最高优先）
2. **KMP Boundary** — shared 模块误用 java.time/android.*
3. **Null Safety** — MeterRecord nullable 字段的非空断言
4. **Style** — 硬编码颜色、字体、collectAsState 问题
5. **Performance** — 不必要的重组、缺少 remember
6. **Design** — 架构模式不一致

### 3.3 验证 finding
- 每报告一个 critical/warning，必须能说出具体会触发什么问题
- 如果无法构造 failure scenario，降级为 info
- 不确定的 finding 标注 `(uncertain)` 后缀

## 4. 工具协议

### 4.1 允许的工具
- Code-reviewer: Read, Grep, Glob, Edit, Bash
- Architecture-reviewer: Read, Grep, Glob
- Analytics-reviewer: Read, Grep, Glob, Bash
- UI-reviewer: Read, Grep, Glob

### 4.2 效率规则
- 先 grep 定位，再 Read 确认（不要盲目读大文件）
- 同一个文件的多个 finding 合并为一次 Read
- 用 Bash 跑编译/测试，不要猜测

## 5. 跨 Agent 协作

当你的 finding 涉及其他 agent 的领域时：
- 标注 `[cross: architecture]` / `[cross: analytics]` / `[cross: ui]`
- 不要跳过——报告后让 synthesis 阶段去重

## 6. 已知忽略列表

以下问题已记录在案，除非变得更严重，否则不报告：
- `ChartScreen.collectAsState()` — 已知的遗留不一致（gotchas.md 已记录）
- `NeonYellow = #00A3FF` — 遗留命名（gotchas.md 已记录）
- `fallbackToDestructiveMigration()` — 开发阶段有意为之（gotchas.md 已记录）
- `SmartInputParser` 年份假设 — 已知限制（gotchas.md 已记录）

如果你发现新的反直觉行为，建议添加到 gotchas.md。
