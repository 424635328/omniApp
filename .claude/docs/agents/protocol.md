# Agent Protocol — 所有 Agent 通用规则

> 权威规则见 `.claude/shared/rules.md`。本文件定义审查流程和输出规范。

## 启动协议

1. 确认审查范围：`git diff main...HEAD --name-only`（只审变更，不审遗留）
2. 铁律和已知忽略见 `.claude/shared/rules.md`

## 输出格式

```
FILE:LINE — SEVERITY — CATEGORY — Summary
```

| 字段 | 可选值 |
|------|--------|
| SEVERITY | `critical`（崩溃/数据丢失） / `warning`（错误但非致命） / `info`（风格/建议） |
| CATEGORY | `correctness` / `kmp-boundary` / `null-safety` / `style` / `performance` / `accessibility` / `architecture` / `math` / `security` / `design` / `di` / `data-flow` |

## 审查原则

- 优先: Correctness > KMP Boundary > Null Safety > Style > Performance
- 每个 critical/warning 必须能说出具体触发场景
- 不确定的 finding 标注 `(uncertain)`
- 跨领域 finding 标注 `[cross: xxx]`

## 工具协议

- 先 grep 定位，再 Read 确认（不要盲目读大文件）
- 同一个文件的多个 finding 合并为一次 Read
- 编译/测试验证只在**串行阶段**由单个 agent 执行，不要猜测结果

## 并发纪律（防死锁）

- **同一工作树同一时刻最多一个 Gradle 进程**。并发的 Gradle 调用会在项目锁上互相等待（表现为卡死），且每个调用可能拉起一个 ~2GB 的独立 daemon（表现为整机卡顿）。
- 与其他 agent 并发运行时（审查、诊断、扫描阶段）：**只读操作**（grep/read/git diff），禁止运行 Gradle、禁止编辑文件。
- 并行编辑阶段：只编辑，不编译；编译由阶段结束后的单点 agent 串行执行。
- worktree 隔离仅用于必须并行编辑同一文件的场景，且**禁止在 worktree 里跑 Gradle 构建**（会产生独立 daemon + 数百 MB build 目录）；任务结束必须清理 worktree 与分支。

## 跨 Agent 协作

- 涉及其他 agent 领域时标注 `[cross: architecture]` / `[cross: analytics]` / `[cross: ui]`
- 不要跳过——报告后让 synthesis 阶段去重

## 验证命令

```bash
./gradlew :app:compileDebugKotlin                          # 编译
./gradlew :shared:compileDebugKotlinAndroid                # KMP编译
./gradlew :app:testDebugUnitTest                           # 全量测试
grep -rn "java\.time\|android\." shared/src/commonMain/    # KMP边界
grep -rn "Color(0x" <ui files> | grep -v "Color.kt"        # 硬编码颜色
grep -rn "collectAsState()" <ui files> # 状态收集
```
