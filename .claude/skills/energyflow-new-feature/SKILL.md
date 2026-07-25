---
name: energyflow-new-feature
description: 功能实现引导——从需求分析到代码验证的完整流程
---

# EnergyFlow — 新功能实现引导

**用途**: 加功能 / 改代码 / 实现需求时使用。

> **什么时候用这个 Skill vs Workflow?**
> - 简单功能（单文件、已知方案）→ 继续用本 skill 手动实现
> - 复杂功能（≥3 文件 + 跨模块）→ 用 `workflow:feature-development`（3-Agent 面板设计 + 并行实现）
> - 批量加 N 个功能 → 用 `workflow:multi-feature`（N 个 Agent 并行分析→实现）

## 第一步：领域分析

### 必读文档（按功能领域选择）
| 功能涉及 | 必须读的 doc |
|---------|-------------|
| 计费/价格 | `.claude/docs/data-layer/cost-engine.md` |
| 数据解析 | `.claude/docs/data-layer/smart-input-parser.md` |
| 异常检测 | `.claude/docs/data-layer/anomaly-detector.md` |
| 预测分析 | `.claude/docs/analytics/predictive-analyzer.md` |
| 碳足迹/洞察 | `.claude/docs/analytics/carbon-and-insight.md` |
| KMP 共享逻辑 | `.claude/docs/shared-kmp/module-design.md` |
| UI/图表 | `.claude/docs/ui-layer/theme-and-navigation.md` + `chart-screen.md` |
| 设置/报告 | `.claude/docs/ui-layer/settings-and-reports.md` |
| 外部 API | `.claude/docs/data-layer/external-services.md` |
| 数据模型 | `.claude/docs/data-layer/meter-record.md` |
| **所有功能（必读）** | `.claude/docs/architecture/gotchas.md` |

### 理解现有设计
- 读相关 ADR (`adr-001`/`002`/`003`)——了解为什么这样设计
- 读对应测试文件——了解现有行为契约

## 第二步：方案设计

在写代码前，回答以下问题：
1. **改动落在哪一层？** shared (纯逻辑) / app:data (数据层) / app:ui (界面)
2. **需要新的 DI 依赖吗？** @Singleton / @HiltViewModel / @Inject
3. **需要新的 Room 实体/DAO 吗？** destructive migration 会清数据
4. **涉及 KMP 边界吗？** shared 禁止 java.time / android.*
5. **需要新的 DataStore key 吗？** 注意版本迁移
6. **对现有测试有什么影响？** 哪些测试需要更新？

## 第三步：检查清单

写代码过程中逐个确认：

### KMP 边界检查
- [ ] shared 模块没有 `import java.*` 或 `import android.*`
- [ ] shared 模块只用 `kotlinx.datetime`，不用 `java.time`
- [ ] Android 包装器正确处理类型转换 (java.time ↔ kotlinx.datetime)

### Hilt 检查
- [ ] Engine 类: `@Singleton class X @Inject constructor(deps)`
- [ ] ViewModel: `@HiltViewModel class X @Inject constructor(deps)`
- [ ] 新 Module 正确 `@InstallIn(SingletonComponent::class)`

### Compose 检查
- [ ] 字体: 使用 `MonoFontFamily`（不是默认字体）
- [ ] 颜色: 只用主题色 (`ElectricColor` 等)，禁止硬编码 hex
- [ ] 状态: Flow 用 `collectAsStateWithLifecycle()`
- [ ] 性能: 大列表项用 `remember{}`，列表用 `animateItem()`

### 数据层检查
- [ ] MeterRecord 字段 nullable 使用前检查 null
- [ ] 读数是累计值，非增量——减法方向要对
- [ ] SmartInputParser 年份假设（当年）
- [ ] 异常检测门控在保存前触发

### 简洁性检查
- [ ] 没有为单次使用创建抽象
- [ ] 没有"万一以后需要"的灵活性
- [ ] 没有处理不可能发生的错误
- [ ] 代码量合理（200行能搞定就不要写成500行）
- [ ] 没有"顺手优化"相邻代码

## 第四步：验证

```bash
# 1. 编译检查
./gradlew :app:compileDebugKotlin

# 2. KMP 编译检查（如果改了 shared）
./gradlew :shared:compileDebugKotlinAndroid

# 3. 全量单元测试
./gradlew :app:testDebugUnitTest

# 4. 相关测试（更快）
./gradlew :app:testDebugUnitTest --tests "com.example.energyflow.data.YourTest"
```

### 验证标准
- ✅ 编译通过（无警告）
- ✅ 全部已有测试通过（未破坏现有行为）
- ✅ 新增逻辑有测试覆盖（至少边界条件）
- ✅ 代码风格与现有代码一致

## 第五步：提交

按照 Conventional Commits 规范提交：
```
<type>(<scope>): <description>
```
- type: feat / fix / refactor / test / docs / chore
- scope: data / ui / shared / di / test
- 示例: `feat(data): add water prediction to ChartViewModel`

## 禁止事项
- ❌ 不要在 shared 模块用 java.time
- ❌ 不要硬编码 hex 颜色——用主题色
- ❌ 不要跳过 gotchas.md
- ❌ 不要"顺手优化"相邻代码
- ❌ 不要为单次使用创建抽象层
- ❌ 不要盲目用 NavHost——保持 AnimatedContent 模式

## 相关 Skills
- 写测试: `energyflow-test` — TDD 循环和测试模板
- 提交: `energyflow-commit` — Conventional Commits 规范
- 预检: `energyflow-quick-scan` — 提交前的最后一轮检查
- 排查问题: `energyflow-diagnose` — 如果实现中遇到 bug
