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
- 用 Bash 跑编译/测试验证，不要猜测

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
grep -rn "collectAsState()" <ui files> | grep -v ChartScreen # 状态收集
```
