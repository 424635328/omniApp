# CLAUDE.md — EnergyFlow

> 家庭能耗记录 Android App · Kotlin + Compose + Hilt + Room + KMP
> `.claude/skills/` = 行动手册 | `.claude/docs/` = 深度参考 | `.claude/workflows/` = 多Agent编排

---

## ⚡ 启动决策树 — 每条对话第一步

**禁止在读取对应 skill 之前写代码。** 按此决策树找到正确的 skill：

```
用户请求
├── "帮我理解"/"这是什么"/"看看代码" → energyflow-acknowledge
├── "加功能"/"实现"/"添加"/"新增"     → energyflow-new-feature
├── "修bug"/"报错"/"不工作"/"崩溃"     → energyflow-diagnose
├── "重构"/"优化结构"/"拆分"           → energyflow-refactor
├── "跑测试"/"测试失败"/"写测试"       → energyflow-test
├── "提交"/"commit"/"PR"              → energyflow-commit
├── "检查"/"扫描"/"审查" (提交前)      → energyflow-quick-scan
├── "新人"/"第一次"/"上手"             → energyflow-onboard
├── "改数据库"/"迁移"/"DataStore"      → energyflow-data-migration
├── "编译失败"/"Gradle报错"/"Hilt"    → energyflow-build-debug
├── "安全"/"API Key"/"隐私"           → energyflow-security
├── 大规模任务(审查/全流程/修复)       → 考虑 workflow:xxx (见下方)
└── 其他/不确定                        → energyflow-acknowledge (安全默认)
```

### 快速通道（仅限以下情况可跳过 skill 加载）
- 单行修改（改个字符串、加个 import、修个 typo）
- 用户明确说"直接改，不用读文档"
- 该对话中已经读过同一个 skill

### Workflows（大规模任务用多 Agent 编排）
| 场景 | 调用 |
|------|------|
| 提交前全面审查（4维度并联） | `workflow:full-review` |
| 端到端功能开发（理解→方案→实现→验证） | `workflow:feature-development` |
| 系统化 Bug 修复（复现→诊断→修复→回归） | `workflow:bug-fix` |
| 测试通过后自动提交 | `workflow:test-then-commit` |
| 代码库引导漫游 | `workflow:onboarding` |

---

## 🔒 铁律 — 违反必错

1. **KMP 边界**: `shared/src/commonMain/` 只能用 `kotlinx.datetime`，禁止 `java.time` / `android.*`
   - 验证: `grep -rn "java\.time\|android\." shared/src/commonMain/ --include="*.kt"` → 必须空
2. **Hilt**: Engine 用 `@Singleton class X @Inject constructor(deps)`，VM 用 `@HiltViewModel`
3. **Compose**: `MonoFontFamily` 全局字体；颜色只用主题色 (`ElectricColor` 等)，禁止硬编码 hex `Color(0x...)`；Flow 用 `collectAsStateWithLifecycle()`
4. **精准修改**: 只改任务相关代码；不"顺手优化"相邻代码；匹配现有风格
5. **数据模型**: `MeterRecord` 字段全 nullable；读数是累计值（大减小=消耗）；`null`≠`0.0`
6. **Room**: `fallbackToDestructiveMigration()` — schema 变更=数据全清（开发阶段有意为之）

---

## 🔧 构建 & 测试

```bash
./gradlew :app:compileDebugKotlin              # 编译 app
./gradlew :shared:compileDebugKotlinAndroid    # 编译 shared KMP
./gradlew :app:testDebugUnitTest               # 全量单元测试
./gradlew :app:testDebugUnitTest --tests "..." # 单个测试类
./gradlew :app:assembleDebug --rerun-tasks     # Hilt缓存投毒修复
```

---

## 🔄 任务闭环 — 完成后必做

每完成一个代码修改任务，按此检查：

```
1. 编译: ./gradlew :app:compileDebugKotlin                         → PASS/FAIL
2. KMP:  grep "java.time\|android\." shared/src/commonMain/ → 空/有
3. 测试: ./gradlew :app:testDebugUnitTest                          → PASS/FAIL
4. 颜色: grep "Color(0x" <changed ui files>                        → 空/有
5. 状态: grep "collectAsState()" <new ui code>                     → 空/有
6. 空安全: grep "!!" <new code>                                    → 空/有
```

如果步骤 2-6 有任何非预期结果 → 修复 → 回到步骤 1。
全部通过 → 报告 "✅ 闭环验证通过"。
