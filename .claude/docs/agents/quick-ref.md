# Quick Reference — Agent 速查卡

> 替代 agent-protocol.md + gotchas.md 的压缩版。Agent 启动时只读这一份（~40行），不再分别读两份大文档。

## 铁律（违反必错）

1. **KMP**: `shared/src/commonMain/` 禁止 `java.time` / `android.*` / `java.util.*` — 只用 `kotlinx.datetime`
2. **Hilt**: Engine = `@Singleton class X @Inject constructor(deps)` | VM = `@HiltViewModel`
3. **Compose**: `MonoFontFamily` 全局字体 | 颜色只用主题色(禁止 `Color(0xXX..)`) | Flow 用 `collectAsStateWithLifecycle()`
4. **Data**: `MeterRecord` 字段全 nullable（`!!` 禁止，用 `?: 0.0`）| 读数是累计值(大减小=消耗) | `null`≠`0.0`
5. **Room**: `fallbackToDestructiveMigration()` — schema变更=数据全清（开发阶段有意）

## 已知忽略（不要再报告）

- `ChartScreen.collectAsState()` — 遗留不一致
- `NeonYellow = #00A3FF` — 遗留命名（实际是蓝色）
- `fallbackToDestructiveMigration()` — 有意为之
- `SmartInputParser` 年份假设 — 已知限制

## 输出格式

```
FILE:LINE — SEVERITY — CATEGORY — Summary
```
SEVERITY: `critical` | `warning` | `info`
CATEGORY: `correctness` | `kmp-boundary` | `null-safety` | `style` | `math` | `architecture` | `performance` | `accessibility`

## 审查原则

- 只审 diff 变更（`git diff main...HEAD --name-only`），不审遗留代码
- 优先: Correctness > KMP Boundary > Null Safety > Style > Performance
- 每个 critical/warning 必须能说出具体触发场景
- 跨领域 finding 标注 `[cross: xxx]`

## 验证命令

```bash
./gradlew :app:compileDebugKotlin                          # 编译
./gradlew :shared:compileDebugKotlinAndroid                # KMP编译
./gradlew :app:testDebugUnitTest                           # 全量测试
grep -rn "java\.time\|android\." shared/src/commonMain/    # KMP边界检查
grep -rn "Color(0x" <new ui files> | grep -v "Color.kt"   # 硬编码颜色检查
grep -rn "collectAsState()" <new ui files>                 # 状态收集检查
```
