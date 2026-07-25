---
name: energyflow-quick-scan
description: 提交前快速扫描——检查常见错误、风格问题、KMP边界、遗漏的测试
---

# EnergyFlow — 提交前快速扫描

**用途**: 提交代码前的最后一轮检查——5分钟内找出明显问题。

## 扫描清单（按优先级）

### 1. KMP 边界（最高优先级）
```bash
# 检查 shared 模块是否误用 java.time 或 android.*
grep -rn "java\.time\|android\.\|java\.util\." shared/src/commonMain/ --include="*.kt"
```
- ✅ 应该 0 结果
- ❌ 如果有结果 → 替换为 kotlinx.datetime / kotlinx.*

### 2. 硬编码颜色
```bash
# 检查是否硬编码了 hex 颜色（应该用主题色）
grep -rn "Color(0x[0-9A-Fa-f]\{6,8\})" app/src/main/java/com/example/energyflow/ui/ --include="*.kt"
```
- ✅ 可能 0 结果（或用函数参数传入的颜色）
- ❌ 如果有新的硬编码 → 替换为主题色 (`ElectricColor`, `DarkBackground` 等)
- ⚠️ 遗留代码中的 `DarkCard = Color(0xFF212538)` 等定义在 `Color.kt` 中是允许的

### 3. 状态收集
```bash
# 检查是否用了 collectAsState() 而非 collectAsStateWithLifecycle()
grep -rn "\.collectAsState()" app/src/main/java/com/example/energyflow/ui/ --include="*.kt"
```
- ChartScreen 已知使用 `collectAsState()`（遗留不一致）
- 新代码必须用 `collectAsStateWithLifecycle()`

### 4. 字体
```bash
# 检查新代码是否使用了默认字体
grep -rn "fontFamily\s*=" app/src/main/java/com/example/energyflow/ui/ --include="*.kt" | grep -v "MonoFontFamily\|fontFamily\|FontFamily"
```
- ✅ 所有 `fontFamily` 都应该是 `MonoFontFamily` 或不需要显式设置

### 5. Null 安全
```bash
# 检查 MeterRecord 字段的非空断言（可能崩溃）
grep -rn "electricTotal!!\|electricPeak!!\|electricValley!!\|waterTotal!!\|gasTotal!!" app/src/ --include="*.kt"
```
- ✅ 应该 0 使用 `!!`
- ❌ 如果有 → 替换为 `?: 0.0` 或 `?: null` 安全处理

### 6. 测试覆盖
快速检查新增/修改的 public 函数是否有对应测试：
```bash
# 查看改了哪些文件
git diff --name-only
# 对于 data/ 下的文件，检查 app/src/test/ 是否有对应测试
```

## 快速修复常见问题

| 问题 | 快速修复 |
|------|---------|
| 用了 `#XXXXXX` 颜色 | 查 `.claude/docs/ui-layer/theme-and-navigation.md` 颜色表找对应主题色 |
| 用了 `java.time` in shared | 改为 `kotlinx.datetime`，必要时在 Android wrapper 转换 |
| 用了 `collectAsState()` | 改为 `collectAsStateWithLifecycle()` |
| 用了 `!!` 空断言 | 改为 `?: 0.0` / `?: return` / `?.let{}` |
| 没写测试 | 在对应测试文件添加至少边界条件测试 |
| 改了 public API | 确认所有调用方已更新，运行全量测试 |

## 输出格式

扫描完成后，用以下格式输出结果：
```
## Quick Scan Results

### 🔴 Critical (必须修复)
- file.kt:42 — 在 shared 模块用了 java.time

### 🟡 Warning (建议修复)
- file.kt:88 — 新代码未写测试

### 🟢 Info (已确认合规)
- KMP 边界: ✅
- 颜色规范: ✅
- 字体: ✅
```

## 相关 Skills
- 跑测试: `energyflow-test` — 扫描前先跑测试
- 提交: `energyflow-commit` — 扫描通过后规范化提交
- 安全: `energyflow-security` — 安全专项检查
