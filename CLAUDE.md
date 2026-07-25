# CLAUDE.md — EnergyFlow

> 家庭能耗记录 Android App · Kotlin + Compose + Hilt + Room + KMP
> 权威规则: `.claude/shared/rules.md` | Agent协议: `.claude/docs/agents/protocol.md`

---

## ⚡ 启动决策树

**禁止在加载对应 skill 之前写代码。**

```
用户请求
├── "理解代码"/"这是什么"           → use energyflow-acknowledge skill
├── "加功能"/"实现"/"添加"          → use energyflow-new-feature skill
│   └── 涉及 ≥3 文件 + 跨模块       → 自动升级: workflow:feature-development
├── "修bug"/"报错"/"不工作"         → use energyflow-diagnose skill
│   └── 涉及 ≥3 层                  → 自动升级: workflow:bug-fix
├── "批量修N个bug"                  → workflow:multi-fix
├── "同时加N个功能"                  → workflow:multi-feature
├── "混合任务"(修bug+加功能)         → use energyflow-parallel skill
├── "重构"/"优化结构"               → use energyflow-refactor skill
├── "跑测试"/"写测试"               → use energyflow-test skill
├── "提交"/"commit"                 → use energyflow-commit skill
├── "预检"/"扫描"(提交前)           → use energyflow-quick-scan skill
├── "新人"/"上手"                   → use energyflow-onboard skill
├── "改数据库"/"迁移"               → use energyflow-data-migration skill
├── "编译失败"/"Gradle报错"         → use energyflow-build-debug skill
├── "安全"/"API Key"               → use energyflow-security skill
├── 大规模任务(审查/全流程)          → 优先用 workflow:xxx
│   ├── 提交前全面审查              → workflow:full-review
│   ├── 端到端功能开发              → workflow:feature-development
│   ├── 系统化Bug修复               → workflow:bug-fix
│   └── 测试通过后提交              → workflow:test-then-commit
└── 其他/不确定                     → use energyflow-acknowledge skill
```

### 快速通道（仅限以下情况可跳过 skill 加载）
- 单行修改（改个字符串、加个 import、修个 typo）
- 用户明确说"直接改，不用读文档"
- 该对话中已经用过同一个 skill

---

## 🔒 铁律（详细规则: `.claude/shared/rules.md`）

1. **KMP**: `shared/src/commonMain/` 只用 `kotlinx.datetime`，禁止 `java.time` / `java.util.*` / `android.*`
2. **Hilt**: `@Singleton class X @Inject constructor(deps)` | VM = `@HiltViewModel`
3. **Compose**: `MonoFontFamily` | 主题色(禁止 hex) | `collectAsStateWithLifecycle()`
4. **Data**: `MeterRecord` nullable → `?: 0.0` | 读数累计值(大减小) | `null`≠`0.0`
5. **Room**: `fallbackToDestructiveMigration()` — schema 变更=数据全清

---

## 🔧 构建 & 测试

```bash
./gradlew :app:compileDebugKotlin              # 编译 app
./gradlew :shared:compileDebugKotlinAndroid    # 编译 shared KMP
./gradlew :app:testDebugUnitTest               # 全量单元测试
./gradlew :app:testDebugUnitTest --tests "..." # 单个测试类
./gradlew :app:assembleDebug --rerun-tasks     # Hilt缓存投毒修复
```
